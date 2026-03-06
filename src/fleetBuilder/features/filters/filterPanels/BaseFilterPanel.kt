package fleetBuilder.features.filters.filterPanels

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.*
import org.lwjgl.input.Mouse

abstract class BaseFilterPanel(
    width: Float,
    height: Float,
    parent: UIPanelAPI,
    val defaultText: String
) : CustomUIPanelPlugin {

    protected val mainPanel: CustomPanelAPI =
        Global.getSettings().createCustom(width, height, this)

    fun getPanel(): CustomPanelAPI = mainPanel

    protected val textField: TextFieldAPI
    protected var prevString: String = defaultText

    init {
        val tooltip = mainPanel.createUIElement(width, height, false)
        textField = tooltip.addTextField(width, height, Fonts.DEFAULT_SMALL, 0f)
        textField.isLimitByStringWidth = false
        textField.maxChars = 255

        resetText()

        mainPanel.addUIElement(tooltip).inTL(0f, 0f)
        parent.addComponent(mainPanel)
    }

    fun resetText() {
        textField.text = defaultText
        prevString = defaultText
    }

    override fun advance(amount: Float) {
        handleFocus()

        if (textField.text == prevString) return
        if (textField.text == defaultText.dropLast(1)) resetText() // Hack to fix hasFocus not properly reporting in some conditions

        onFilterChanged(textField.text)

        prevString = textField.text
    }

    private fun handleFocus() {
        if (textField.hasFocus()) {
            if (textField.text == defaultText) {
                textField.text = ""
                prevString = ""
            }
        } else if (textField.text.isEmpty()) {
            resetText()
            prevString = " " // Hack to fix hasFocus not properly reporting in some conditions
        } else if (textField.text.dropLast(1) == defaultText) { // Hack to fix hasFocus not properly reporting in some conditions
            textField.text = textField.text.takeLast(1)
            prevString = ""
        }
    }

    protected fun parseSearchTokens(input: String): List<String> {
        val regex = Regex("""(?:[^\s"]+|"[^"]*"|'[^']*')+""")
        return regex.findAll(input.lowercase())
            .map { match ->
                val token = match.value
                val hasDash = token.startsWith("-") && token.length > 1 && token[1] != '-'
                val core = if (hasDash) token.substring(1) else token
                val trimmed = core.trim('"', '\'', '-')
                if (hasDash) "-$trimmed" else trimmed
            }
            .filter { it.isNotBlank() }
            .toList()
    }

    override fun processInput(events: List<InputEventAPI>) {
        if (Mouse.isButtonDown(2)) {
            resetText()
            onMiddleMouseReset()
        }
    }

    protected open fun onMiddleMouseReset() {}

    protected abstract fun onFilterChanged(text: String)

    override fun positionChanged(position: PositionAPI) = Unit
    override fun renderBelow(alphaMult: Float) = Unit
    override fun render(alphaMult: Float) = Unit
    override fun buttonPressed(buttonId: Any?) {}
}
