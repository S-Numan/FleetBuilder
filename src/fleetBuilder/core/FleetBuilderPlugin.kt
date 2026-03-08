package fleetBuilder.core

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global

class FleetBuilderPlugin : BaseModPlugin() {
    // Cause the lazy class loader to load these classes preemptively to prevent issues.
    companion object {
        init {
            try {
                Class.forName("org.apache.log4j.Layout")
                Class.forName("org.apache.log4j.spi.LoggingEvent")
                Class.forName("org.apache.log4j.Priority")
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    val eventDispatcher = EventDispatcher()
    override fun onApplicationLoad() {
        super.onApplicationLoad()

        eventDispatcher.onApplicationLoad()
    }

    override fun onGameLoad(newGame: Boolean) {
        super.onGameLoad(newGame)

        val sector = Global.getSector() ?: return

        sector.addTransientScript(eventDispatcher)

        eventDispatcher.onGameLoad(newGame)
    }

    override fun beforeGameSave() {
        super.beforeGameSave()

        eventDispatcher.beforeGameSave()
    }

    override fun afterGameSave() {
        super.afterGameSave()

        eventDispatcher.afterGameSave()
    }

    override fun onDevModeF8Reload() {
        super.onDevModeF8Reload()

        eventDispatcher.onDevModeF8Reload()
    }
}