package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.FBTxt
import fleetBuilder.otherMods.starficz.ReflectionUtils.get
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.util.api.CampaignUtils.closeCampaignDummyDialog
import fleetBuilder.util.api.CampaignUtils.getMarkets
import fleetBuilder.util.api.CampaignUtils.getSectorEntities
import fleetBuilder.util.api.CampaignUtils.getSubmarkets
import fleetBuilder.util.api.CampaignUtils.openCampaignDummyDialog
import fleetBuilder.util.api.kotlin.safeInvoke

object CampaignUtils {

    // Taken from Logistics Notifications by SafariJohn
    /**
     * Calculates how many days of supply the player has left, accounting for repairs and recovery.
     * @return days of supply
     */
    fun getPlayerSupplyDays(): Float {
        // Calculate days of supply remaining
        val playerFleet = Global.getSector().playerFleet
        val supplies = playerFleet.cargo.supplies
        val logistics = playerFleet.logistics
        val recoveryCost = logistics.totalRepairAndRecoverySupplyCost
        val totalPerDay = logistics.totalSuppliesPerDay
        val suDays: Float
        // Running out
        if (recoveryCost >= supplies) {
            suDays = supplies / totalPerDay
        } else {

            // Total up maintenance costs per day for fleet
            var maintPerDay = 0f
            for (mem: FleetMemberAPI in playerFleet.membersWithFightersCopy) {
                val maint = mem.stats.suppliesPerMonth.modifiedValue / 30
                maintPerDay += maint
            }
            // Account for extra cost from over-capacity
            // Not going to try to do cargo because it's reductive as it consumes supplies
            maintPerDay += logistics.excessPersonnelCapacitySupplyCost
            maintPerDay += logistics.excessFuelCapacitySupplyCost

            // And finally: compute!
            suDays = (recoveryCost / totalPerDay) + ((supplies - recoveryCost.toInt()) / maintPerDay)
        }
        return suDays
    }

    // Taken from Logistics Notifications by SafariJohn
    /**
     * Calculates how far the player's fleet can travel, minus amount needed to jump to hyper if in-system.
     * @return distance in lightyears
     */
    fun getPlayerFuelLY(): Float {
        // Calculate lightyears of fuel remaining
        val playerFleet = Global.getSector().playerFleet
        val fuel = playerFleet.cargo.fuel
        val fuelPerDay = playerFleet.logistics.baseFuelCostPerLightYear
        var ly: Float
        if (playerFleet.isInHyperspace) {
            ly = fuel / fuelPerDay
        } else {
            ly = (fuel - fuelPerDay) / fuelPerDay
        }
        if (ly < 0) ly = 0f
        return ly
    }

    /**
     * Returns all entities across all locations in the sector.
     */
    @JvmStatic
    fun getSectorEntities(): List<SectorEntityToken> {
        return Global.getSector().allLocations
            .flatMap { it.allEntities }
    }

    /**
     * Returns all unique markets in the sector.
     *
     * Markets are collected from all [getSectorEntities] and deduplicated by market ID.
     */
    @JvmStatic
    fun getSectorMarkets(): List<MarketAPI> {
        return getMarkets(getSectorEntities())
    }

    /**
     * Returns markets from a precomputed entity list.
     */
    @JvmStatic
    fun getMarkets(entities: List<SectorEntityToken>): List<MarketAPI> {
        return entities
            .mapNotNull { it.market }
            .distinctBy { it.id }
    }

    /**
     * Returns all unique submarkets in the sector.
     *
     * Submarkets are collected from all [getMarkets] and deduplicated by reference.
     */
    @JvmStatic
    fun getSectorSubmarkets(): List<SubmarketAPI> {
        return getSubmarkets(getSectorMarkets())
    }

    /**
     * Returns submarkets from a precomputed market list.
     */
    @JvmStatic
    fun getSubmarkets(markets: List<MarketAPI>): List<SubmarketAPI> {
        return markets
            .flatMap { it.submarketsCopy }
            .distinctBy { it }
    }

    /**
     * Returns all unique cargo instances from sector submarkets.
     *
     * Cargo is collected from all [getSubmarkets] and deduplicated by reference.
     */
    @JvmStatic
    fun getCargoFromSectorSubmarkets(): List<CargoAPI> {
        return getCargoFromSubmarkets(getSectorSubmarkets())
    }

    /**
     * Returns cargo from a precomputed submarket list.
     */
    @JvmStatic
    fun getCargoFromSubmarkets(submarkets: List<SubmarketAPI>): List<CargoAPI> {
        return submarkets
            .mapNotNull { it.cargo }
            .distinctBy { it }
    }


    private var placeholderDialog: UIPanelAPI? = null

    /**
     * Opens an empty dialog, which does nothing by itself and cannot be seen.
     *
     * This causes Global.getSector().getCampaignUI().isShowingDialog() to return true, as a dialog is indeed open.
     *
     * Remember to close it via [closeCampaignDummyDialog] when done.
     *
     * @param isInteractionDialog If true, the campaign UI will be hidden. Otherwise, no visual changes.
     * @return Returns true if successful. Returns false if it did not open a dummy dialog, usually due to a dialog already being open or not being in the campaign.
     * @see closeCampaignDummyDialog
     */
    @JvmOverloads
    @JvmStatic
    fun openCampaignDummyDialog(
        isInteractionDialog: Boolean = false,
        onBackFromEngagement: () -> Unit = {}
    ): Boolean {
        val ui = Global.getSector().campaignUI ?: return false

        if (!ui.isShowingDialog && placeholderDialog != null)
            closeCampaignDummyDialog()

        if (placeholderDialog == null && Global.getSettings().isInCampaignState && !ui.isShowingDialog) {
            if (isInteractionDialog) {
                class PlaceholderDialog : InteractionDialogPlugin {
                    override fun init(dialog: InteractionDialogAPI?) {}
                    override fun optionSelected(optionText: String?, optionData: Any?) {}
                    override fun optionMousedOver(optionText: String?, optionData: Any?) {}
                    override fun advance(amount: Float) {}
                    override fun backFromEngagement(battleResult: EngagementResultAPI?) {
                        onBackFromEngagement.invoke()
                    }

                    override fun getContext(): Any? = null
                    override fun getMemoryMap(): MutableMap<String, MemoryAPI> = hashMapOf()
                }
                ui.showInteractionDialog(PlaceholderDialog(), Global.getSector().playerFleet) // While this also works, it hides the campaign UI.
                placeholderDialog = ui.currentInteractionDialog as? UIPanelAPI
            } else {
                ui.showMessageDialog(" ")
                val screenPanel = ui.get("screenPanel") as? UIPanelAPI
                placeholderDialog = screenPanel?.findChildWithMethod("getOptionMap") as? UIPanelAPI
            }
            if (placeholderDialog != null) {
                placeholderDialog!!.safeInvoke("setOpacity", 0f)
                placeholderDialog!!.safeInvoke("setBackgroundDimAmount", 0f)
                placeholderDialog!!.safeInvoke("setAbsorbOutsideEvents", false)
                placeholderDialog!!.safeInvoke("makeOptionInstant", 0)
                return true
            }
        }
        return false
    }

    /**
     * Closes the dialog opened by [openCampaignDummyDialog].
     * @see openCampaignDummyDialog
     */
    @JvmStatic
    fun closeCampaignDummyDialog(): Boolean {
        if (placeholderDialog == null) return false
        placeholderDialog?.safeInvoke("dismiss", 0)
        placeholderDialog = null
        return true
    }

    @JvmStatic
    fun isCampaignDummyDialogOpen(): Boolean {
        return placeholderDialog != null
    }

    /**
     * Spends a given amount of story points and gains experience points.
     *
     * @param points the amount of story points to spend
     * @param experiencePointsGained the amount of experience points to gain per story point (before any bonus)
     */
    @JvmStatic
    fun spendStoryPoint(points: Int, experiencePointsGained: Float) {
        if (points <= 0)
            return

        Global.getSector().playerStats.spendStoryPoints(
            points,
            true,
            null,
            true,
            (experiencePointsGained / Global.getSector().playerStats.bonusXPForSpendingStoryPointBeforeSpendingIt.toFloat()) / points,
            FBTxt.txtPlural("used_story_points", points)
        )
        Global.getSoundPlayer().playUISound("ui_char_spent_story_point_technology", 1f, 1f);
    }
}