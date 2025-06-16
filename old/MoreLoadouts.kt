package SN.TestMod.MoreLoadouts

import SN.TestMod.MoreLoadouts.VariantStuff.ApplyVariant
import SN.TestMod.MoreLoadouts.VariantStuff.delegate
import SN.TestMod.MoreLoadouts.VariantStuff.getVariantFromJson
import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.campaign.listeners.RefitScreenListener
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.loading.specs.HullVariantSpec
import com.fs.state.AppDriver
import org.json.JSONObject
import org.magiclib.ReflectionUtils
import org.magiclib.internalextensions.findChildWithMethod


class MoreLoadouts : EveryFrameScript, CampaignInputListener {
    override fun isDone(): Boolean = false

    override fun runWhilePaused(): Boolean = true

    var inRefitScreen: Boolean = false

    //When false: Autofit like normal. Access cargo and market to fit the ship.
    //When true: Delete all contents of the ship, weapons/wings/hullmods/tags. Then, apply the specified loadout to the ship, Do not access cargo or pay any price.
    val forceRefit = false

    override fun advance(amount: Float) {
        inRefitScreen = false

        val sector = Global.getSector() ?: return
        val ui = sector.campaignUI ?: return

        if (!sector.isPaused) return
        //Game is paused

        if (ui.currentCoreTab != CoreUITabId.REFIT) return

        //Validate that we're not stuck in a ghost interaction dialog. (Happens when you escape out of a ui screen while in an interaction dialog. It reports that the player is still in that ui screen, which is false)
        val interaction = ui.currentInteractionDialog
        if (interaction != null && interaction.interactionTarget != null) {
            if (interaction.optionPanel != null && interaction.optionPanel.savedOptionList.isNotEmpty()) return
        }
        //Player is viewing refit screen

        //Only proceed if in a valid campaign state
        val state = AppDriver.getInstance().currentState
        if (state !is CampaignState) return

        inRefitScreen = true

        val newCoreUI = (ReflectionUtils.invoke("getEncounterDialog", state)?.let { dialog ->
            ReflectionUtils.invoke("getCoreUI", dialog) as? UIPanelAPI
        } ?: ReflectionUtils.invoke("getCore", state) as? UIPanelAPI) ?: return
        val borderContainer = newCoreUI.findChildWithMethod("setBorderInsetLeft") as? UIPanelAPI ?: return
        val refitTab = borderContainer.findChildWithMethod("goBackToParentIfNeeded") as? UIPanelAPI ?: return
        val refitPanel = refitTab.findChildWithMethod("syncWithCurrentVariant") as? UIPanelAPI ?: return
        val shipDisplay = ReflectionUtils.invoke("getShipDisplay", refitPanel) as UIPanelAPI

        var baseVariant = ReflectionUtils.invoke("getCurrentVariant", shipDisplay) as HullVariantSpec
        val fleetMember = ReflectionUtils.invoke("getMember", refitPanel) as? FleetMemberAPI
        val hullSpec = fleetMember?.hullSpec ?: return

        //If there is no delegate, make one
        /*if(delegate == null) {
            delegate = MLAutofitPluginDelegate(fleetMember, Global.getSector().playerFaction, Global.getSector().playerFleet.cargo)

            if (interaction != null && interaction.interactionTarget != null && interaction.interactionTarget.market != null) {
                val market = interaction.interactionTarget.market
                delegate!!.setMarket(market)
            }
        }*/

        //If both ctrl/alt are held, and a number key is pressed
        if ((keysHeld[29] //KEY_LCONTROL
                    || keysHeld[56]) //KEY_LMENU (left alt)
            && keysDown[2] || keysDown[3] || keysDown[4]// KEY_1, KEY_2, KEY_3
        ) {
            val filename = "loadouts/ships/" +
                    hullSpec.hullId + "/" +
                    hullSpec.hullId + "_" + baseVariant.displayName +
                    ".variant"

            if(keysDown[2]) { //KEY_1
                //Save
                val json: JSONObject = baseVariant.toJSONObject()
                json.remove("variantId")
                json.put("variantId", hullSpec.hullId + baseVariant.displayName)
                json.remove("goalVariant")
                json.put("goalVariant", false)
                Global.getSettings().writeJSONToCommon(filename, json, false)//TODO, remove .data after the filename. Not sure why it gets put there

            } else if (keysDown[3] //KEY_2
                && !fleetMember.hullSpec.hasTag("no_autofit")
            ) {
                //Load

                if (Global.getSettings().fileExistsInCommon(filename)) {
                    val json = Global.getSettings().readJSONFromCommon(filename, false)

                    val loadout = getVariantFromJson(json)
                    if(loadout == null) throw Exception()

                    ApplyVariant(baseVariant, loadout, fleetMember)

                    //Sync the variant with what the player is currently viewing
                    ReflectionUtils.invoke("syncWithCurrentVariant", refitPanel)
                    ReflectionUtils.invoke("updateModules", shipDisplay)
                    ReflectionUtils.invoke("updateButtonPositionsToZoomLevel", shipDisplay)
                } else {
                    Global.getLogger(this.javaClass).error("Ship variant file does not exist")
                }
            }
        }
    }

    override fun getListenerInputPriority(): Int {
        return 1
    }

    val keysDown = BooleanArray(256) { false }
    val keysHeld = BooleanArray(256) { false }

    override fun processCampaignInputPreCore(events: List<InputEventAPI>) {

        keysDown.fill(false)
        //Key IDs are integers, and can be found here:
        //https://gist.github.com/Mumfrey/5cfc3b7e14fef91b6fa56470dc05218a
        for (event in events) {
            if (event.isConsumed || event.eventValue > 255) {
                continue
            }

            if (event.eventType == InputEventType.KEY_DOWN) {
                keysDown[event.eventValue] = true
                keysHeld[event.eventValue] = true
            }
            if (event.eventType == InputEventType.KEY_UP) {
                keysHeld[event.eventValue] = false
            }
        }

        if (Global.getSector() == null || Global.getSector().campaignUI == null) return
        if (!Global.getSector().isPaused) return //Return if not paused
        val ui = Global.getSector().campaignUI

        //Campaign UI exists
        if (ui.currentCoreTab != CoreUITabId.REFIT) return//Return if not refit

        val state = AppDriver.getInstance().currentState
        if (state !is CampaignState) return

        //Player is viewing refit screen

        //if the player ESCs out of a refit/storage/etc screen while interacting with a market, it continues to say we are in that screen. This checks if interaction options don't exist. If no interaction options exist, but the player in interacting with something, the player is probably in a UI Tab
        val interaction = ui.currentInteractionDialog
        if (interaction != null && interaction.interactionTarget != null) {
            if (interaction.optionPanel != null && interaction.optionPanel.savedOptionList.isNotEmpty()) return
        }

        //If both ctrl/alt are held, and a number key is pressed
        if ((keysHeld[29] //KEY_LCONTROL
            || keysHeld[56]) //KEY_LMENU (left alt)
            && keysDown[2] || keysDown[3] || keysDown[4]// KEY_1, KEY_2, KEY_3
            ) {
            for (event in events) {
                if (event.isConsumed || event.eventValue >= 256) {
                    continue
                }

                if (event.eventValue == 29 || event.eventValue == 56 || event.eventValue == 2 || event.eventValue == 3 || event.eventValue == 4) {
                    event.consume()
                }
            }
        }

    }
    override fun processCampaignInputPreFleetControl(events: List<InputEventAPI?>?) {

    }
    override fun processCampaignInputPostCore(events: List<InputEventAPI?>?) {

    }


}