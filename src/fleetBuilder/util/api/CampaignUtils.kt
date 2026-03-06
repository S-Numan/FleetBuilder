package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.util.FBTxt
import fleetBuilder.util.safeInvoke
import starficz.ReflectionUtils.get
import starficz.findChildWithMethod

object CampaignUtils {
    var placeholderDialog: UIPanelAPI? = null
    fun openCampaignDummyDialog() {
        val ui = Global.getSector().campaignUI
        //Open a dialog to prevent input from most other mods
        if (ui != null && placeholderDialog == null && Global.getSettings().isInCampaignState && !ui.isShowingDialog) {
            //ui.showInteractionDialog(PlaceholderDialog(), Global.getSector().playerFleet) // While this also works, it hides the campaign UI.
            //placeholderDialog = ui.currentInteractionDialog

            ui.showMessageDialog(" ")
            val screenPanel = ui.get("screenPanel") as? UIPanelAPI
            placeholderDialog = screenPanel?.findChildWithMethod("getOptionMap") as? UIPanelAPI
            if (placeholderDialog != null) {
                placeholderDialog!!.safeInvoke("setOpacity", 0f)
                placeholderDialog!!.safeInvoke("setBackgroundDimAmount", 0f)
                placeholderDialog!!.safeInvoke("setAbsorbOutsideEvents", false)
                placeholderDialog!!.safeInvoke("makeOptionInstant", 0)
            }
        }
    }

    fun closeCampaignDummyDialog() {
        placeholderDialog?.safeInvoke("dismiss", 0)
        placeholderDialog = null
    }

    fun spendStoryPoint(points: Int, buildInBonus: Float) {
        val text =
            if (points > 1)
                FBTxt.txt("used_story_point_plural", points)
            else
                FBTxt.txt("used_story_point", points)

        Global.getSector().playerStats.spendStoryPoints(
            points,
            true,
            null,
            true,
            (buildInBonus / Global.getSector().playerStats.bonusXPForSpendingStoryPointBeforeSpendingIt.toFloat()) / points,
            text
        );
        Global.getSoundPlayer().playUISound("ui_char_spent_story_point_technology", 1f, 1f);
    }
}