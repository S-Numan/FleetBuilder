package fleetBuilder.features.logMessageAppender

import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.FBSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.Level
import org.apache.log4j.spi.LoggingEvent
import org.lazywizard.console.Console
import java.awt.Color

internal class LogMessageAppender : AppenderSkeleton() {
    override fun append(event: LoggingEvent) {
        if (FBSettings.addLogsToConsoleModConsoleLevel == Level.OFF && FBSettings.addLogsToDisplayMessageLevel == Level.OFF)
            return

        // Ignore logs from Console class to prevent infinite loops
        if (event.loggerName == Console::class.java.name) return

        val level = event.getLevel()
        if (level.isGreaterOrEqual(FBSettings.addLogsToConsoleModConsoleLevel)) {
            if (FBSettings.isConsoleModEnabled && FBSettings.addLogsToConsoleModConsoleLevel != Level.OFF) {
                val msg = buildString {
                    append("[${level}] ")
                    append("${event.loggerName} - ")
                    append(event.renderedMessage)

                    if (event.throwableInformation?.throwable is NoDisplayThrowable)
                        return@buildString

                    event.throwableStrRep?.let {
                        append("\n")
                        append(it.joinToString("\n"))
                    }

                }

                Console.showMessage(msg, Level.ALL)
            }

            if (FBSettings.addLogsToDisplayMessageLevel == Level.OFF || event.throwableInformation?.throwable is NoDisplayThrowable)
                return
            when (level) {
                Level.WARN -> DisplayMessage.showMessageCustom(event.renderedMessage, Color.yellow)
                Level.ERROR, Level.FATAL -> DisplayMessage.showMessageCustom(event.renderedMessage, Color.red)
                else -> DisplayMessage.showMessageCustom(event.renderedMessage, Misc.getTextColor())
            }
        }
    }

    override fun close() {}

    override fun requiresLayout(): Boolean = false
}

class NoDisplayThrowable : Throwable(null, null, false, false) {
    override fun fillInStackTrace(): Throwable = this
    override fun toString(): String = ""
}