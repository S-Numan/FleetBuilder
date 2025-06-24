package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.json.JSONArray
import starficz.ReflectionUtils.getMethodsMatching
import starficz.ReflectionUtils.invoke

fun JSONArray.containsString(value: String): Boolean {
    for (i in 0 until this.length()) {
        if (this.optString(i) == value) return true
    }
    return false
}

// Extension to get the effective hull ID
fun ShipHullSpecAPI.getEffectiveHullId(): String {
    return if (isCompatibleWithBase){//If the ship's variants are mostly compatible with the variant's of the base hull.
        if(!dParentHullId.isNullOrEmpty() && dParentHullId != hullId)//If is DHull
            dParentHullId//Get D Parent Hull
        else
            baseHullId//Otherwise, get de base hull
    } else
        hullId
}

fun ShipHullSpecAPI.getCompatibleDLessHullId(): String {
    return if(isCompatibleWithBase && !dParentHullId.isNullOrEmpty())
        dParentHullId
    else hullId
}

fun CampaignUIAPI.getActualCurrentTab(): CoreUITabId? {
    if(!Global.getSector().isPaused) return null
    if(currentInteractionDialog != null && currentInteractionDialog.interactionTarget != null) {
        //Validate that we're not stuck in a ghost interaction dialog. (Happens when you escape out of a UI screen while in an interaction dialog. It reports that the player is still in that ui screen, which is false)
        if (currentInteractionDialog.optionPanel != null && currentInteractionDialog.optionPanel.savedOptionList.isNotEmpty()) return null
    }

    return currentCoreTab
}

val String.toBinary: Int
    get() = if (this.equals("TRUE", ignoreCase = true)) 1 else 0

@Suppress("UNCHECKED_CAST")
internal fun UIPanelAPI.getChildrenCopy(): List<UIComponentAPI> {
    return this.invoke("getChildrenCopy") as List<UIComponentAPI>
}

internal fun UIPanelAPI.findChildWithMethod(methodName: String): UIComponentAPI? {
    return getChildrenCopy().find { it.getMethodsMatching(name = methodName).isNotEmpty() }
}
/*
fun PersonAPI.isGenericOfficer(): Boolean {
    var hasSkill = false
    for (skill in stats.skillsCopy) {
        if(skill.level != 0f) {
            hasSkill = true
            break
        }
    }
    return !hasSkill
}*/