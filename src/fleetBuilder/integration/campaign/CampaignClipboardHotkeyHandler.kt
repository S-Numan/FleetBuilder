package fleetBuilder.integration.campaign

import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.coreui.CaptainPickerDialog
import fleetBuilder.config.ModSettings.commandShuttleId
import fleetBuilder.config.ModSettings.fleetClipboardHotkeyHandler
import fleetBuilder.config.ModSettings.unassignPlayer
import fleetBuilder.persistence.FleetSerialization.saveFleetToJson
import fleetBuilder.persistence.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.OfficerSerialization.saveOfficerToJson
import fleetBuilder.persistence.VariantSerialization.getVariantFromJsonWithMissing
import fleetBuilder.util.ClipboardFunctions.codexEntryToClipboard
import fleetBuilder.util.ClipboardFunctions.refitScreenVariantToClipboard
import fleetBuilder.util.ClipboardUtil.getClipboardJson
import fleetBuilder.util.ClipboardUtil.setClipboardText
import fleetBuilder.util.MISC
import fleetBuilder.util.MISC.addParamEntryToFleet
import fleetBuilder.util.MISC.addPlayerShuttle
import fleetBuilder.util.MISC.campaignPaste
import fleetBuilder.util.MISC.fleetPaste
import fleetBuilder.util.MISC.getCodexDialog
import fleetBuilder.util.MISC.getCodexEntryParam
import fleetBuilder.util.MISC.getCoreUI
import fleetBuilder.util.MISC.removePlayerShuttle
import fleetBuilder.util.MISC.showError
import fleetBuilder.util.getActualCurrentTab
import fleetBuilder.variants.LoadoutManager.importShipLoadout
import org.json.JSONObject
import org.lwjgl.input.Keyboard
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.getMethodsMatching
import starficz.ReflectionUtils.invoke
import java.awt.Color


internal class CampaignClipboardHotkeyHandler : CampaignInputListener {
    override fun getListenerInputPriority(): Int {
        return 1
    }

    override fun processCampaignInputPreCore(events: MutableList<InputEventAPI>) {
        if (!fleetClipboardHotkeyHandler) return


        val sector = Global.getSector() ?: return

        //if (!sector.isPaused) return //Return if not paused
        val ui = sector.campaignUI ?: return


        for (event in events) {
            if (event.isConsumed) continue
            if (event.eventType == InputEventType.KEY_DOWN) {
                if (event.isCtrlDown) {
                    if (event.eventValue == Keyboard.KEY_D && Global.getSettings().isDevMode) {
                        try {
                            val codex = getCodexDialog()

                            if (codex != null) {
                                val param = getCodexEntryParam(codex) ?: return

                                addParamEntryToFleet(sector, param)

                                event.consume(); continue
                            }

                        } catch (e: Exception) {
                            showError("FleetBuilder hotkey failed", e)
                        }
                    }
                    if (event.eventValue == Keyboard.KEY_C) {

                        try {
                            //Codex
                            val codex = getCodexDialog()

                            if (codex != null) {
                                codexEntryToClipboard(codex)
                                event.consume(); continue
                            }

                            //Interaction
                            val interaction = ui.currentInteractionDialog
                            if (interaction != null) {//Interacting with a target?
                                val plugin = interaction.plugin
                                var battle: BattleAPI? = null
                                if (plugin.context is FleetEncounterContext) {
                                    battle = (plugin.context as FleetEncounterContext).battle
                                }
                                val fleet: CampaignFleetAPI?
                                var fleetCount = 1

                                if (battle != null && !event.isAltDown) {
                                    fleet = battle.nonPlayerCombined
                                    fleetCount = battle.nonPlayerSide.size
                                } else
                                    fleet = interaction.interactionTarget as? CampaignFleetAPI

                                if (fleet != null) {
                                    val json = saveFleetToJson(fleet, includeOfficerLevelingStats = false)
                                    setClipboardText(json.toString(4))
                                    if (fleetCount > 1) {
                                        MISC.showMessage("Copied interaction fleet with supporting fleets to clipboard");
                                    } else {
                                        MISC.showMessage("Copied interaction fleet to clipboard");
                                    }
                                }

                                event.consume(); continue
                            }


                            if (ui.getActualCurrentTab() == CoreUITabId.FLEET) {
                                val json = saveFleetToJson(sector.playerFleet)
                                setClipboardText(json.toString(4))

                                MISC.showMessage("Copied entire fleet to clipboard"); event.consume(); continue
                            }
                            if (ui.getActualCurrentTab() == CoreUITabId.REFIT) {
                                if (!refitScreenVariantToClipboard()) {
                                    event.consume(); continue
                                }
                                MISC.showMessage("Variant copied to clipboard"); event.consume(); continue
                            }
                        } catch (e: Exception) {
                            showError("FleetBuilder hotkey failed", e)
                        }
                    } else if (event.eventValue == Keyboard.KEY_V) {
                        if (ui.getActualCurrentTab() == CoreUITabId.REFIT) {
                            //Import loadout

                            val json = getClipboardJson() ?: continue

                            val (variant, missing) = json.optJSONObject("variant")?.let { variantJson ->
                                getVariantFromJsonWithMissing(variantJson)//JSON is of a FleetMemberAPI
                            } ?: getVariantFromJsonWithMissing(json)//JSON is of a ShipVariantAPI (fallback)

                            if (missing.hullIds.isNotEmpty()) {
                                val missingHullId = json.optString("hullId", "")
                                MISC.showMessage(
                                    "Failed to import loadout. Could not find hullId $missingHullId",
                                    Color.RED
                                ); event.consume(); continue
                            }

                            val loadoutExists = importShipLoadout(variant, missing)

                            if (!loadoutExists) {
                                MISC.showMessage(
                                    "Imported loadout with hull: ${variant.hullSpec.hullId}",
                                    variant.hullSpec.hullId,
                                    Misc.getHighlightColor()
                                )
                            } else {
                                MISC.showMessage(
                                    "Loadout already exists, cannot import loadout with hull: ${variant.hullSpec.hullId}\n",
                                    variant.hullSpec.hullId,
                                    Misc.getHighlightColor()
                                )
                            }

                            event.consume(); continue
                        } else {
                            if (!Global.getSettings().isDevMode) continue

                            val json = getClipboardJson()
                            if (json == null) {
                                MISC.showMessage("No valid fleet data.", Color.RED); event.consume(); continue
                            }
                            handleHotkeyModePaste(sector, ui, json)
                            event.consume(); continue
                        }
                    }
                }
            } else if (event.eventType == InputEventType.MOUSE_DOWN) {
                if (ui.getActualCurrentTab() == CoreUITabId.FLEET) {

                    if (event.isLMBDownEvent || event.isRMBDownEvent) {

                        try {

                            val codex = getCodexDialog()

                            if (codex != null)//If in codex, stop!
                                continue


                            val core = getCoreUI() ?: continue

                            val children = (core.invoke("getChildrenNonCopy") as MutableList<*>?)

                            var captainPicker: Any? = null

                            if (children != null) {
                                for (i in 0..<children.size) {
                                    val component: Any? = children[i]
                                    if (component is CaptainPickerDialog) {
                                        captainPicker = component
                                        break
                                    }
                                }
                            }

                            if (captainPicker != null) { //Captain picker is open?
                                if (!event.isCtrlDown)
                                    continue

                                val officers =
                                    captainPicker.invoke("getListOfficers")?.invoke("getItems") as MutableList<*>?
                                if (officers == null)
                                    continue

                                var hoverOfficer: PersonAPI? = null

                                for (officer in officers) {
                                    val selector = officer?.invoke("getSelector")
                                    if (selector != null) {
                                        val fader = selector.invoke("getMouseoverHighlightFader") as? Fader
                                            ?: continue
                                        val isHoveredOver = (fader.isFadingIn || fader.brightness == 1f)

                                        if (isHoveredOver) {
                                            //hoverOfficer = officer
                                            val parent = selector.invoke("getParent")
                                            if (parent == null)
                                                continue
                                            val person = parent.invoke("getPerson") as PersonAPI
                                            hoverOfficer = person
                                            break
                                        }
                                    }
                                }

                                if (hoverOfficer != null) {
                                    val json = saveOfficerToJson(hoverOfficer)
                                    setClipboardText(json.toString())
                                    MISC.showMessage("Officer copied to clipboard"); event.consume()
                                }

                                continue
                            }

                            val fleetTab = MISC.getFleetTab() ?: continue

                            val mouseOverMember =
                                fleetTab.invoke("getMousedOverFleetMember") as? FleetMemberAPI ?: continue

                            val fleetPanel = fleetTab.invoke("getFleetPanel") as? UIPanelAPI ?: continue

                            val list = fleetPanel.invoke("getList") ?: continue

                            val items = list.invoke("getItems") as? List<Any?>
                                ?: continue //Core UI box that contains everything related to the fleet member, including the ship, officer, cr, etc. There is one for each member in your fleet.

                            // Find UI element of which the mouse is hovering over
                            val memberUI = items.firstNotNullOfOrNull { item ->
                                if (item == null) return@firstNotNullOfOrNull null

                                //Get all children for this item
                                val children =
                                    item.invoke("getChildrenCopy") as? List<Any?> ?: return@firstNotNullOfOrNull null

                                //Find the UI child with a portrait button
                                val foundUI = children.firstOrNull { child ->
                                    child != null && child.getMethodsMatching(name = "getPortraitButton").isNotEmpty()
                                } ?: return@firstNotNullOfOrNull null

                                //Get FleetMember
                                val fields = foundUI.getFieldsMatching(type = FleetMember::class.java)
                                if (fields.isEmpty()) return@firstNotNullOfOrNull null

                                //Return if this item's fleet member is not the one we are hovering over
                                val member = fields[0].get(foundUI) as? FleetMemberAPI
                                if (member?.id != mouseOverMember.id) return@firstNotNullOfOrNull null

                                //If we've got here, this is the UI item the mouse is hovering over.
                                foundUI
                            } ?: continue

                            val portraitPanel = memberUI.invoke("getPortraitButton") ?: continue
                            val fader = portraitPanel.invoke("getMouseoverHighlightFader") as? Fader
                                ?: continue
                            val isPortraitHoveredOver = (fader.isFadingIn || fader.brightness == 1f)

                            if (event.isCtrlDown && event.isLMBDownEvent) {//Copy to clipboard
                                if (isPortraitHoveredOver) {
                                    val json = saveOfficerToJson(mouseOverMember.captain)
                                    setClipboardText(json.toString(4))
                                    MISC.showMessage("Officer copied to clipboard")
                                } else {
                                    val json = saveMemberToJson(mouseOverMember)
                                    setClipboardText(json.toString(4))
                                    MISC.showMessage("Fleet member copied to clipboard")
                                }
                                event.consume(); continue
                            } else if (isPortraitHoveredOver && mouseOverMember.captain.isPlayer) {//Unassign player officer
                                if (event.isLMBDownEvent) {
                                    //Stop officer assignment screen from showing up on the shuttle. If you change your officer from the player's ship, the ship is removed, then reassign to the ship, the game will crash.
                                    if (mouseOverMember.variant.hasHullMod(commandShuttleId)) {
                                        event.consume(); continue
                                    }
                                }
                                if (event.isRMBDownEvent) {
                                    if (mouseOverMember.variant.hasHullMod(commandShuttleId)) {
                                        if (sector.playerFleet.fleetSizeCount == 1) {//Prevent removing last fleet member
                                            MISC.showMessage(
                                                "Cannot remove last ship in fleet",
                                                Color.RED
                                            ); event.consume(); continue
                                        }
                                        removePlayerShuttle()
                                    } else {
                                        if (!unassignPlayer) {
                                            MISC.showMessage(
                                                "Unassign Player must be on in the FleetBuilder mod settings to unassign the player",
                                                Color.RED
                                            ); event.consume(); continue
                                        }
                                        addPlayerShuttle()
                                    }
                                    Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)
                                    event.consume(); continue
                                }
                            }
                        } catch (e: Exception) {
                            showError("FleetBuilder hotkey failed", e)
                        }
                    }
                }

                if (event.isCtrlDown && event.isLMBDownEvent) {
                    if (ui.getActualCurrentTab() == CoreUITabId.REFIT) {
                        try {
                            val refitTab = MISC.getRefitTab()
                            if (refitTab == null)
                                continue

                            val refitTabChildren = refitTab.invoke("getChildrenCopy") as MutableList<*>

                            var thing: Any? = null
                            refitTabChildren.forEach { child ->
                                if (child?.getMethodsMatching("getFleetMemberIndex") != null) {
                                    thing = child
                                    // break
                                }
                            }

                            if (thing == null)
                                continue

                            val officerCRDisplay = thing.invoke("getOfficerAndCRDisplay") as UIPanelAPI

                            val children = officerCRDisplay.invoke("getChildrenCopy") as List<UIComponentAPI>

                            val officerPanel: Any? = children.find {
                                it.getMethodsMatching(name = "getBar")
                                    .isEmpty() && it.getMethodsMatching(name = "getMouseoverHighlightFader")
                                    .isNotEmpty()
                            }

                            if (officerPanel == null)
                                continue

                            val fader = officerPanel.invoke("getMouseoverHighlightFader") as? Fader
                                ?: continue
                            val isHoveredOver = (fader.isFadingIn || fader.brightness == 1f)

                            if (isHoveredOver) {
                                val member = thing.invoke("getMember") as FleetMemberAPI

                                val json = saveOfficerToJson(member.captain)
                                setClipboardText(json.toString())
                                MISC.showMessage("Officer copied to clipboard")
                                event.consume()
                            }

                            continue
                        } catch (e: Exception) {
                            showError("FleetBuilder hotkey failed", e)
                        }
                    }

                }
            }
        }
    }

    override fun processCampaignInputPreFleetControl(events: MutableList<InputEventAPI>) {

    }

    override fun processCampaignInputPostCore(events: MutableList<InputEventAPI>) {

    }
}

fun handleHotkeyModePaste(
    sector: SectorAPI,
    ui: CampaignUIAPI,
    json: JSONObject
): Boolean {
    if (ui.getActualCurrentTab() == CoreUITabId.FLEET) {
        return fleetPaste(sector, json)
    }

    // Handle campaign map paste (no dialog/menu showing)
    if (ui.currentInteractionDialog == null &&
        !ui.isShowingDialog &&
        !ui.isShowingMenu
    ) {
        return campaignPaste(sector, json)
    }

    return false
}