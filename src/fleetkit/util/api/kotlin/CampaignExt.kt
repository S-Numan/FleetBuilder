package fleetkit.util.api.kotlin

import com.fs.starfarer.api.campaign.CampaignUIAPI


/**
 * Returns true if the campaign UI is idle.
 *
 * A campaign UI is idle if it is not showing a dialog, menu, or interaction dialog.
 *
 * @return Returns true if the campaign UI is idle. False otherwise.
 */
fun CampaignUIAPI.isIdle(): Boolean {
    return currentInteractionDialog == null &&
            !isShowingDialog &&
            !isShowingMenu
}