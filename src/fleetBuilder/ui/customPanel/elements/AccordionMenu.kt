package fleetBuilder.ui.customPanel.elements

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import fleetBuilder.otherMods.starficz.*
import fleetBuilder.ui.UIUtils
import org.lazywizard.lazylib.ui.FontException
import org.lazywizard.lazylib.ui.LazyFont
import org.lwjgl.opengl.GL11
import java.awt.Color

// Made using Claude because I feel as though I've done enough complicated UI myself already.

// ─────────────────────────────────────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single node in the accordion tree.
 *
 * @param label    Text displayed in the row.
 * @param children Sub-nodes. Empty = leaf (no arrow shown).
 * @param id       Stable identifier surfaced in [AccordionMenu.onSelect].
 */
data class AccordionNode(
    val label: String,
    val children: List<AccordionNode> = emptyList(),
    val id: Any? = null
) {
    val isLeaf: Boolean get() = children.isEmpty()
}

// ─────────────────────────────────────────────────────────────────────────────
//  AccordionMenu
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Scrollable accordion/tree-view panel with single-node selection.
 *
 * Both group nodes AND leaf nodes are selectable. Clicking the arrow button on
 * a group toggles its children open/closed. Clicking the label area of any row
 * selects that node and fires [onSelect].
 *
 * Rendered entirely in one [StarUIPanelPlugin] using GL11 for backgrounds/borders
 * and [LazyFont.DrawableString]s for text — no second panel, no TooltipMaker.
 *
 * Usage
 * -----
 * ```kotlin
 * val tree = AccordionMenu(
 *     parentPanel   = myPanel,
 *     x             = 10f,
 *     y             = 10f,
 *     width         = 220f,
 *     visibleHeight = 300f,
 *     nodes         = listOf(
 *         AccordionNode("Ships", id = "ships", children = listOf(
 *             AccordionNode("Frigates", id = "frigates", children = listOf(
 *                 AccordionNode("Wolf",    id = "wolf"),
 *                 AccordionNode("Lasher",  id = "lasher")
 *             )),
 *             AccordionNode("Destroyers", id = "destroyers", children = listOf(
 *                 AccordionNode("Hammerhead", id = "hammerhead")
 *             ))
 *         )),
 *         AccordionNode("Fighters", id = "fighters", children = listOf(
 *             AccordionNode("Broadsword", id = "broadsword")
 *         ))
 *     )
 * )
 * tree.onSelect { node -> println("Selected: ${node.label}  id=${node.id}") }
 * ```
 *
 * @param parentPanel     Parent panel this widget will be attached to.
 * @param x               Left edge, relative to [parentPanel].
 * @param y               Bottom edge, relative to [parentPanel].
 * @param width           Total pixel width.
 * @param visibleHeight   Clipped viewport height; content scrolls inside.
 * @param nodes           Root-level tree nodes.
 * @param rowHeight       Height of every row in pixels.
 * @param indentPerLevel  Extra left-indent per depth level, in pixels.
 */
class AccordionMenu(
    parentPanel: TooltipMakerAPI,
    x: Float,
    y: Float,
    val width: Float,
    val visibleHeight: Float,
    nodes: List<AccordionNode>,
    val rowHeight: Float = 50f,
    val indentPerLevel: Float = 14f,
    val renderUILines: Boolean = false,
) {

    // ── Theming ───────────────────────────────────────────────────────────────

    var rowBgColor: Color = Color(30, 30, 30, 180)
    var rowHoverColor: Color = Color(255, 255, 255, 25)
    var rowSelectedColor: Color = Color(80, 160, 255, 60)
    var rowSeparatorColor: Color = Color(255, 255, 255, 18)
    var labelColor: Color = Color(220, 220, 220, 255)
    var labelSelectedColor: Color = Color(130, 200, 255, 255)
    var labelGroupColor: Color = Color(255, 255, 255, 255)    // non-leaf label
    var borderColor: Color = Global.getSettings().darkPlayerColor ?: Color(80, 120, 160, 200)
    var scrollBarColor: Color = Color(100, 140, 180, 150)
    var scrollBarBgColor: Color = Color(20, 20, 20, 100)
    var fontSize: Float = 20f
    var arrowSize: Float = 24f

    // Horizontal padding between arrow and label, and between label left and indent
    var arrowPadLeft: Float = 4f
    var arrowPadRight: Float = 5f

    // ── State ─────────────────────────────────────────────────────────────────

    private var rootNodes: List<AccordionNode> = nodes
    private val expandedPaths = mutableSetOf<String>()
    private var selectedPath: String? = null

    private var scrollOffsetY: Float = 0f
    private var totalContentHeight: Float = 0f

    private var onSelectCallback: ((AccordionNode) -> Unit)? = null
    private var onRightClickSelectCallback: ((AccordionNode) -> Unit)? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun onSelect(callback: (AccordionNode) -> Unit) {
        onSelectCallback = callback
    }

    fun onRightClickSelect(callback: (AccordionNode) -> Unit) {
        onRightClickSelectCallback = callback
    }

    /** Programmatically select a node by [AccordionNode.id]; expands all ancestors. */
    fun selectById(id: Any?) {
        findAndSelect(rootNodes, "", id)
    }

    fun expandAll() {
        traverseNodes(rootNodes, "") { node, path -> if (!node.isLeaf) expandedPaths.add(path) }
        rebuild()
    }

    fun collapseAll() {
        expandedPaths.clear()
        rebuild()
    }

    /** Adds [node] as a child of the node with [parentId], or at the root if [parentId] is null. */
    fun addNode(node: AccordionNode, parentId: Any? = null) {
        rootNodes = rootNodes.addToTree(node, parentId)
        rebuildAll()
    }

    /** Removes the node with [id] (and all its descendants) from the tree. */
    fun removeNode(id: Any?) {
        selectedPath = null
        expandedPaths.removeAll { path ->
            // also clear any expand state that belonged to the removed subtree
            flatRows.any { it.node.id == id && path.startsWith(it.path) }
        }
        rootNodes = rootNodes.filterTree(id)
        rebuildAll()
    }

    // ── Flat row model ────────────────────────────────────────────────────────

    private data class Row(
        val node: AccordionNode,
        val path: String,
        val depth: Int,
        val isExpanded: Boolean
    )

    private var flatRows: List<Row> = emptyList()

    private fun rebuild() {
        flatRows = buildFlatRows(rootNodes, "", 0)
        totalContentHeight = flatRows.size * rowHeight
        scrollOffsetY = scrollOffsetY.coerceIn(0f, maxOf(0f, totalContentHeight - visibleHeight))
    }

    private fun buildFlatRows(nodes: List<AccordionNode>, parentPath: String, depth: Int): List<Row> {
        val out = mutableListOf<Row>()
        nodes.forEachIndexed { i, node ->
            val path = if (parentPath.isEmpty()) "$i" else "$parentPath/$i"
            val expanded = expandedPaths.contains(path)
            out += Row(node, path, depth, expanded)
            if (!node.isLeaf && expanded) out += buildFlatRows(node.children, path, depth + 1)
        }
        return out
    }

    // ── LazyFont ──────────────────────────────────────────────────────────────

    private val font: LazyFont? = try {
        LazyFont.loadFont("graphics/fonts/orbitron20aa.fnt")
    } catch (e: FontException) {
        Global.getLogger(AccordionMenu::class.java).error("AccordionMenu: failed to load font", e)
        null
    }

    // One reusable DrawableString per row. Re-created in rebuild(), disposed on
    // the next rebuild() so we never leak GPU buffers.
    private var labelStrings: List<LazyFont.DrawableString> = emptyList()

    private fun rebuildLabels() {
        labelStrings.forEach { if (!it.isDisposed) it.dispose() }

        labelStrings = if (font == null) emptyList() else flatRows.map { row ->
            val isSelected = row.path == selectedPath
            val color = when {
                isSelected -> labelSelectedColor
                !row.node.isLeaf -> labelGroupColor
                else -> labelColor
            }
            font.createText(row.node.label, color, fontSize)
        }
    }

    // ── Sprites ───────────────────────────────────────────────────────────────

    private val arrowDownSprite = Global.getSettings().getSprite("FleetBuilder", "arrow_down")
    private val arrowRightSprite = Global.getSettings().getSprite("FleetBuilder", "arrow_right")

    // ── Panel ─────────────────────────────────────────────────────────────────

    /** The viewport panel; it is attached to [parentPanel] in the init block. */
    val panel: CustomPanelAPI

    private val plugin = object : StarUIPanelPlugin() {

        private var hoverRowIndex: Int = -1

        // ── renderBelow: row backgrounds, separator lines, scroll bar ─────────
        override fun renderBelow(alphaMult: Float) {
            val px = panel.x
            val py = panel.y
            val pw = panel.width
            val ph = panel.height

            GL11.glPushMatrix()
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            // Scissor everything to the viewport.
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(px.toInt(), py.toInt(), pw.toInt(), ph.toInt())

            flatRows.forEachIndexed { i, row ->
                // rowY = bottom edge of row i in screen coords (Y up).
                val rowY = py + ph - (i + 1) * rowHeight + scrollOffsetY
                if (rowY + rowHeight < py || rowY > py + ph) return@forEachIndexed

                val isSelected = row.path == selectedPath
                val bg = when {
                    isSelected -> rowSelectedColor
                    i == hoverRowIndex -> rowHoverColor
                    else -> rowBgColor
                }
                setColor(bg, alphaMult)
                UIUtils.drawRectGL(px, rowY, pw, rowHeight)

                // Separator on the top edge of every row.
                setColor(rowSeparatorColor, alphaMult)
                UIUtils.drawRectGL(px, rowY + rowHeight - 1f, pw, 1f)
            }

            GL11.glDisable(GL11.GL_SCISSOR_TEST)

            // Border around the whole widget (outside scissor so it's always full).
            if (renderUILines)
                UIUtils.renderUILines(px, py, pw, ph, alphaMult, borderColor)

            // Scroll bar track + thumb.
            if (totalContentHeight > visibleHeight) {
                val scrollBarX = px + pw - UIUtils.SCROLLER_WIDTH - 2f
                // Track background
                setColor(scrollBarBgColor, alphaMult)
                UIUtils.drawRectGL(scrollBarX, py, UIUtils.SCROLLER_WIDTH, ph)

                val thumbH = (visibleHeight / totalContentHeight * ph).coerceAtLeast(20f)
                val maxScroll = totalContentHeight - visibleHeight
                val thumbY = py + (scrollOffsetY / maxScroll) * (ph - thumbH)
                setColor(scrollBarColor, alphaMult)
                UIUtils.drawRectGL(scrollBarX, thumbY, UIUtils.SCROLLER_WIDTH, thumbH)
            }

            GL11.glColor4f(1f, 1f, 1f, 1f)
            GL11.glPopMatrix()
        }

        // ── render: arrow sprites + LazyFont labels ───────────────────────────
        override fun render(alphaMult: Float) {
            val px = panel.x
            val py = panel.y
            val pw = panel.width
            val ph = panel.height

            // Scissor labels and arrows to viewport.
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(px.toInt(), py.toInt(), pw.toInt(), ph.toInt())

            // LazyFont needs blend enabled.
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            flatRows.forEachIndexed { i, row ->
                val rowY = py + ph - (i + 1) * rowHeight + scrollOffsetY
                if (rowY + rowHeight < py || rowY > py + ph) return@forEachIndexed

                val rowCY = rowY + rowHeight / 2f
                val indent = indentPerLevel * row.depth
                val arrowX = px + indent + arrowPadLeft

                // ── Arrow sprite (all nodes; leaf uses transparent padding space) ──
                if (!row.node.isLeaf) {
                    val sprite = if (row.isExpanded) arrowDownSprite else arrowRightSprite
                    sprite.setSize(arrowSize, arrowSize)
                    sprite.setAlphaMult(alphaMult)
                    sprite.renderAtCenter(arrowX + arrowSize / 2f, rowCY)
                }

                // ── Label via LazyFont.DrawableString ──────────────────────────
                val ds = labelStrings.getOrNull(i) ?: return@forEachIndexed
                if (ds.isDisposed) return@forEachIndexed

                // Update color live (selection may have changed since last rebuild).
                val isSelected = row.path == selectedPath
                val color = when {
                    isSelected -> labelSelectedColor
                    !row.node.isLeaf -> labelGroupColor
                    else -> labelColor
                }
                // Only update the DrawableString if color actually changed, to
                // avoid triggering an unnecessary GPU buffer rebuild every frame.
                if (ds.baseColor != color) ds.baseColor = color

                // draw(x, y): x = left edge, y = TOP of the text block (LazyFont uses top-left origin).
                val labelX = arrowX + arrowSize + arrowPadRight
                // Center text vertically: top of text = rowCY + half font height.
                val labelY = rowCY + fontSize / 2f

                // Clamp label width so it doesn't overlap the scroll bar.
                val maxLabelW = pw - (labelX - px) - UIUtils.SCROLLER_WIDTH - 4f
                if (ds.maxWidth != maxLabelW) ds.maxWidth = maxLabelW

                ds.draw(labelX, labelY)
            }

            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            GL11.glColor4f(1f, 1f, 1f, 1f)
        }

        // ── processInput: hover, scroll, click ────────────────────────────────
        override fun processInput(events: MutableList<InputEventAPI>) {
            val px = panel.x
            val py = panel.y
            val pw = panel.width
            val ph = panel.height
            val inPanel = UIUtils.isMouseWithinBounds(px, py, pw, ph)

            hoverRowIndex = if (inPanel) rowIndexAt(Global.getSettings().mouseY) else -1

            for (event in events) {
                if (event.isConsumed || !event.isMouseEvent) continue

                if (inPanel) {
                    // Scroll wheel
                    if (event.isMouseScrollEvent) {
                        val delta = if (event.eventValue > 0) -(rowHeight * 2f) else (rowHeight * 2f)
                        val maxScroll = maxOf(0f, totalContentHeight - visibleHeight)
                        scrollOffsetY = (scrollOffsetY + delta).coerceIn(0f, maxScroll)
                        event.consume()
                        continue
                    }

                    // Left-click
                    if (event.isMouseDownEvent && (event.eventValue == 0 || event.eventValue == 1)) {
                        val clickX = event.x.toFloat()
                        val idx = rowIndexAt(event.y)
                        if (idx in flatRows.indices) {
                            handleClick(flatRows[idx], clickX, px, event)
                            event.consume()
                        }
                    }
                }
            }
        }

        override fun advance(amount: Float) {}

        // ── helpers ──────────────────────────────────────────────────────────

        /** Convert screen Y (bottom-up) to a flat row index. */
        private fun rowIndexAt(screenY: Int): Int {
            val py = panel.y
            val ph = panel.height
            val relFromTop = (py + ph) - screenY + scrollOffsetY
            val idx = (relFromTop / rowHeight).toInt()
            return if (idx in flatRows.indices) idx else -1
        }

        private fun setColor(c: Color, alphaMult: Float) {
            GL11.glColor4f(c.red / 255f, c.green / 255f, c.blue / 255f, c.alpha / 255f * alphaMult)
        }
    }

    // ── Click logic ───────────────────────────────────────────────────────────

    /**
     * Handles a click on [row].
     *
     * - Clicking the **arrow hit area** (left portion of the row) toggles expand/collapse
     *   for non-leaf nodes.
     * - Clicking **anywhere else** (label area) selects the node.
     *
     * Both a group node AND a leaf can be selected. Expanding/collapsing a group does NOT
     * change the selection unless the user explicitly clicks the label.
     */
    private fun handleClick(row: Row, clickX: Float, panelX: Float, event: InputEventAPI) {
        val indent = indentPerLevel * row.depth
        val arrowLeft = panelX + indent + arrowPadLeft
        val arrowRight = arrowLeft + arrowSize

        val clickedArrow = !row.node.isLeaf && clickX >= arrowLeft && clickX <= arrowRight

        if (clickedArrow && event.eventValue == 0) {
            // Toggle expand/collapse — do NOT change selection.
            if (expandedPaths.contains(row.path)) expandedPaths.remove(row.path)
            else expandedPaths.add(row.path)
            rebuild()
            rebuildLabels()
        } else {
            if (event.eventValue == 1) { // If right click
                onRightClickSelectCallback?.invoke(row.node)
            } else if (selectedPath != row.path) { // Select this node (group or leaf).
                selectedPath = row.path
                // No rebuild needed — just update DrawableString colors live in render().
                if (event.eventValue == 0)
                    onSelectCallback?.invoke(row.node)
            }
        }
    }

    // ── Traversal helpers ─────────────────────────────────────────────────────

    private fun traverseNodes(
        nodes: List<AccordionNode>, parentPath: String,
        action: (AccordionNode, String) -> Unit
    ) {
        nodes.forEachIndexed { i, node ->
            val path = if (parentPath.isEmpty()) "$i" else "$parentPath/$i"
            action(node, path)
            if (!node.isLeaf) traverseNodes(node.children, path, action)
        }
    }

    private fun findAndSelect(nodes: List<AccordionNode>, parentPath: String, targetId: Any?): Boolean {
        nodes.forEachIndexed { i, node ->
            val path = if (parentPath.isEmpty()) "$i" else "$parentPath/$i"
            if (node.id == targetId) {
                selectedPath = path
                rebuild()
                rebuildLabels()
                onSelectCallback?.invoke(node)
                return true
            }
            if (!node.isLeaf) {
                expandedPaths.add(path) // expand ancestor
                if (findAndSelect(node.children, path, targetId)) return true
                expandedPaths.remove(path) // not found here — collapse again
            }
        }
        return false
    }

    private fun rebuildAll() {
        rebuild()
        rebuildLabels()
    }

    private fun List<AccordionNode>.addToTree(
        node: AccordionNode,
        parentId: Any?
    ): List<AccordionNode> {
        // null parentId = add at root
        if (parentId == null) return this + node

        var added = false

        fun add(nodes: List<AccordionNode>): List<AccordionNode> {
            return nodes.map { existing ->
                if (existing.id == parentId) {
                    added = true
                    existing.copy(children = existing.children + node)
                } else if (!added) {
                    existing.copy(children = add(existing.children))
                } else {
                    existing // stop modifying once added
                }
            }
        }

        return add(this)
    }

    private fun List<AccordionNode>.filterTree(id: Any?): List<AccordionNode> {
        return filter { it.id != id }
            .map { it.copy(children = it.children.filterTree(id)) }
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    init {
        panel = Global.getSettings().createCustom(width, visibleHeight, plugin)
        plugin.panel = panel
        parentPanel.addComponent(panel).inTL(x, y)

        rebuild()
        rebuildLabels()
    }
}
