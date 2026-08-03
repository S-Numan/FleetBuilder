package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.util.FBTxt
import fleetBuilder.otherMods.starficz.ReflectionUtils.get
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.util.api.CampaignUtils.closeCampaignDummyDialog
import fleetBuilder.util.api.CampaignUtils.openCampaignDummyDialog
import fleetBuilder.util.api.kotlin.safeInvoke

object CampaignUtils {

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