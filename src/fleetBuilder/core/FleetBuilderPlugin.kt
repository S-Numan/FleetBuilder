package fleetBuilder.core

import com.fs.starfarer.api.BaseModPlugin

class FleetBuilderPlugin : BaseModPlugin() {

    override fun onApplicationLoad() {
        EventDispatcher.onApplicationLoad()
    }

    override fun onGameLoad(newGame: Boolean) {
        EventDispatcher.onGameLoad(newGame)
    }

    override fun beforeGameSave() {
        EventDispatcher.beforeGameSave()
    }

    override fun afterGameSave() {
        EventDispatcher.afterGameSave()
    }

    override fun onGameSaveFailed() {
        EventDispatcher.onGameSaveFailed()
    }

    override fun onDevModeF8Reload() {
        EventDispatcher.onDevModeF8Reload()
    }

    override fun onNewGame() {

    }

    override fun onEnabled(wasEnabledBefore: Boolean) {

    }
}