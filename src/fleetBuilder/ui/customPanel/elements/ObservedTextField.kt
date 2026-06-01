package fleetBuilder.ui.customPanel.elements

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.Fonts
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import fleetBuilder.otherMods.starficz.StarUIPanelPlugin

open class ObservedTextField(
    width: Float,
    height: Float = 24f,
    font: String = Fonts.DEFAULT_SMALL,
    pad: Float = 0f,
    initialText: String = ""
) : StarUIPanelPlugin() {

    fun getText(): String {
        return textField.text ?: ""
    }

    val component: UIComponentAPI
    val textField: TextFieldAPI

    var lastText: String = initialText

    // Lambda that can be set later
    private var onTextChanged: ((String) -> Unit)? = null

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
        onTextChanged = callback
        // optionally call immediately with current text
        callback(lastText)
    }

    override fun advance(amount: Float) {
        val currentText = textField.text ?: return
        if (currentText == lastText) return

        lastText = currentText
        onTextChanged?.invoke(currentText)

        super.advance(amount)
    }
}