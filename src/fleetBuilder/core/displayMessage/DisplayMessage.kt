package fleetBuilder.core.displayMessage

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.FBSettings
import fleetBuilder.features.logMessageAppender.NoDisplayThrowable
import fleetBuilder.ui.customPanel.common.DialogPanel
import fleetBuilder.core.FBMisc.getCallerClass
import org.apache.log4j.Level
import org.lazywizard.console.Console
import java.awt.Color

object DisplayMessage {

    //Prevent error spam
    private val recentErrors = mutableMapOf<String, Long>()
    private const val ERROR_SPAM_INTERVAL_MS = 4000 // 4 seconds

    @JvmOverloads
    fun showError(short: String, full: String, e: Exception? = null) {
        val now = System.currentTimeMillis()

        // Combine both short + full messages to uniquely identify the error
        val key = "$short::$full"
        val lastTime = recentErrors[key] ?: 0L

        // Skip if the same error occurred recently
        if (now - lastTime < ERROR_SPAM_INTERVAL_MS) {
            return
        }

        // Record this error occurrence
        recentErrors[key] = now

        // Clean up stale entries occasionally
        if (recentErrors.size > 100) {
            val cutoff = now - ERROR_SPAM_INTERVAL_MS
            recentErrors.entries.removeIf { it.value < cutoff }
        }

        val callerClass: Class<*> = getCallerClass() ?: javaClass
        // Console or logger output
        if (FBSettings.isConsoleModEnabled) {
            if (e != null) {
                Console.showException(full, e)
            } else {
                Console.showMessage(callerClass.name + " - " + full, Level.ERROR)
            }
        } else {
            logMessage(callerClass, full, Level.ERROR, displayMessage = false)
        }

        // Show short message to player
        if (!FBSettings.hideErrorMessages) {
            showMessageCustom(short, Color.RED)
        }
    }

    @JvmOverloads
    fun showError(short: String, e: Exception? = null) {
        showError(short, short, e)
    }

    @JvmOverloads
    fun showMessage(
        short: String,
        color: Color?,
        highlight: String,
        highlightColor: Color = Misc.getHighlightColor(),
    ) {
        var defaultColor = color

        val gameState = Global.getCurrentState()
        if (gameState == GameState.CAMPAIGN) {
            if (defaultColor == null)
                defaultColor = Misc.getTooltipTitleAndLightHighlightColor()

            val ui = Global.getSector().campaignUI
            ui.messageDisplay.addMessage(short, defaultColor, highlight, highlightColor)
        } else if (gameState == GameState.COMBAT) {
            if (defaultColor == null)
                defaultColor = Misc.getTextColor()

            val engine = Global.getCombatEngine()
            val ui = engine.combatUI

            val highlightIndex = short.indexOf(highlight)
            if (highlight.isEmpty() || highlightIndex == -1) { // Highlight text not found.
                ui.addMessage(1, defaultColor, short)
            } else {
                val before = short.substring(0, highlightIndex)
                val after = short.substring(highlightIndex + highlight.length)

                ui.addMessage(
                    0,
                    defaultColor, before,
                    highlightColor, highlight,
                    defaultColor, after
                )
            }
        } else
            DrawMessageOnTop.addMessage(short, color ?: Misc.getTextColor())

        //Global.getSoundPlayer().playUISound("ui_noise_static_message_quiet", 1f, 1f)
    }

    @JvmOverloads
    fun showMessage(short: String, color: Color? = null) {
        showMessage(short, color, "")
    }

    @JvmOverloads
    fun showMessage(short: String, highlight: String, highlightColor: Color = Misc.getHighlightColor()) {
        showMessage(short, null, highlight, highlightColor)
    }

    @JvmOverloads
    fun showMessageCustom(message: String, color: Color? = null) {
        DrawMessageOnTop.addMessage(message, color ?: Misc.getTextColor())
    }
    /*
    val stackTrace = Exception().stackTrace
        var stackTraceString = ""
        for(stack in stackTrace) {
            stackTraceString += stack
        }
        Global.getLogger(this.javaClass).error("\n" + stackTraceString)
     */

    /*fun dialogMessage(inputDialog: BasePopUpUI) {

    }*/

    @JvmOverloads
    fun dialogMessage(title: String, message: String, messageColor: Color = Misc.getTextColor()) {
        val dialog = DialogPanel(headerTitle = title)
        dialog.allowHotkeyQuit = false

        dialog.show(width = 800f, height = 400f) { ui ->
            ui.addPara(message, messageColor, 0f)

            dialog.addActionButtons(alignment = Alignment.MID, addCancelButton = false)
        }
    }


    /**
     * Logs a message with the specified level.
     *
     * If [displayMessage] is false, do not show on screen even if the FleetBuilder setting to display logged messages would be true.
     */
    @JvmOverloads
    fun logMessage(javaClass: Class<*>, message: String, level: Level, displayMessage: Boolean = true) {
        if (displayMessage) {
            when (level) {
                Level.INFO -> Global.getLogger(javaClass).info(message)
                Level.WARN -> Global.getLogger(javaClass).warn(message)
                Level.ERROR -> Global.getLogger(javaClass).error(message)
                Level.FATAL -> Global.getLogger(javaClass).fatal(message)
                Level.DEBUG -> Global.getLogger(javaClass).debug(message)
            }
        } else {
            when (level) {
                Level.INFO -> Global.getLogger(javaClass).info(message, NoDisplayThrowable())
                Level.WARN -> Global.getLogger(javaClass).warn(message, NoDisplayThrowable())
                Level.ERROR -> Global.getLogger(javaClass).error(message, NoDisplayThrowable())
                Level.FATAL -> Global.getLogger(javaClass).fatal(message, NoDisplayThrowable())
                Level.DEBUG -> Global.getLogger(javaClass).debug(message, NoDisplayThrowable())
            }
        }
    }
}