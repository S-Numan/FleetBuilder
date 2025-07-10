package fleetBuilder.integration.combat

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.loading.specs.HullVariantSpec
import fleetBuilder.config.ModSettings
import fleetBuilder.config.ModSettings.fleetClipboardHotkeyHandler
import fleetBuilder.util.ClipboardUtil
import fleetBuilder.util.MISC
import fleetBuilder.util.MISC.codexEntryToClipboard
import fleetBuilder.util.MISC.getCodexDialog
import fleetBuilder.util.MISC.showError
import fleetBuilder.util.MISC.showMessage
import fleetBuilder.util.ModifyInternalVariants
import fleetBuilder.util.findChildWithMethod
import fleetBuilder.variants.MissingElements
import org.lwjgl.input.Keyboard
import starficz.ReflectionUtils.invoke
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
                                codexEntryToClipboard(codex)
                                event.consume(); continue
                            }
                        } catch (e: Exception) {
                            showError("FleetBuilder hotkey failed", e)
                        }
                    } else if (event.eventValue == Keyboard.KEY_V || event.eventValue == Keyboard.KEY_D) {
                        if (Global.getCombatEngine().isSimulation) {
                            val core = MISC.getCoreUI() ?: return
                            val simulatorUI = core.findChildWithMethod("enableAdvanced") ?: return

                            val variantIdList = mutableListOf<String>()


                            ModifyInternalVariants.clearAllModifiedVariants()
                            var element: Any? = null
                            val missing = MissingElements()

                            if (event.eventValue == Keyboard.KEY_V) {
                                val json = ClipboardUtil.getClipboardJson()
                                if (json == null) {
                                    MISC.showMessage("No valid json in clipboard", Color.YELLOW)
                                    event.consume()
                                    continue
                                }

                                val (tempElement, tempMissing) = MISC.getAnyFromJson(json)
                                element = tempElement
                                missing.add(tempMissing)
                            } else if (event.eventValue == Keyboard.KEY_D) {
                                element = Global.getSector().playerFleet
                                val commandShuttleMember = element?.fleetData?.membersListCopy?.find { it.variant.hasHullMod(ModSettings.commandShuttleId) }
                                if (commandShuttleMember != null)
                                    element.fleetData.removeFleetMember(commandShuttleMember)
                            }

                            when (element) {
                                is CampaignFleetAPI -> {
                                    element.fleetData.membersListCopy.forEach { member ->
                                        ModifyInternalVariants.setModifiedInternalVariant(member.variant as HullVariantSpec)
                                        variantIdList.add("${ModifyInternalVariants.safteyPrefix}${member.variant.hullVariantId}")
                                    }
                                }

                                is FleetMemberAPI -> {
                                    ModifyInternalVariants.setModifiedInternalVariant(element.variant as HullVariantSpec)
                                    variantIdList.add("${ModifyInternalVariants.safteyPrefix}${element.variant.hullVariantId}")
                                }

                                is ShipVariantAPI -> {
                                    ModifyInternalVariants.setModifiedInternalVariant(element as HullVariantSpec)
                                    variantIdList.add("${ModifyInternalVariants.safteyPrefix}${element.hullVariantId}")
                                }

                                else -> {
                                    MISC.showMessage("Could not put element into simulator reserves", Color.YELLOW)
                                    event.consume()
                                    continue
                                }
                            }

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


                            showMessage("Replaced simulator reserves")
                            event.consume()
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