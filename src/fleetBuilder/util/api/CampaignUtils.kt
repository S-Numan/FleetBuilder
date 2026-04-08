package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.FBTxt
import fleetBuilder.otherMods.starficz.ReflectionUtils.get
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.util.api.CampaignUtils.closeCampaignDummyDialog
import fleetBuilder.util.api.CampaignUtils.openCampaignDummyDialog
import fleetBuilder.util.api.kotlin.safeInvoke

object CampaignUtils {

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
    fun closeCampaignDummyDialog(): Boolean {
        if (placeholderDialog == null) return false
        placeholderDialog?.safeInvoke("dismiss", 0)
        placeholderDialog = null
        return true
    }

    fun isCampaignDummyDialogOpen(): Boolean {
        return placeholderDialog != null
    }

    /**
     * Spends a given amount of story points and gains experience points.
     *
     * @param points the amount of story points to spend
     * @param experiencePointsGained the amount of experience points to gain per story point (before any bonus)
     */
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