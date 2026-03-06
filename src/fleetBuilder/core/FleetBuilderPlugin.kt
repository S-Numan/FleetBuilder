package fleetBuilder.core

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global

class FleetBuilderPlugin : BaseModPlugin() {
    val eventDispatcher = EventDispatcher()
    override fun onApplicationLoad() {
        super.onApplicationLoad()

        eventDispatcher.onApplicationLoad()
    }

    override fun onGameLoad(newGame: Boolean) {
        super.onGameLoad(newGame)

        val sector = Global.getSector() ?: return

        val listeners = sector.listenerManager

        listeners.addListener(eventDispatcher, true)
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