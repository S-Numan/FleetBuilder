package fleetBuilder.util

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.ModSettings
import fleetBuilder.config.ModSettings.isConsoleModEnabled
import fleetBuilder.integration.combat.DrawMessageInTitle
import fleetBuilder.variants.MissingElements
import org.apache.log4j.Level
import org.lazywizard.console.Console
import java.awt.Color

object DisplayMessage {

    //Prevent error spam
    private val recentErrors = mutableMapOf<String, Long>()
    private const val ERROR_SPAM_INTERVAL_MS = 4000 // 4 seconds

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

        // Show short message to player
        showMessage(short, Color.RED)

        // Console or logger output
        if (isConsoleModEnabled) {
            if (e != null) {
                Console.showException(full, e)
            } else {
                Console.showMessage(full, Level.ERROR)
            }

            //CampaignEngine.getInstance().campaignUI.messageList.messages.forEach { message -> }//TODO, remove console message in bottom left messages.
            //Global.getSector().campaignUI.messageDisplay.removeMessage(short)
        } else {
            Global.getLogger(this.javaClass).error(full)
        }

        Global.getSoundPlayer().playUISound("ui_selection_cleared", 1f, 1f)
    }


    fun showError(short: String, e: Exception? = null) {
        showError(short, short, e)
    }

    fun showMessage(short: String, color: Color?, highlight: String, highlightColor: Color = Misc.getHighlightColor()) {
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
        } else if (gameState == GameState.TITLE) {

            DrawMessageInTitle.addMessage(short)

            Global.getSoundPlayer().playUISound("ui_noise_static_message_quiet", 1f, 1f)
        }
    }

    fun showMessage(short: String, color: Color? = null) {
        showMessage(short, color, "")
    }

    fun showMessage(short: String, highlight: String, highlightColor: Color = Misc.getHighlightColor()) {
        showMessage(short, null, highlight, highlightColor)
    }

    fun logMessage(message: String, level: Level, javaClass: Class<*>) {
        if (ModSettings.isConsoleModEnabled)
            Console.showMessage(message, level)
        else if (level == Level.WARN)
            Global.getLogger(javaClass).warn(message)
        else if (level == Level.ERROR)
            Global.getLogger(javaClass).error(message)
        else if (level == Level.DEBUG)
            Global.getLogger(javaClass).debug(message)
        else if (level == Level.INFO)
            Global.getLogger(javaClass).info(message)
        else if (level == Level.FATAL)
            Global.getLogger(javaClass).fatal(message)
    }
    /*
    val stackTrace = Exception().stackTrace
        var stackTraceString = ""
        for(stack in stackTrace) {
            stackTraceString += stack
        }
        Global.getLogger(this.javaClass).error("\n" + stackTraceString)
     */
}