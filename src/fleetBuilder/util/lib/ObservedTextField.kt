package fleetBuilder.util.lib

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI

class ObservedTextField(
    width: Float,
    height: Float,
    font: String,
    pad: Float,
    initialText: String = ""
) : CustomUIPanelPlugin {

    fun getText(): String {
        return textField.text ?: ""
    }

    val component: UIComponentAPI
    val textField: TextFieldAPI

    var lastText: String = initialText

    // Lambda that can be set later
    private var onTextChangedLambda: ((String) -> Unit)? = null

    init {
        val panel = Global.getSettings().createCustom(width, height, this)
        component = panel

        val ui = panel.createUIElement(width, height, false)

        textField = ui.addTextField(
            width,
            height,
            font,
            pad
        )

        textField.isLimitByStringWidth = false
        textField.maxChars = 255

        textField.text = initialText
        panel.addUIElement(ui).inTL(-5f, 0f)
    }

    fun onTextChanged(callback: (String) -> Unit) {
        onTextChangedLambda = callback
        // optionally call immediately with current text
        callback(lastText)
    }

    override fun advance(amount: Float) {
        val currentText = textField.text ?: return
        if (currentText == lastText) return

        lastText = currentText
        onTextChangedLambda?.invoke(currentText)
    }

    override fun processInput(events: List<InputEventAPI?>?) {}
    override fun buttonPressed(buttonId: Any?) {}
    override fun positionChanged(position: PositionAPI) {}
    override fun renderBelow(alphaMult: Float) {}
    override fun render(alphaMult: Float) {}
}
