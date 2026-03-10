package fleetBuilder.core

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global

class FleetBuilderPlugin : BaseModPlugin() {
    val eventDispatcher = EventDispatcher()
    override fun onApplicationLoad() {
        eventDispatcher.onApplicationLoad()
    }

    override fun onGameLoad(newGame: Boolean) {
        val sector = Global.getSector() ?: return

        sector.addTransientScript(eventDispatcher)

        eventDispatcher.onGameLoad(newGame)
    }

    override fun beforeGameSave() {
        eventDispatcher.beforeGameSave()
    }

    override fun afterGameSave() {
        eventDispatcher.afterGameSave()
    }

    override fun onGameSaveFailed() {
        eventDispatcher.onGameSaveFailed()
    }

    override fun onDevModeF8Reload() {
        eventDispatcher.onDevModeF8Reload()
    }

    override fun onNewGame() {

    }

    override fun onEnabled(wasEnabledBefore: Boolean) {

    }
}