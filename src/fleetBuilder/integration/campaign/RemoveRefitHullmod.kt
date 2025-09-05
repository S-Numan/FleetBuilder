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
        if (!Global.getSettings().isDevMode) return

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

                                DisplayMessage.showMessage("Removed sModdedBuiltIn in with ID '${hullModID.id}'")
                            } else if (VariantLib.getAllDMods().contains(hullModID.id)) {//Built in DMod?
                                variant.completelyRemoveMod(hullModID.id)
                                refitPanel.invoke("syncWithCurrentVariant")

                                DisplayMessage.showMessage("Removed built in DMod with ID '${hullModID.id}'")
                            } else {
                                DisplayMessage.showMessage("The hullmod '${hullModID.id}' is built into the hullspec, it cannot be removed from the variant")
                            }
                        } else {
                            variant.completelyRemoveMod(hullModID.id)
                            refitPanel.invoke("syncWithCurrentVariant")

                            DisplayMessage.showMessage("Removed hullmod with ID '$hullModID'")
                        }

                        event.consume()
                        return
                    }
                }
            }

        } catch (e: Exception) {
            DisplayMessage.showError("FleetBuilder hotkey failed", e)
        }
    }

    override fun processCampaignInputPreFleetControl(events: List<InputEventAPI?>?) {

    }

    override fun processCampaignInputPostCore(events: List<InputEventAPI?>?) {

    }
}