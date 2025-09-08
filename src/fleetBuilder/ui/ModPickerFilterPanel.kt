package fleetBuilder.ui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.campaign.ui.UITable
import org.lwjgl.input.Mouse
import starficz.ReflectionUtils.get
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.invoke
import starficz.getChildrenCopy

class ModPickerFilterPanel(
    width: Float,
    height: Float,
    private var parentPanel: UIPanelAPI,
    private var modPicker: UIPanelAPI
) : CustomUIPanelPlugin {

    private val defaultText = "Search for a modspec"
    private var mainPanel: CustomPanelAPI

    private var textField: TextFieldAPI
    private lateinit var prevString: String

    fun resetText() {
        textField.text = defaultText
        prevString = defaultText
    }


    init {
        mainPanel = Global.getSettings().createCustom(width, height, this)

        val tooltip = mainPanel.createUIElement(width, height, false)
        textField = tooltip.addTextField(width, height, Fonts.DEFAULT_SMALL, 0f)
        textField.isLimitByStringWidth = false
        textField.maxChars = 255
        resetText()

        mainPanel.addUIElement(tooltip).inTL(0f, 0f)
        parentPanel.addComponent(mainPanel)//.inBR(xPad, yPad)
        mainPanel.position.inTL(0f, 5f)
    }

    override fun advance(amount: Float) {
        if (textField.hasFocus()) {
            if (textField.text == defaultText) {//On focus
                textField.text = ""
                prevString = ""
            }
        } else if (textField.text == "") {//Left focus with no input?
            resetText()
            return
        }

        if (textField.text == prevString) {
            return
        }

        filterModPickerList()

        prevString = textField.text
    }

    private fun filterModPickerList() {
        if (textField.text.isEmpty()) {
            refreshUI()
            return
        }
        if (textField.text == defaultText) return

        refreshUI()

        val modPickerTable = modPicker.getChildrenCopy().find { it is UITable }

        val list = modPickerTable?.invoke("getList")
        val items = list?.invoke("getItems") as? List<Any?> ?: return

        val regex = Regex("""(?:[^\s"]+|"[^"]*"|'[^']*')+""")//Non-space tokens or quoted strings with "double" or 'single' quotes
        val descriptions = regex.findAll(textField.text.lowercase())
            .map { match ->//Trim quotes, keep the dash
                val token = match.value
                val hasInitialDash = token.startsWith("-") && token.length > 1 && token[1] != '-'
                val core = if (hasInitialDash) token.substring(1) else token
                val trimmed = core.trim('"', '\'', '-') // Trim quotes and dashes
                if (hasInitialDash) "-$trimmed" else trimmed
            }
            .filter { it.isNotBlank() }
            .toList()

        descriptions.forEach { desc ->
            val itemsToRemove = mutableListOf<Any?>()
            items.forEach { item ->
                if (item == null) return@forEach
                val tooltip = item.invoke("getTooltip") ?: return@forEach
                val modSpecField = tooltip.getFieldsMatching(fieldAssignableTo = HullModSpecAPI::class.java)
                val modSpec = modSpecField.getOrNull(0)?.get(tooltip) as? HullModSpecAPI
                    ?: return@forEach

                if (desc.startsWith("-")) {
                    if (modSpec.matchesDescription(desc.removePrefix("-")))
                        itemsToRemove.add(item)
                } else if (!modSpec.matchesDescription(desc)) {
                    itemsToRemove.add(item)
                }
            }
            itemsToRemove.forEach { list.invoke("removeItem", it) }
        }

        list.invoke("collapseEmptySlots")
    }

    private fun refreshUI() {
        modPicker.invoke("createUI")
        modPicker.invoke("restoreTags")
        parentPanel.addComponent(mainPanel)
    }

    private fun HullModSpecAPI.matchesDescription(desc: String): Boolean {
        return when {
            this.displayName.lowercase().startsWith(desc) -> true
            this.manufacturer.lowercase().startsWith(desc) -> true
            else -> false
        }
    }

    override fun positionChanged(position: PositionAPI) = Unit

    override fun renderBelow(alphaMult: Float) = Unit

    override fun render(alphaMult: Float) = Unit

    override fun processInput(events: List<InputEventAPI>) {
        if (Mouse.isButtonDown(2)) {
            resetText()
        }
    }

    override fun buttonPressed(buttonId: Any?) {

    }

}