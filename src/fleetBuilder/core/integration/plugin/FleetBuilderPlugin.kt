package fleetBuilder.core.integration.plugin

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import fleetBuilder.core.integration.listener.EventDispatcher

class FleetBuilderPlugin : BaseModPlugin() {

    companion object {
        private var modSpec = Global.getSettings().modManager.enabledModsCopy.find { it.modPluginClassName == javaClass.enclosingClass.name }!!

        fun getModSpec(): ModSpecAPI = modSpec
        fun getModName(): String = modSpec.name.trim()
        fun getModID(): String = modSpec.id
    }

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