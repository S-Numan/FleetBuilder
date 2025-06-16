package fleetBuilder

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import lunalib.lunaSettings.LunaSettings.addSettingsListener
import fleetBuilder.ModSettings.backupSave
import fleetBuilder.misc.MISC
import fleetBuilder.ui.CampaignAutofitAdder


class FleetBuilderPlugin : BaseModPlugin() {
    val reporter = Reporter()
    override fun onApplicationLoad() {
        super.onApplicationLoad()

        if (Global.getSettings().modManager.isModEnabled("lunalib"))
            addSettingsListener(ModSettingsListener())

        Reporter.onApplicationLoad()
    }
    override fun onGameLoad(newGame: Boolean) {
        super.onGameLoad(newGame)

        val sector = Global.getSector() ?: return

        val listeners = sector.listenerManager

        val campaignAutofitAdder = CampaignAutofitAdder()
        listeners.addListener(campaignAutofitAdder, true)

        val hotkeyHandler = CampaignClipboardHotkeyHandler()
        listeners.addListener(hotkeyHandler, true)

        //sector.addTransientScript(CampaignCodexButton())

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

        if(backupSave) {
            val json = MISC.createPlayerSaveJson()
            Global.getSettings().writeJSONToCommon("SaveTransfer/lastSave", json, false)
        }
    }

}