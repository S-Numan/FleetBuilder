package starficz

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import org.lwjgl.input.Keyboard
import java.awt.Color


internal fun CustomPanelAPI.TooltipMakerPanel(
    width: Float,
    height: Float,
    withScroller: Boolean = false,
    builder: TooltipMakerAPI.() -> Unit = {}
): TooltipMakerAPI {
    val tooltipMakerPanel = createUIElement(width, height, withScroller)
    addUIElement(tooltipMakerPanel)
    return tooltipMakerPanel.apply(builder)
}

internal fun UIPanelAPI.Text(
    text: String,
    font: Font? = null,
    color: Color? = null,
    highlightedText: Collection<Pair<String, Color>>? = null,
    builder: BoxedUILabel.() -> Unit = {}
): BoxedUILabel {
    return this.addPara(text, font, color, highlightedText).apply(builder)
}

internal fun UIPanelAPI.LabelledValue(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    width: Float,
    builder: UIComponentAPI.() -> Unit = {}
): UIComponentAPI {
    return this.addLabelledValue(label, value, labelColor, valueColor, width).apply(builder)
}

internal fun UIPanelAPI.TextField(
    width: Float,
    height: Float,
    font: Font,
    builder: TextFieldAPI.() -> Unit = {}
): TextFieldAPI {
    return this.addTextField(width, height, font).apply(builder)
}

internal fun UIPanelAPI.Image(
    imageSpritePath: String,
    width: Float,
    height: Float,
    builder: BoxedUIImage.() -> Unit = {} // Configures BoxedUiImage
): BoxedUIImage {
    return addImage(imageSpritePath, width, height).apply(builder)
}

internal fun UIComponentAPI.Tooltip(
    location: TooltipMakerAPI.TooltipLocation,
    width: Float,
    padding: Float? = null,
    builder: TooltipMakerAPI.() -> Unit = {} // Configuration lambda for tooltip content
) {
    this.addTooltip(location, width, padding, builder)
}

internal fun UIPanelAPI.Button(
    text: String,
    baseColor: Color,
    bgColor: Color,
    align: Alignment = Alignment.MID,
    style: CutStyle = CutStyle.TL_BR,
    width: Float,
    height: Float,
    font: Font? = null,
    builder: ButtonAPI.() -> Unit = {}
): ButtonAPI {
    return this.addButton(text, null, baseColor, bgColor, align, style, width, height, font).apply(builder)
}

fun UIPanelAPI.CustomPanel(
    width: Float,
    height: Float,
    builder: CustomPanelAPI.(plugin: ExtendableCustomUIPanelPlugin) -> Unit = {}
): CustomPanelAPI {
    val panel = Global.getSettings().createCustom(width, height, null)
    val plugin = ExtendableCustomUIPanelPlugin(panel)
    panel.setPlugin(plugin)
    this.addComponent(panel)
    panel.builder(plugin)
    return panel
}

