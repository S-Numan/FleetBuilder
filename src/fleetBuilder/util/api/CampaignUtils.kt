package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.util.FBTxt
import fleetBuilder.otherMods.starficz.ReflectionUtils.get
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.util.api.CampaignUtils.closeCampaignDummyDialog
import fleetBuilder.util.api.CampaignUtils.getMarkets
import fleetBuilder.util.api.CampaignUtils.getSectorEntities
import fleetBuilder.util.api.CampaignUtils.getSubmarkets
import fleetBuilder.util.api.CampaignUtils.openCampaignDummyDialog
import fleetBuilder.util.api.kotlin.safeInvoke

object CampaignUtils {

    /**
     * Returns the actual CoreUITabId of the campaign UI.
     *
     * This function is necessary because the campaign UI can report that the player is still in a CoreUITab even if they are not.
     * This can happen when the player enters an interaction dialog, opens any CoreUITab such as the crew/cargo tab, then escapes that CoreUITab back to the interaction dialog. It will still report that they are in the crew/cargo tab when they are not.
     *
     * This function checks if the player is in a ghost interaction dialog and if so, returns null, indicating that the player is not in a CoreUITab]
     */
    @JvmStatic
    fun getActualCurrentTab(ui: CampaignUIAPI): CoreUITabId? {
        val sector = Global.getSector() ?: return null
        if (!sector.isPaused) return null
        if (ui.currentInteractionDialog != null && ui.currentInteractionDialog.interactionTarget != null) {
            // Validate that we're not stuck in a ghost interaction dialog. (Happens when you escape out of a CoreUITab while in an interaction dialog. It reports that the player is still in that CoreUITab, which is false)
            if (ui.currentInteractionDialog.optionPanel != null && ui.currentInteractionDialog.optionPanel.savedOptionList.isNotEmpty()) return null
        }

        return ui.currentCoreTab
    }

    // Taken from Logistics Notifications by SafariJohn
    /**
     * Calculates how many days of supply the player has left, accounting for repairs and recovery.
     * @return days of supply
     */
    @JvmStatic
    fun getPlayerSupplyDays(): Float {
        // Calculate days of supply remaining
        val playerFleet = Global.getSector()?.playerFleet ?: return 0f
        val supplies = playerFleet.cargo?.supplies ?: return 0f
        val logistics = playerFleet.logistics ?: return 0f
        val recoveryCost = logistics.totalRepairAndRecoverySupplyCost
        val totalPerDay = logistics.totalSuppliesPerDay
        val suDays: Float
        // Running out
        if (recoveryCost >= supplies) {
            suDays = supplies / totalPerDay
        } else {

            // Total up maintenance costs per day for fleet
            var maintPerDay = 0f
            for (mem: FleetMemberAPI in playerFleet.membersWithFightersCopy ?: emptyList()) {
                val maint = (mem.stats?.suppliesPerMonth?.modifiedValue ?: 0f) / 30
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
    @JvmStatic
    fun getPlayerFuelLY(): Float {
        // Calculate lightyears of fuel remaining
        val playerFleet = Global.getSector()?.playerFleet ?: return 0f
        val fuelPerDay = playerFleet.logistics.baseFuelCostPerLightYear
        val ly = if (playerFleet.isInHyperspace)
            playerFleet.cargo.fuel / fuelPerDay
        else
            (playerFleet.cargo.fuel - fuelPerDay) / fuelPerDay
        // multiple by overburn? Actual speed is a setting!
        return ly.coerceAtLeast(0f)
    }

    /**
     * Returns all entities across all locations in the sector.
     */
    @JvmStatic
    fun getSectorEntities(): List<SectorEntityToken> {
        val sector = Global.getSector() ?: return emptyList()

        return sector.allLocations
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
        val sector = Global.getSector() ?: return false
        val ui = sector.campaignUI ?: return false

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
                ui.showInteractionDialog(PlaceholderDialog(), sector.playerFleet) // While this also works, it hides the campaign UI.
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
        val sector = Global.getSector() ?: return

        if (points <= 0)
            return

        sector.playerStats.spendStoryPoints(
            points,
            true,
            null,
            true,
            (experiencePointsGained / sector.playerStats.bonusXPForSpendingStoryPointBeforeSpendingIt.toFloat()) / points,
            FBTxt.txtPlural("used_story_points", points)
        )
        Global.getSoundPlayer().playUISound("ui_char_spent_story_point_technology", 1f, 1f);
    }
}