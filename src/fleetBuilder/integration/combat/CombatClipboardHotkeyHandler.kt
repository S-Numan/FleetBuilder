package fleetBuilder.integration.combat

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.loading.specs.HullVariantSpec
import fleetBuilder.config.ModSettings.fleetClipboardHotkeyHandler
import fleetBuilder.persistence.fleet.DataFleet
import fleetBuilder.persistence.fleet.DataFleet.createCampaignFleetFromData
import fleetBuilder.persistence.fleet.DataFleet.getFleetDataFromFleet
import fleetBuilder.persistence.member.DataMember
import fleetBuilder.persistence.variant.DataVariant
import fleetBuilder.persistence.variant.DataVariant.buildVariantFull
import fleetBuilder.util.*
import fleetBuilder.util.FBMisc.handleRefitCopy
import fleetBuilder.util.FBMisc.handleRefitPaste
import fleetBuilder.util.ReflectionMisc.getCodexDialog
import fleetBuilder.util.ReflectionMisc.getCoreUI
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.reportMissingElementsIfAny
import org.lwjgl.input.Keyboard
import starficz.ReflectionUtils.invoke
import starficz.findChildWithMethod
import java.awt.Color

internal class CombatClipboardHotkeyHandler : EveryFrameCombatPlugin {

    var initSimTest = false
    override fun processInputPreCoreControls(
        amount: Float,
        events: List<InputEventAPI>
    ) {
        if (!fleetClipboardHotkeyHandler) return

        for (event in events) {
            if (event.isConsumed) continue
            if (event.eventType == InputEventType.KEY_DOWN) {
                if (event.isCtrlDown) {
                    if (event.eventValue == Keyboard.KEY_C) {
                        try {
                            val codex = getCodexDialog()

                            if (codex != null) {
                                ClipboardMisc.codexEntryToClipboard(codex)
                                event.consume(); continue
                            }
                            if (handleRefitCopy(event.isShiftDown))
                                event.consume()

                        } catch (e: Exception) {
                            DisplayMessage.showError("FleetBuilder hotkey failed", e)
                        }
                    } else if (event.eventValue == Keyboard.KEY_V || event.eventValue == Keyboard.KEY_D) {
                        if (event.isShiftDown && event.eventValue == Keyboard.KEY_D && !DialogUtil.isPopUpUIOpen() && !ReflectionMisc.isCodexOpen()) {
                            Dialogs.createDevModeDialog()
                            event.consume()
                            continue
                        }
                        if (Global.getCombatEngine().isSimulation) {
                            val core = getCoreUI() ?: return
                            val simulatorUI = core.findChildWithMethod("enableAdvanced") ?: return

                            val variantIdList = mutableListOf<String>()


                            ModifyInternalVariants.clearAllModifiedVariants()
                            var element: Any? = null

                            if (event.eventValue == Keyboard.KEY_V) {
                                val data = ClipboardMisc.extractDataFromClipboard() ?: return
                                if (data is DataVariant.ParsedVariantData || data is DataMember.ParsedMemberData || data is DataFleet.ParsedFleetData) {
                                    //
                                } else {
                                    DisplayMessage.showMessage("No valid data in clipboard", Color.YELLOW)
                                    event.consume()
                                    continue
                                }

                                element = data
                            } else if (event.eventValue == Keyboard.KEY_D) {
                                val playerFleet = Global.getSector().playerFleet

                                element = getFleetDataFromFleet(playerFleet)
                            }

                            var missing = MissingElements()

                            when (element) {
                                is DataVariant.ParsedVariantData -> {
                                    val variant = buildVariantFull(element, missing = missing)

                                    ModifyInternalVariants.setModifiedInternalVariant(variant as HullVariantSpec)
                                    variantIdList.add("${ModifyInternalVariants.safteyPrefix}${variant.hullVariantId}")
                                }

                                is DataMember.ParsedMemberData -> {
                                    var variant: ShipVariantAPI? = null
                                    if (element.variantData != null)
                                        variant = buildVariantFull(element.variantData, missing = missing)

                                    ModifyInternalVariants.setModifiedInternalVariant(variant as HullVariantSpec)
                                    variantIdList.add("${ModifyInternalVariants.safteyPrefix}${variant.hullVariantId}")
                                }

                                is DataFleet.ParsedFleetData -> {
                                    val fleet = createCampaignFleetFromData(element, false, missing = missing)

                                    fleet.fleetData.membersListCopy.forEach { member ->
                                        ModifyInternalVariants.setModifiedInternalVariant(member.variant as HullVariantSpec)
                                        variantIdList.add("${ModifyInternalVariants.safteyPrefix}${member.variant.hullVariantId}")
                                    }
                                }
                            }

                            reportMissingElementsIfAny(missing)


                            //simulatorUI.invoke("õ00000", true)//Remake Codex UI to show new members

                            simulatorUI.invoke("updateReserves", variantIdList, null)

                            /*
                            val uiFields = simulatorUI.getFieldsMatching(fieldAssignableTo = UIPanelAPI::class.java)
                            val tempArray = uiFields.map { field ->
                                simulatorUI.get(field.name) as? UIPanelAPI
                            }
                            //val bottomShipSelector = tempArray.find { it.getMethodsMatching(name = "isShowDmods").isNotEmpty() }

                            val combatFleetManagers = simulatorUI.getFieldsMatching(fieldAssignableTo = CombatFleetManager::class.java)
                            val combatFleetManager = combatFleetManagers.getOrNull(1)?.get(simulatorUI) as? CombatFleetManager
                            val reserves = combatFleetManager?.reserves as LinkedHashSet<FleetMember>*/


                            //val plugin = Misc.getSimulatorPlugin() ?: return
                            //if (plugin !is SimulatorPluginImpl) return
                            //plugin.addCustomOpponents(variantIdList)


                            DisplayMessage.showMessage("Replaced simulator reserves")
                            event.consume()
                        } else if (event.eventValue == Keyboard.KEY_V) {
                            if (!ReflectionMisc.isCodexOpen() && !DialogUtil.isPopUpUIOpen())
                                if (handleRefitPaste())
                                    event.consume()

                            continue
                        }
                    }
                }
            }
        }
    }

    override fun advance(
        amount: Float,
        events: List<InputEventAPI?>?
    ) {

    }

    override fun renderInWorldCoords(viewport: ViewportAPI?) {

    }

    override fun renderInUICoords(viewport: ViewportAPI?) {

    }


    @Deprecated("Deprecated in Java")
    override fun init(engine: CombatEngineAPI?) {

    }

}