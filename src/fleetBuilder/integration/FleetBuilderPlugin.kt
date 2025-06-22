package fleetBuilder.integration

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import fleetBuilder.config.ModSettings
import fleetBuilder.config.ModSettingsListener
import fleetBuilder.integration.campaign.CampaignClipboardHotkeyHandler
import fleetBuilder.util.MISC
import fleetBuilder.integration.campaign.CampaignAutofitAdder
import fleetBuilder.temporary.CampaignCodexButton
import fleetBuilder.util.Reporter
import lunalib.lunaSettings.LunaSettings

class FleetBuilderPlugin : BaseModPlugin() {
    val reporter = Reporter()
    override fun onApplicationLoad() {
        super.onApplicationLoad()

        if (Global.getSettings().modManager.isModEnabled("lunalib"))
            LunaSettings.addSettingsListener(ModSettingsListener())

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

        sector.addTransientScript(CampaignCodexButton())

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

}