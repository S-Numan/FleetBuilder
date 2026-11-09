package fleetkit.features.removeRefitHullMod

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetkit.core.config.FTSettings
import fleetkit.core.integration.plugin.ModPlugin
import fleetkit.core.util.DisplayMessage
import fleetkit.core.util.FTTxt
import fleetkit.features.autofit.ui.AutofitPanel
import fleetkit.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetkit.otherMods.starficz.findChildWithMethod
import fleetkit.otherMods.starficz.getChildrenCopy
import fleetkit.otherMods.starficz.height
import fleetkit.otherMods.starficz.width
import fleetkit.otherMods.starficz.ReflectionUtilsSafe.safeInvoke
import fleetkit.util.reflection.InternalReflectionMisc
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicLookup
import org.magiclib.util.api.getActualCurrentTab
import org.magiclib.util.api.removeModFull
import org.magiclib.util.reflection.UIFinder
import org.magiclib.util.reflection.boxed.BoxedRefitTab

internal class RemoveRefitHullmod : CampaignInputListener {

    override fun getListenerInputPriority(): Int = 1

    override fun processCampaignInputPreCore(events: List<InputEventAPI>) {
        if (!FTSettings.cheatsEnabled()) return

        val sector = Global.getSector() ?: return
        val ui = sector.campaignUI ?: return

        events.forEach { event ->
            if (event.isConsumed) return@forEach

            if (event.eventType == InputEventType.MOUSE_DOWN && event.isRMBDownEvent && ui.getActualCurrentTab() == CoreUITabId.REFIT) {
                handleRefitRemoveHullMod(event)
            }
        }
    }

    private fun handleRefitRemoveHullMod(event: InputEventAPI) {
        val screenPanel = UIFinder.getScreenPanel() ?: return
        val isAutofitPanelOpen = screenPanel
            .getChildrenCopy()
            .filterIsInstance<CustomPanelAPI>()
            .any { it.plugin is AutofitPanel.AutofitPanelPlugin }
        if (isAutofitPanelOpen) return

        try {
            val refitTab = BoxedRefitTab.get() ?: return
            val modWidget = InternalReflectionMisc.getRefitPanelModWidget(refitTab.refitPanel) ?: return

            val modWidgetModIcons = modWidget.findChildWithMethod("getColumns")

            @Suppress("UNCHECKED_CAST")
            val items = modWidgetModIcons?.safeInvoke("getItems") as? MutableList<UIPanelAPI?> ?: return

            items.forEach { item ->
                if (item == null) return@forEach
                val modIcon = item.findChildWithMethod("getFader") as? ButtonAPI ?: return@forEach

                val mouseX = Global.getSettings().mouseX
                val mouseY = Global.getSettings().mouseY
                val modIconVec = Vector2f(modIcon.position.x, modIcon.position.y)
                if (mouseX >= modIconVec.x && mouseX <= modIconVec.x + modIcon.width &&
                    mouseY >= modIconVec.y && mouseY <= modIconVec.y + modIcon.height
                ) {
                    val hullModField = item.getFieldsMatching(fieldAssignableTo = HullModSpecAPI::class.java).firstOrNull()
                        ?: return@forEach
                    val hullModID = hullModField.get(item) as? HullModSpecAPI ?: return@forEach

                    val variant = refitTab.getCurrentVariant()
                    if (variant != null) {
                        if (variant.hullSpec.builtInMods.contains(hullModID.id)) {//Built in SMod?
                            if (variant.sModdedBuiltIns.contains(hullModID.id)) {
                                variant.removeModFull(hullModID.id)
                                refitTab.syncWithCurrentVariant()

                                DisplayMessage.showMessage(FTTxt.txt("removed_smoddedbuiltin", hullModID.displayName))
                            } else if (MagicLookup.getAllDModIds().contains(hullModID.id)) {//Built in DMod?
                                variant.removeModFull(hullModID.id, true)
                                refitTab.syncWithCurrentVariant()

                                DisplayMessage.showMessage(FTTxt.txt("removed_built_in_dmod", hullModID.displayName))
                            } else {
                                DisplayMessage.showMessage(FTTxt.txt("cannot_remove_built_in_hullmod", hullModID.displayName))
                            }
                        } else {
                            variant.removeModFull(hullModID.id)
                            refitTab.syncWithCurrentVariant()

                            DisplayMessage.showMessage(FTTxt.txt("removed_hullmod", hullModID.displayName))
                        }

                        event.consume()
                        return
                    }
                }
            }

        } catch (e: Exception) {
            DisplayMessage.showError(FTTxt.txt("mod_hotkey_failed", ModPlugin.getModName()), e)
        }
    }

    override fun processCampaignInputPreFleetControl(events: List<InputEventAPI?>?) {

    }

    override fun processCampaignInputPostCore(events: List<InputEventAPI?>?) {

    }
}