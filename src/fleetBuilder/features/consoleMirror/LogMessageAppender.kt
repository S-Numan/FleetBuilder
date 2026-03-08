package fleetBuilder.features.consoleMirror

import fleetBuilder.core.ModSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.Level
import org.apache.log4j.spi.LoggingEvent
import org.lazywizard.console.Console

class LogMessageAppender : AppenderSkeleton() {
    override fun append(event: LoggingEvent) {
        if (ModSettings.addLogsToConsoleModConsoleLevel == Level.OFF && ModSettings.addLogsToDisplayMessageLevel == Level.OFF)
            return

        // Ignore logs from Console class to prevent infinite loops
        if (event.loggerName == Console::class.java.name) return

        if (event.getLevel().isGreaterOrEqual(ModSettings.addLogsToConsoleModConsoleLevel)) {
            if (ModSettings.isConsoleModEnabled && ModSettings.addLogsToConsoleModConsoleLevel != Level.OFF) {
                val msg = buildString {
                    append("[${event.getLevel()}] ")
                    append("${event.loggerName} - ")
                    append(event.renderedMessage)

                    event.throwableStrRep?.let {
                        append("\n")
                        append(it.joinToString("\n"))
                    }
                }

                Console.showMessage(msg, Level.ALL)
            }
            if (ModSettings.addLogsToDisplayMessageLevel != Level.OFF) {
                DisplayMessage.showMessageCustom(event.renderedMessage)
            }
        }
    }

    override fun close() {}

    override fun requiresLayout(): Boolean = false
}