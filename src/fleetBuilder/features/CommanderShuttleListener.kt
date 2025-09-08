package fleetBuilder.features

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.characters.AbilityPlugin
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin
import com.fs.starfarer.api.impl.campaign.JumpPointInteractionDialogPluginImpl
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl
import fleetBuilder.config.FBTxt
import fleetBuilder.config.ModSettings
import fleetBuilder.features.CommanderShuttle.playerShuttleExists
import fleetBuilder.util.DisplayMessage
import java.awt.Color

class CommanderShuttleListener : CampaignEventListener, EveryFrameScript {
    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    var prevLocationSetter: LocationAPI? = null

    fun reportCurrentLocationChanged(prev: LocationAPI, curr: LocationAPI) {
        if (playerShuttleExists() && !prev.isHyperspace && curr.isHyperspace) {
            prevLocationSetter = prev
        }

    }

    var prevInteractionPlugin: InteractionDialogPlugin? = null

    override fun advance(amount: Float) {
        if (!Global.getSettings().isInCampaignState) return

        val interactionDialog = Global.getSector().campaignUI.currentInteractionDialog
        if (interactionDialog?.plugin != prevInteractionPlugin) {
            prevInteractionPlugin = interactionDialog?.plugin
            //Plugin change
            val playerFleet = Global.getSector().playerFleet ?: return

            if (interactionDialog == null || interactionDialog.plugin is RuleBasedInteractionDialogPluginImpl) { // No dialog?
                makeCommanderShuttleGood(playerFleet)
            } else { // Yes dialog?

                makeCommanderShuttleNonCombat(playerFleet)

                if (playerFleet.fleetSizeCount == 1 && playerFleet.fleetData.membersListCopy.first().variant.hasHullMod(
                        ModSettings.commandShuttleId
                    )
                ) {
                    //No getting around jumping with only the command shuttle

                    if (interactionDialog.plugin is JumpPointInteractionDialogPluginImpl
                        || (interactionDialog.interactionTarget != null && interactionDialog.interactionTarget.customPlugin is GateEntityPlugin)
                    ) {
                        interactionDialog.dismiss()
                        DisplayMessage.showMessage(FBTxt.txt("no_command_shuttle_jump_alone"), Color.YELLOW)
                    }
                    //val pods = Misc.addCargoPods(playerFleet.containingLocation, playerFleet.location)
                    //pods.cargo.addFuel(playerFleet.cargo.fuel)
                    //playerFleet.cargo.removeFuel(playerFleet.cargo.fuel)
                    //playerFleet.fleetData.membersListCopy.first().stats.fuelUseMod.modifyFlat("test", 99999f)
                }
            }
        }

        if (interactionDialog != null) return

        val playerFleet = Global.getSector().playerFleet ?: return

        if (prevLocationSetter != null && playerFleet.fleetSizeCount == 1 && playerFleet.fleetData.membersListCopy.first().variant.hasHullMod(ModSettings.commandShuttleId)) {
            val playerFleet = Global.getSector().playerFleet
            playerFleet.containingLocation.removeEntity(playerFleet)
            prevLocationSetter!!.addEntity(playerFleet)

            Global.getSector().currentLocation = prevLocationSetter

            playerFleet.setLocation(0f, 0f)
            DisplayMessage.showMessage(FBTxt.txt("no_command_shuttle_jump_alone") + "...", Color.YELLOW)

            prevLocationSetter = null
        }

        makeCommanderShuttleGood(playerFleet)
    }

    var marketOpened = false

    override fun reportShownInteractionDialog(dialog: InteractionDialogAPI) {
        val playerFleet = Global.getSector().playerFleet ?: return

        if (marketOpened) {
            marketOpened = false
            return
        }

        makeCommanderShuttleNonCombat(playerFleet)
    }

    private fun makeCommanderShuttleNonCombat(playerFleet: CampaignFleetAPI) {
        for (member in playerFleet.fleetData.membersListCopy) {
            if (member.variant.hasHullMod(ModSettings.commandShuttleId)) {
                member.repairTracker.isMothballed = true
            }
        }
    }

    private fun makeCommanderShuttleGood(playerFleet: CampaignFleetAPI) {
        for (member in playerFleet.fleetData.membersListCopy) {
            if (member.repairTracker.isMothballed && member.variant.hasHullMod(ModSettings.commandShuttleId)) {
                member.repairTracker.isMothballed = false
                member.repairTracker.cr = member.repairTracker.maxCR
                member.stats.fuelUseMod.flatBonus = 0f
            }
        }
    }

    override fun reportPlayerOpenedMarket(market: MarketAPI?) {
        marketOpened = true
    }

    override fun reportPlayerClosedMarket(market: MarketAPI?) {

    }

    override fun reportPlayerOpenedMarketAndCargoUpdated(market: MarketAPI?) {
    }

    override fun reportEncounterLootGenerated(plugin: FleetEncounterContextPlugin?, loot: CargoAPI?) {

    }

    override fun reportPlayerMarketTransaction(transaction: PlayerMarketTransaction) {
        if (transaction.shipsSold.isNotEmpty()) {
            val member = transaction.shipsSold.first().member
            if (member.variant.hasHullMod(ModSettings.commandShuttleId)) {
                transaction.submarket.cargo.mothballedShips.removeFleetMember(member)

                if (transaction.creditValue > 0) {
                    val message = FBTxt.txt("command_shuttle_transfer_message", transaction.creditValue.toInt())
                    Global.getSector().playerFleet.cargo.credits.subtract(transaction.creditValue)
                    DisplayMessage.showMessage(message, Color.YELLOW)
                }
            }
        }
    }

    override fun reportBattleOccurred(primaryWinner: CampaignFleetAPI?, battle: BattleAPI?) {

    }

    override fun reportBattleFinished(primaryWinner: CampaignFleetAPI?, battle: BattleAPI?) {

    }

    override fun reportPlayerEngagement(result: EngagementResultAPI?) {

    }

    override fun reportFleetDespawned(
        fleet: CampaignFleetAPI?,
        reason: CampaignEventListener.FleetDespawnReason?,
        param: Any?
    ) {

    }

    override fun reportFleetSpawned(fleet: CampaignFleetAPI?) {

    }

    override fun reportFleetReachedEntity(fleet: CampaignFleetAPI?, entity: SectorEntityToken?) {

    }

    override fun reportFleetJumped(
        fleet: CampaignFleetAPI?,
        from: SectorEntityToken?,
        to: JumpPointAPI.JumpDestination?
    ) {

    }

    override fun reportPlayerReputationChange(faction: String?, delta: Float) {

    }

    override fun reportPlayerReputationChange(person: PersonAPI?, delta: Float) {

    }

    override fun reportPlayerActivatedAbility(ability: AbilityPlugin?, param: Any?) {

    }

    override fun reportPlayerDeactivatedAbility(ability: AbilityPlugin?, param: Any?) {

    }

    override fun reportPlayerDumpedCargo(cargo: CargoAPI?) {

    }

    override fun reportPlayerDidNotTakeCargo(cargo: CargoAPI?) {

    }

    override fun reportEconomyTick(iterIndex: Int) {

    }

    override fun reportEconomyMonthEnd() {

    }
}