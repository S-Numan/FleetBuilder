package fleetBuilder.integration

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import fleetBuilder.util.Reporter

class FleetBuilderPlugin : BaseModPlugin() {
    val reporter = Reporter()
    override fun onApplicationLoad() {
        super.onApplicationLoad()

        reporter.onApplicationLoad()
    }

    override fun onGameLoad(newGame: Boolean) {
        super.onGameLoad(newGame)

        val sector = Global.getSector() ?: return

        val listeners = sector.listenerManager

        listeners.addListener(reporter, true)
        sector.addTransientScript(reporter)

        reporter.onGameLoad(newGame)
    }

    override fun beforeGameSave() {
        super.beforeGameSave()

        reporter.beforeGameSave()
    }

    override fun afterGameSave() {
        super.afterGameSave()

        reporter.afterGameSave()
    }

    override fun onDevModeF8Reload() {
        super.onDevModeF8Reload()

        reporter.onApplicationLoad()
    }
}