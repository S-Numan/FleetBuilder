package fleetBuilder.features.consoleMirror

import fleetBuilder.core.ModSettings
import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.Level
import org.apache.log4j.spi.LoggingEvent
import org.lazywizard.console.Console

class ConsoleMirrorAppender : AppenderSkeleton() {
    override fun append(event: LoggingEvent) {
        if (ModSettings.addLogsToConsoleModConsoleLevel == Level.OFF)
            return
        if (event.getLevel().isGreaterOrEqual(ModSettings.addLogsToConsoleModConsoleLevel)) {
            val msg = buildString {
                append("[${event.getLevel()}] ")
                append("${event.loggerName} - ")
                append(event.renderedMessage)

                event.throwableStrRep?.let {
                    append("\n")
                    append(it.joinToString("\n"))
                }
            }

            Console.showMessage(msg)
        }
    }

    override fun close() {}

    override fun requiresLayout(): Boolean = false
}