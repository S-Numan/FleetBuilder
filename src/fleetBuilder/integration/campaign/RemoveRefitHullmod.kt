package fleetBuilder.integration.campaign

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.config.FBTxt
import fleetBuilder.config.ModSettings
import fleetBuilder.ui.autofit.AutofitPanel
import fleetBuilder.util.DisplayMessage
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.completelyRemoveMod
import fleetBuilder.util.getActualCurrentTab
import fleetBuilder.variants.VariantLib
import org.lwjgl.util.vector.Vector2f
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.invoke
import starficz.findChildWithMethod
import starficz.getChildrenCopy
import starficz.height
import starficz.width
import kotlin.collections.forEach

class RemoveRefitHullmod : CampaignInputListener {

    override fun getListenerInputPriority(): Int = 1

    override fun processCampaignInputPreCore(events: List<InputEventAPI>) {
        if (!ModSettings.cheatsEnabled()) return

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
        val coreUI = ReflectionMisc.getCoreUI() ?: return
        val isAutofitPanelOpen = coreUI
            .getChildrenCopy()
            .filterIsInstance<CustomPanelAPI>()
            .any { it.plugin is AutofitPanel.AutofitPanelPlugin }
        if (isAutofitPanelOpen) return

        try {
            val refitPanel = ReflectionMisc.getRefitPanel() ?: return
            val modWidget = ReflectionMisc.getRefitPanelModWidget(refitPanel) ?: return

            val modWidgetModIcons = modWidget.findChildWithMethod("getColumns")

            @Suppress("UNCHECKED_CAST")
            val items = modWidgetModIcons?.invoke("getItems") as? MutableList<UIPanelAPI?> ?: return

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

                    val variant = ReflectionMisc.getCurrentVariantInRefitTab()
                    if (variant != null) {
                        if (variant.hullSpec.builtInMods.contains(hullModID.id)) {//Built in SMod?
                            if (variant.sModdedBuiltIns.contains(hullModID.id)) {
                                variant.completelyRemoveMod(hullModID.id)
                                refitPanel.invoke("syncWithCurrentVariant")

                                DisplayMessage.showMessage(FBTxt.txt("removed_smoddedbuiltin", hullModID.displayName))
                            } else if (VariantLib.getAllDMods().contains(hullModID.id)) {//Built in DMod?
                                variant.completelyRemoveMod(hullModID.id)
                                refitPanel.invoke("syncWithCurrentVariant")

                                DisplayMessage.showMessage(FBTxt.txt("removed_built_in_dmod", hullModID.displayName))
                            } else {
                                DisplayMessage.showMessage(FBTxt.txt("cannot_remove_built_in_hullmod", hullModID.displayName))
                            }
                        } else {
                            variant.completelyRemoveMod(hullModID.id)
                            refitPanel.invoke("syncWithCurrentVariant")

                            DisplayMessage.showMessage(FBTxt.txt("removed_hullmod", hullModID.displayName))
                        }

                        event.consume()
                        return
                    }
                }
            }

        } catch (e: Exception) {
            DisplayMessage.showError(FBTxt.txt("mod_hotkey_failed", ModSettings.modName), e)
        }
    }

    override fun processCampaignInputPreFleetControl(events: List<InputEventAPI?>?) {

    }

    override fun processCampaignInputPostCore(events: List<InputEventAPI?>?) {

    }
}