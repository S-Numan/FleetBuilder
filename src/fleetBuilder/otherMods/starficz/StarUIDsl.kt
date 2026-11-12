package fleetBuilder.otherMods.starficz

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.*
import fleetBuilder.otherMods.starficz.Anchor.AnchorData
import fleetBuilder.otherMods.starficz.internal.HorizontalStrategy
import fleetBuilder.otherMods.starficz.internal.StackLayout
import fleetBuilder.otherMods.starficz.internal.VerticalStrategy
import org.lwjgl.input.Keyboard
import java.awt.Color


fun UIPanelAPI.CustomPanel(
    width: Float,
    height: Float,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: CustomPanelAPI.() -> Unit = {}
): CustomPanelAPI {
    val panel = Global.getSettings().createCustom(width, height, null)
    this.addComponent(panel)
    panel.applyAnchor(anchor) // by default anchor in TL
    panel.builder()
    return panel
}

fun CustomPanelAPI.Plugin(builder: StarUIPanelPlugin.() -> Unit): CustomUIPanelPlugin {
    val plugin = StarUIPanelPlugin()
    plugin.panel = this
    
    this.setPlugin(plugin)
    plugin.builder()
    return plugin
}

fun UIPanelAPI.ScrollPanel(
    width: Float,
    height: Float,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: BoxedScrollPanel.() -> Unit = {}
): BoxedScrollPanel {
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tempTMAPI = tempPanel.createUIElement(width, height, true)
    tempPanel.addUIElement(tempTMAPI) // make and add a temp TMAPI to a custom panel to make a scroll panel
    val scrollPanel = tempPanel.children[0] as ScrollPanelAPI // hijack the scroll panel
    scrollPanel.removeComponent(tempTMAPI) // remove the temp TMAPI we just added to it
    val boxedScrollPanel = BoxedScrollPanel(scrollPanel) // box and use the scroll panel as needed
    boxedScrollPanel.setSize(width, height)
    boxedScrollPanel.setContentSize(width, height)
    this.addComponent(scrollPanel)
    scrollPanel.applyAnchor(anchor) // by default anchor in TL
    return boxedScrollPanel.apply(builder)
}

fun UIPanelAPI.VerticalStackLayout(
    xMargin: Float = 0f,
    yMargin: Float = 0f,
    spacing: Float = 0f,
    alignment: Alignment = Alignment.TMID,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: CustomPanelAPI.() -> Unit = {}
): CustomPanelAPI {
    return this.StackLayout(VerticalStrategy, anchor, alignment, yMargin, xMargin, spacing, builder)
}

fun UIPanelAPI.HorizontalStackLayout(
    xMargin: Float = 0f,
    yMargin: Float = 0f,
    spacing: Float = 0f,
    alignment: Alignment = Alignment.LMID,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: CustomPanelAPI.() -> Unit = {}
): CustomPanelAPI {
    return this.StackLayout(HorizontalStrategy, anchor, alignment, xMargin, yMargin, spacing, builder)
}

fun CustomPanelAPI.TooltipMakerPanel(
    width: Float,
    height: Float,
    withScroller: Boolean = false,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: TooltipMakerAPI.() -> Unit = {}
): TooltipMakerAPI {
    val tooltipMakerPanel = createUIElement(width, height, withScroller)
    addUIElement(tooltipMakerPanel)
    return tooltipMakerPanel.apply {
        applyAnchor(anchor)
        apply(builder)
    }
}

fun UIComponentAPI.Tooltip(
    width: Float,
    location: TooltipMakerAPI.TooltipLocation,
    margin: Float? = null,
    builder: TooltipMakerAPI.() -> Unit = {}
) {
    this.addTooltip(location, width, margin, builder)
}

fun UIPanelAPI.Text(
    text: String,
    font: Font? = null,
    color: Color? = null,
    highlightedText: Collection<Pair<String, Color>>? = null,
    widthOverride: Float? = null,
    xPad: Float = 0f, yPad: Float = 0f,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: BoxedUILabel.() -> Unit = {}
): BoxedUILabel {
    return this.addPara(text, font, color, highlightedText, widthOverride, xPad, yPad).apply {
        applyAnchor(anchor)
        apply(builder)
    }
}

fun UIPanelAPI.LabelledValue(
    width: Float,
    label: String,
    value: String,
    labelColor: Color = Global.getSettings().basePlayerColor,
    valueColor: Color = Global.getSettings().basePlayerColor,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: UIComponentAPI.() -> Unit = {}
): UIComponentAPI {
    return this.addLabelledValue(label, value, labelColor, valueColor, width).apply {
        applyAnchor(anchor)
        apply(builder)
    }
}

fun UIPanelAPI.TextField(
    width: Float,
    height: Float,
    font: Font,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: TextFieldAPI.() -> Unit = {}
): TextFieldAPI {
    return this.addTextField(width, height, font).apply {
        applyAnchor(anchor)
        apply(builder)
    }
}

fun UIPanelAPI.Image(
    width: Float,
    height: Float,
    imageSpritePath: String,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: BoxedUIImage.() -> Unit = {}
): BoxedUIImage {
    return addImage(imageSpritePath, width, height).apply {
        applyAnchor(anchor)
        apply(builder)
    }
}

fun UIPanelAPI.ShipDisplay(
    width: Float,
    height: Float,
    fleetMember: FleetMemberAPI? = null,
    style: BoxedUIShipPreview.Style = BoxedUIShipPreview.Style.NORMAL,
    color: Color = Global.getSettings().getColor("textFriendColor"),
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: BoxedUIShipPreview.() -> Unit = {}
): BoxedUIShipPreview {
    return addShipPreview(width, height, fleetMember, style, color).apply {
        applyAnchor(anchor)
        apply(builder)
    }
}

fun UIPanelAPI.Button(
    width: Float,
    height: Float,
    text: String,
    baseColor: Color = Global.getSettings().basePlayerColor,
    bgColor: Color = Global.getSettings().darkPlayerColor,
    font: Font? = null,
    shortcut: Int? = null,
    align: Alignment = Alignment.MID,
    style: CutStyle = CutStyle.TL_BR,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: ButtonAPI.() -> Unit = {}
): ButtonAPI {
    return this.addButton(text, null, baseColor, bgColor, align, style, width, height, font, shortcut).apply {
        applyAnchor(anchor)
        apply(builder)
    }
}

fun UIPanelAPI.AreaCheckbox(
    width: Float,
    height: Float,
    text: String,
    baseColor: Color = Global.getSettings().basePlayerColor,
    bgColor: Color = Global.getSettings().darkPlayerColor,
    brightColor: Color = Global.getSettings().brightPlayerColor,
    font: Font? = null,
    leftAlign: Boolean = false,
    flag: Flag? = null,
    buttonGroup: ButtonGroup? = null,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: ButtonAPI.() -> Unit = {}
): ButtonAPI {
    val validGroup = (buttonGroup != null && flag != null)

    val button = this.addAreaCheckbox(
        text, null, baseColor, bgColor, brightColor, width, height, font,
        leftAlign, if (!validGroup) flag else null
    ).apply {
        applyAnchor(anchor)
        apply(builder)
    }

    if (validGroup) buttonGroup!!.addButtonToGroup(button, flag!!)

    return button
}

fun UIPanelAPI.Checkbox(
    width: Float,
    height: Float,
    size: ButtonAPI.UICheckboxSize? = ButtonAPI.UICheckboxSize.SMALL,
    flag: Flag? = null,
    buttonGroup: ButtonGroup? = null,
    anchor: AnchorData = Anchor.inside.topLeft.ofParent(),
    builder: ButtonAPI.() -> Unit = {}
): ButtonAPI {
    val validGroup = (buttonGroup != null && flag != null)

    val button = this.addCheckbox(
        width, height, size = size,
        flag = if (!validGroup) flag else null
    ).apply {
        applyAnchor(anchor)
        apply(builder)
    }

    if (validGroup) buttonGroup!!.addButtonToGroup(button, flag!!)

    return button
}

class ButtonGroup {
    private val allFlags: MutableCollection<Flag> = mutableListOf()

    internal fun addButtonToGroup(button: ButtonAPI, flag: Flag) {
        allFlags.add(flag)
        button.isChecked = flag.isChecked
        button.onClick {
            if (allFlags.count { it.isChecked } == 1 && flag.isChecked) {
                // If the only active item is clicked, re-enable all items in the group.
                allFlags.forEach { it.isChecked = true }
            } else {
                // if multiselect key (Shift or Ctrl) is held, toggle the clicked filter
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
                    Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ||
                    Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ||
                    Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
                ) {
                    flag.isChecked = !flag.isChecked
                } else { // if no modifier key is held, only exclusively enable the clicked filter
                    allFlags.forEach { it.isChecked = (it === flag) }
                }
            }
            // sync the button to the flag
            button.isChecked = flag.isChecked
        }
    }
}