package fleetBuilder.integration.campaign

import MagicLib.height
import MagicLib.width
import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.codex2.CodexDialog
import com.fs.starfarer.coreui.CaptainPickerDialog
import com.fs.starfarer.coreui.refit.ModWidget
import fleetBuilder.config.ModSettings
import fleetBuilder.features.CommanderShuttle
import fleetBuilder.persistence.FleetSerialization
import fleetBuilder.persistence.MemberSerialization
import fleetBuilder.persistence.PersonSerialization
import fleetBuilder.persistence.VariantSerialization
import fleetBuilder.util.*
import fleetBuilder.util.MISC.campaignPaste
import fleetBuilder.util.MISC.fleetPaste
import fleetBuilder.util.MISC.getMemberUIHoveredInFleetTabLowerPanel
import fleetBuilder.util.MISC.getViewedFleetInFleetPanel
import fleetBuilder.util.MISC.showMessage
import fleetBuilder.variants.LoadoutManager
import fleetBuilder.variants.VariantLib
import org.json.JSONObject
import org.lwjgl.input.Keyboard
import org.lwjgl.util.vector.Vector2f
import starficz.ReflectionUtils.get
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.getMethodsMatching
import starficz.ReflectionUtils.invoke
import java.awt.Color


internal class CampaignClipboardHotkeyHandler : CampaignInputListener {
    override fun getListenerInputPriority(): Int = 1

    override fun processCampaignInputPreCore(events: MutableList<InputEventAPI>) {
        val sector = Global.getSector() ?: return
        val ui = sector.campaignUI ?: return

        events.forEach { event ->
            if (event.isConsumed) return@forEach

            when (event.eventType) {
                InputEventType.KEY_DOWN -> handleKeyDownEvents(event, sector, ui)
                InputEventType.MOUSE_DOWN -> handleMouseDownEvents(event, sector, ui)
                else -> Unit
            }
        }
    }

    private fun handleKeyDownEvents(event: InputEventAPI, sector: SectorAPI, ui: CampaignUIAPI) {
        if (!event.isCtrlDown) return

        when (event.eventValue) {
            Keyboard.KEY_D -> handleDevModeHotkey(event, sector)
            Keyboard.KEY_C -> handleCopyHotkey(event, sector, ui)
            Keyboard.KEY_V -> handlePasteHotkey(event, ui, sector)
        }
    }

    private fun handleMouseDownEvents(event: InputEventAPI, sector: SectorAPI, ui: CampaignUIAPI) {
        if (MISC.getCodexDialog() != null) return//If codex is open, halt.

        val captainPicker = MISC.getCaptainPickerDialog()
        if (captainPicker != null) {
            if (event.isCtrlDown && event.isLMBDownEvent)
                handleCaptainPickerMouseEvents(event, captainPicker)
        } else if (ui.getActualCurrentTab() == CoreUITabId.REFIT) {
            if (event.isCtrlDown && event.isLMBDownEvent)
                handleRefitMouseEvents(event)
            else if (event.isRMBDownEvent && Global.getSettings().isDevMode)
                handleRefitRemoveHullMod(event)
        } else if (ui.getActualCurrentTab() == CoreUITabId.FLEET) {
            handleFleetMouseEvents(event, sector)
        }
    }

    private fun handleDevModeHotkey(event: InputEventAPI, sector: SectorAPI) {
        if (!Global.getSettings().isDevMode) return

        try {
            val codex = MISC.getCodexDialog()
            val param = codex?.let { MISC.getCodexEntryParam(it) } ?: return
            MISC.addParamEntryToFleet(sector, param, false)
            event.consume()
        } catch (e: Exception) {
            MISC.showError("FleetBuilder hotkey failed", e)
        }
    }

    private fun handleCopyHotkey(event: InputEventAPI, sector: SectorAPI, ui: CampaignUIAPI) {
        try {
            val codex = MISC.getCodexDialog()
            when {
                codex != null -> handleCodexCopy(event, codex)
                ui.getActualCurrentTab() == CoreUITabId.FLEET -> handleFleetCopy(event, sector)
                ui.getActualCurrentTab() == CoreUITabId.REFIT -> handleRefitCopy(event)
                ui.currentInteractionDialog != null -> handleInteractionCopy(event, ui)
            }
        } catch (e: Exception) {
            MISC.showError("FleetBuilder hotkey failed", e)
        }
    }

    private fun handleCodexCopy(event: InputEventAPI, codex: CodexDialog) {
        MISC.codexEntryToClipboard(codex)
        event.consume()
    }

    private fun handleInteractionCopy(event: InputEventAPI, ui: CampaignUIAPI) {
        val interaction = ui.currentInteractionDialog
        val plugin = interaction?.plugin
        val battle = (plugin?.context as? FleetEncounterContext)?.battle
        val fleet = if (battle != null && !event.isAltDown) {
            battle.nonPlayerCombined
        } else {
            interaction?.interactionTarget as? CampaignFleetAPI
        }

        fleet?.let { fleetToCopy ->
            val json = FleetSerialization.saveFleetToJson(
                fleetToCopy,
                FleetSerialization.FleetSettings().apply {
                    memberSettings.personSettings.storeLevelingStats = false
                }
            )

            ClipboardUtil.setClipboardText(json.toString(4))
            showMessage(
                if (!event.isAltDown && (battle?.nonPlayerSide?.size ?: 1) > 1) {
                    "Copied interaction fleet with supporting fleets to clipboard"
                } else {
                    "Copied interaction fleet to clipboard"
                }
            )
            event.consume()
        }
    }

    private fun handleFleetCopy(event: InputEventAPI, sector: SectorAPI) {
        val playerFleet = sector.playerFleet.fleetData

        val settings = FleetSerialization.FleetSettings()
        var fleetToCopy: FleetDataAPI? = null
        var uiShowsSubmarketFleet = false

        try {
            fleetToCopy = getViewedFleetInFleetPanel() ?: playerFleet
            if (fleetToCopy !== playerFleet)
                uiShowsSubmarketFleet = true

            val fleetGrid = MISC.getFleetPanel()?.findChildWithMethod("removeItem") ?: return

            @Suppress("UNCHECKED_CAST")
            val items = fleetGrid.invoke("getItems") as? List<UIPanelAPI?> ?: return

            // Collect IDs of visible members from the UI
            val visibleMemberIds = items.mapNotNull { item ->
                (item?.invoke("getMember") as? FleetMemberAPI)?.id
            }.toSet()

            // Exclude any member from the fleet that's not visible in the UI
            fleetToCopy.membersListCopy.forEach { member ->
                if (member.id !in visibleMemberIds) {
                    settings.excludeMembersWithID.add(member.id)
                }
            }
        } catch (e: Exception) {
            MISC.showError("FleetBuilder hotkey had an error", e)
        }
        if (fleetToCopy == null) {
            MISC.showError("FleetBuilder hotkey failed")
            return
        }

        val json = FleetSerialization.saveFleetToJson(fleetToCopy, settings)
        ClipboardUtil.setClipboardText(json.toString(4))
        showMessage("Copied ${if (settings.excludeMembersWithID.isEmpty()) "entire" else "visible"} ${if (uiShowsSubmarketFleet) "submarket fleet" else "fleet"} to clipboard")
        event.consume()
    }

    private fun handleRefitCopy(event: InputEventAPI) {

        val baseVariant = MISC.getCurrentVariantInRefitTab() ?: return

        val variantToSave = baseVariant.clone()
        variantToSave.hullVariantId = VariantLib.makeVariantID(baseVariant)

        val json = VariantSerialization.saveVariantToJson(
            variantToSave,
            VariantSerialization.VariantSettings().apply {
                applySMods = ModSettings.saveSMods
                includeDMods = ModSettings.saveDMods
                includeHiddenMods = ModSettings.saveHiddenMods
            }
        )

        ClipboardUtil.setClipboardText(json.toString(4))

        showMessage("Variant copied to clipboard")
        event.consume()
    }

    private fun handlePasteHotkey(event: InputEventAPI, ui: CampaignUIAPI, sector: SectorAPI) {
        if (ui.getActualCurrentTab() == CoreUITabId.REFIT) {
            handleRefitPaste(event)
        } else if (Global.getSettings().isDevMode) {
            handleFleetPaste(event, sector, ui)
        }
    }

    private fun handleRefitPaste(event: InputEventAPI) {
        val json = ClipboardUtil.getClipboardJson() ?: return
        val (variant, missing) = json.optJSONObject("variant")?.let { variantJson ->
            VariantSerialization.getVariantFromJsonWithMissing(variantJson)//JSON is of a FleetMemberAPI
        } ?: VariantSerialization.getVariantFromJsonWithMissing(json)//JSON is of a ShipVariantAPI (also fallback)

        if (missing.hullIds.isNotEmpty()) {
            showMessage(
                "Failed to import loadout. Could not find hullId ${json.optString("hullId", "")}",
                Color.YELLOW
            )
            event.consume()
            return
        }

        val loadoutExists = LoadoutManager.importShipLoadout(variant, missing)
        showMessage(
            if (!loadoutExists) {
                "Imported loadout with hull: ${variant.hullSpec.hullId}"
            } else {
                "Loadout already exists, cannot import loadout with hull: ${variant.hullSpec.hullId}\n"
            },
            variant.hullSpec.hullId,
            Misc.getHighlightColor()
        )
        event.consume()
    }

    private fun handleFleetPaste(event: InputEventAPI, sector: SectorAPI, ui: CampaignUIAPI) {
        val json = ClipboardUtil.getClipboardJson() ?: run {
            showMessage("No valid json in clipboard", Color.YELLOW)
            event.consume()
            return
        }
        handleHotkeyModePaste(sector, ui, json)
        event.consume()
    }

    private fun handleCaptainPickerMouseEvents(event: InputEventAPI, captainPicker: CaptainPickerDialog) {
        try {
            val officers = captainPicker.invoke("getListOfficers")?.invoke("getItems") as? MutableList<*> ?: return
            val hoverOfficer = officers.firstNotNullOfOrNull { officer ->
                val selector = officer?.invoke("getSelector") ?: return@firstNotNullOfOrNull null
                val fader = selector.invoke("getMouseoverHighlightFader") as? Fader ?: return@firstNotNullOfOrNull null
                if (fader.isFadingIn || fader.brightness == 1f) {
                    val parent = selector.invoke("getParent") ?: return@firstNotNullOfOrNull null
                    parent.invoke("getPerson") as? PersonAPI
                } else null
            } ?: return

            val json = PersonSerialization.savePersonToJson(hoverOfficer)
            ClipboardUtil.setClipboardText(json.toString(4))
            showMessage("Officer copied to clipboard")
            event.consume()
        } catch (e: Exception) {
            MISC.showError("FleetBuilder hotkey failed", e)
        }
    }

    private fun handleFleetMouseEvents(event: InputEventAPI, sector: SectorAPI) {
        try {
            val memberUI = getMemberUIHoveredInFleetTabLowerPanel() ?: return

            val mouseOverMember = memberUI.getFieldsMatching(type = FleetMember::class.java).getOrNull(0)?.get(memberUI) as? FleetMemberAPI
                ?: return

            val portraitPanel = memberUI.invoke("getPortraitButton") ?: return
            val fader = portraitPanel.invoke("getMouseoverHighlightFader") as? Fader ?: return
            val isPortraitHoveredOver = fader.isFadingIn || fader.brightness == 1f

            if (event.isCtrlDown && event.isLMBDownEvent) {
                if (isPortraitHoveredOver) {
                    val json = PersonSerialization.savePersonToJson(mouseOverMember.captain)
                    ClipboardUtil.setClipboardText(json.toString(4))
                    showMessage("Officer copied to clipboard")
                } else {
                    val json = MemberSerialization.saveMemberToJson(mouseOverMember)
                    ClipboardUtil.setClipboardText(json.toString(4))
                    showMessage("Fleet member copied to clipboard")
                }
                event.consume()
            } else if (isPortraitHoveredOver && mouseOverMember.captain.isPlayer) {
                //Hovering over player portrait

                val isShuttle = mouseOverMember.variant.hasHullMod(ModSettings.commandShuttleId)

                if (event.isLMBDownEvent && isShuttle) { // Eat attempt to open captain picker dialog for shuttle. The shuttle is player only
                    event.consume()
                    return
                }

                if (event.isRMBDownEvent) {
                    when {
                        isShuttle -> {
                            if (sector.playerFleet.fleetSizeCount == 1)
                                showMessage("Cannot remove last ship in fleet", Color.YELLOW)
                            else
                                CommanderShuttle.removePlayerShuttle()
                        }

                        ModSettings.unassignPlayer -> {
                            CommanderShuttle.addPlayerShuttle()
                        }

                        else -> {
                            showMessage(
                                "Unassign Player must be on in the FleetBuilder mod settings to unassign the player",
                                Color.YELLOW
                            )
                        }
                    }

                    Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)
                    event.consume()
                }
            }
        } catch (e: Exception) {
            MISC.showError("FleetBuilder hotkey failed", e)
        }
    }

    private fun handleRefitMouseEvents(event: InputEventAPI) {
        try {
            val refitTab = MISC.getRefitTab() ?: return
            val refitTabChildren = refitTab.invoke("getChildrenCopy") as? MutableList<*> ?: return
            val thing = refitTabChildren.lastOrNull() { child ->
                child?.getMethodsMatching("getFleetMemberIndex") != null
            } ?: return
            if (thing.getMethodsMatching("getOfficerAndCRDisplay").isEmpty()) return // The previous thing getter may get children we do not want, as it wasn't programmed good enough. I'm too lazy to fix it right now, so this is here to avoid issues.

            val officerCRDisplay = thing.invoke("getOfficerAndCRDisplay") as? UIPanelAPI ?: return
            val children = officerCRDisplay.invoke("getChildrenCopy") as? List<*> ?: return
            val officerPanel = children.firstOrNull {
                it?.getMethodsMatching(name = "getBar")?.isEmpty() ?: true &&
                        it?.getMethodsMatching(name = "getMouseoverHighlightFader")?.isNotEmpty() ?: false
            } ?: return

            val fader = officerPanel.invoke("getMouseoverHighlightFader") as? Fader ?: return
            if (!(fader.isFadingIn || fader.brightness == 1f)) return

            val member = thing.invoke("getMember") as? FleetMemberAPI ?: return
            val json = PersonSerialization.savePersonToJson(member.captain)
            ClipboardUtil.setClipboardText(json.toString(4))
            showMessage("Officer copied to clipboard")
            event.consume()
        } catch (e: Exception) {
            MISC.showError("FleetBuilder hotkey failed", e)
        }
    }

    private fun handleRefitRemoveHullMod(event: InputEventAPI) {
        try {
            val refitTab = MISC.getRefitTab() ?: return
            //val refitTabChildren = refitTab.invoke("getChildrenCopy") as? MutableList<*> ?: return
            val refitPanel = refitTab.findChildWithMethod("syncWithCurrentVariant") as? UIPanelAPI
            val children = refitPanel?.getChildrenCopy() ?: return
            var desiredChild: UIPanelAPI? = null
            children.forEach { child ->
                var yup = false

                val panel = child as? UIPanelAPI
                val childsChildren = panel?.getChildrenCopy()
                childsChildren?.forEach { childChildChild ->
                    if (childChildChild.getMethodsMatching("removeNotApplicableMods").isNotEmpty()) {
                        yup = true
                        return@forEach
                    }
                }
                if (yup) {
                    desiredChild = child as? UIPanelAPI
                    return@forEach
                }
            }

            val modWidget = desiredChild?.findChildWithMethod("removeNotApplicableMods") as? ModWidget ?: return
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
                    val hullModID = item.get(hullModField.name) as? HullModSpecAPI ?: return@forEach

                    val variant = MISC.getCurrentVariantInRefitTab()
                    if (variant != null) {
                        if (variant.hullSpec.builtInMods.contains(hullModID.id)) {
                            if (variant.sModdedBuiltIns.contains(hullModID.id)) {
                                variant.completelyRemoveMod(hullModID.id)
                                refitPanel.invoke("syncWithCurrentVariant")

                                showMessage("Removed sModdedBuiltIn in with ID '$hullModID'")
                            } else {
                                showMessage("The hullmod '${hullModID.id}' is built into the hullspec, it cannot be removed from the variant")
                            }
                        } else {
                            variant.completelyRemoveMod(hullModID.id)
                            refitPanel.invoke("syncWithCurrentVariant")

                            showMessage("Removed hullmod with ID '$hullModID'")
                        }

                        event.consume()
                        return
                    }
                }
            }

        } catch (e: Exception) {
            MISC.showError("FleetBuilder hotkey failed", e)
        }
    }

    override fun processCampaignInputPreFleetControl(events: MutableList<InputEventAPI>) = Unit

    override fun processCampaignInputPostCore(events: MutableList<InputEventAPI>) = Unit
}

fun handleHotkeyModePaste(
    sector: SectorAPI,
    ui: CampaignUIAPI,
    json: JSONObject
): Boolean {
    if (ui.getActualCurrentTab() == CoreUITabId.FLEET) {
        fleetPaste(sector, json)
    }

    // Handle campaign map paste (no dialog/menu showing)
    if (ui.currentInteractionDialog == null &&
        !ui.isShowingDialog &&
        !ui.isShowingMenu
    ) {
        campaignPaste(sector, json)
    }

    return false
}