package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId


/**
 * Returns the actual current tab of the campaign UI.
 *
 * This function is necessary because the campaign UI sometimes reports that the player is still in a UI screen even if they are not.
 * This can happen when the player escapes out of a UI screen while in an interaction dialog.
 *
 * This function checks if the player is in a ghost interaction dialog and if so, returns null, indicating that the player is not in a UI screen.
 */
fun CampaignUIAPI.getActualCurrentTab(): CoreUITabId? {
    if (!Global.getSector().isPaused) return null
    if (currentInteractionDialog != null && currentInteractionDialog.interactionTarget != null) {
        // Validate that we're not stuck in a ghost interaction dialog. (Happens when you escape out of a UI screen while in an interaction dialog. It reports that the player is still in that ui screen, which is false)
        if (currentInteractionDialog.optionPanel != null && currentInteractionDialog.optionPanel.savedOptionList.isNotEmpty()) return null
    }

    return currentCoreTab
}


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