package fleetBuilder.integration.combat

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.title.TitleScreenState
import com.fs.state.AppDriver
import org.lazywizard.lazylib.ui.FontException
import org.lazywizard.lazylib.ui.LazyFont
import java.awt.Color

internal class DrawMessageInTitle : EveryFrameCombatPlugin {
    override fun init(engine: CombatEngineAPI?) {}


    var toDraw: LazyFont.DrawableString? = null

    // Set up the font and the DrawableString; only has to be done once
    fun doInit(engine: CombatEngineAPI) {

        // Load the chosen .fnt file
        // Fonts are cached globally, so it's acceptable for each class using the same
        // font to request their own copy of it - they will all share the underlying data
        val font: LazyFont
        try {
            font = LazyFont.loadFont("graphics/fonts/orbitron20aa.fnt")
        } catch (e: FontException) {
            Global.getLogger(this.javaClass).error("Failed to load font", e)
            engine.removePlugin(this);
            return;
        }

        // Create a renderable block of text
        // In this case, the text will font size 15, and default to yellow text
        toDraw = font.createText("This is some sample text.", Color.YELLOW, 20f);

        // Enable line wrapping when text reaches 400 pixels wide
        toDraw!!.maxWidth = 400f;

        // If you need to add text to the DrawableString, do so like this:
        toDraw!!.append("\nThis is a second line of sample text. It will be drawn orange.", Color.ORANGE)
        toDraw!!.append(
            "\nThis is a third line of sample text that shows off the automatic" +
                    " word wrapping when a line of text reaches the maximum width you've chosen.\n" +
                    "Since this append doesn't have a color attached, it will return to the original yellow."
        )

        toDraw!!.append("You can also chain appends,").append(" like this,", Color.BLUE)
            .append(" to make writing text easier.");
    }

    var init = false

    override fun processInputPreCoreControls(amount: Float, events: MutableList<InputEventAPI>?) {
    }

    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        val state = AppDriver.getInstance().currentState
        if (state !is TitleScreenState) return

        if (!init) {
            doInit(Global.getCombatEngine())
            init = true
        }
    }

    override fun renderInWorldCoords(viewport: ViewportAPI?) {
    }

    override fun renderInUICoords(viewport: ViewportAPI?) {
        // Call draw() once per frame to render the text
        // In this case, draw the text slightly below the mouse cursor
        // The draw point is the top left corner of the textbox, so we adjust the X
        // position to center the text horizontally below the mouse cursor
        //toDraw?.draw(Mouse.getX() - (toDraw!!.width / 2f), Mouse.getY() - 30f)
    }
}