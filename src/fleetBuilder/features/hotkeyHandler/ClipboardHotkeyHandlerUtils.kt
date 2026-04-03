package fleetBuilder.features.hotkeyHandler

import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.coreui.CaptainPickerDialog
import fleetBuilder.core.FBConst
import fleetBuilder.core.FBSettings
import fleetBuilder.core.FBSettings.randomPastedCosmetics
import fleetBuilder.core.FBTxt
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.core.displayMessage.DisplayMessage.showMessage
import fleetBuilder.features.autofit.shipDirectory.ShipDirectoryService.doesLoadoutExist
import fleetBuilder.features.commanderShuttle.CommanderShuttle
import fleetBuilder.features.hotkeyHandler.HotkeyHandlerDialogs.createDevModeDialog
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.otherMods.starficz.ReflectionUtils.getMethodsMatching
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.serialization.ClipboardMisc
import fleetBuilder.serialization.MissingContent
import fleetBuilder.serialization.fleet.CompressedFleet
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.serialization.fleet.DataFleet.getFleetDataFromFleet
import fleetBuilder.serialization.fleet.FleetSettings
import fleetBuilder.serialization.fleet.JSONFleet.saveFleetToJson
import fleetBuilder.serialization.member.DataMember
import fleetBuilder.serialization.member.DataMember.buildMemberFull
import fleetBuilder.serialization.person.DataPerson
import fleetBuilder.serialization.person.DataPerson.buildPersonFull
import fleetBuilder.serialization.reportMissingContentIfAny
import fleetBuilder.serialization.variant.DataVariant
import fleetBuilder.serialization.variant.DataVariant.buildVariantFull
import fleetBuilder.ui.customPanel.common.DialogPanel
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.ReflectionMisc.getMemberUIHoveredInFleetTabLowerPanel
import fleetBuilder.util.ReflectionMisc.getViewedFleetInFleetPanel
import fleetBuilder.util.ReflectionMisc.updateFleetPanelContents
import fleetBuilder.util.api.MemberUtils.randomizeMemberCosmetics
import fleetBuilder.util.api.PersonUtils
import fleetBuilder.util.kotlin.getActualCurrentTab
import fleetBuilder.util.kotlin.safeInvoke
import fleetBuilder.util.lib.ClipboardUtil
import java.awt.Color

internal object ClipboardHotkeyHandlerUtils {

    inline fun hotkeySafe(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            DisplayMessage.showError(
                FBTxt.txt("mod_hotkey_failed", FBSettings.getModName()),
                e
            )
        }
    }

    fun requireCheatsOrWarn(): Boolean {
        if (!FBSettings.cheatsEnabled()) {
            DisplayMessage.showMessage(
                FBTxt.txt("enable_cheats_to_use_paste", FBSettings.getModName()),
                Color.YELLOW
            )
            return false
        }
        return true
    }

    fun Fader.isHovered(): Boolean = isFadingIn || brightness == 1f

    //
    // Require reflection
    //

    fun handleCaptainPickerMouseEvents(
        event: InputEventAPI,
        captainPicker: CaptainPickerDialog
    ) = hotkeySafe {
        val officers =
            captainPicker.safeInvoke("getListOfficers")
                ?.safeInvoke("getItems") as? MutableList<*> ?: return

        val hoverOfficer = officers.firstNotNullOfOrNull { officer ->
            val selector = officer?.safeInvoke("getSelector") ?: return@firstNotNullOfOrNull null
            val fader = selector.safeInvoke("getMouseoverHighlightFader") as? Fader
                ?: return@firstNotNullOfOrNull null

            if (fader.isHovered()) {
                val parent = selector.safeInvoke("getParent")
                    ?: return@firstNotNullOfOrNull null

                parent.safeInvoke("getPerson") as? PersonAPI

            } else null
        } ?: return

        ClipboardHotkeyHandlerUtils.personClick(event, hoverOfficer)
    }

    fun handleFleetMouseEvents(
        event: InputEventAPI
    ) = hotkeySafe {
        val memberUI = getMemberUIHoveredInFleetTabLowerPanel() ?: return

        val member =
            memberUI.getFieldsMatching(type = FleetMember::class.java)
                .firstOrNull()
                ?.get(memberUI) as? FleetMemberAPI ?: return

        val portraitPanel = memberUI.safeInvoke("getPortraitButton") ?: return
        val portraitFader = portraitPanel.safeInvoke("getMouseoverHighlightFader") as? Fader ?: return

        if (!portraitFader.isHovered()) {
            ClipboardHotkeyHandlerUtils.memberClick(event, member)
        } else {
            member.captain?.let {
                ClipboardHotkeyHandlerUtils.personClick(event, it)
            }
        }
    }

    fun handleRefitMouseEvents(event: InputEventAPI) = hotkeySafe {
        val refitTab = ReflectionMisc.getRefitTab() ?: return
        val children = refitTab.safeInvoke("getChildrenCopy") as? MutableList<*> ?: return

        val thing = children.lastOrNull {
            it?.getMethodsMatching("getFleetMemberIndex") != null
        } ?: return

        if (thing.getMethodsMatching("getOfficerAndCRDisplay").isEmpty()) return

        val officerCRDisplay = thing.safeInvoke("getOfficerAndCRDisplay") as? UIPanelAPI ?: return
        val displayChildren = officerCRDisplay.safeInvoke("getChildrenCopy") as? List<*> ?: return

        val officerPanel = displayChildren.firstOrNull {
            it?.getMethodsMatching(name = "getBar")?.isEmpty() ?: true &&
                    it?.getMethodsMatching(name = "getMouseoverHighlightFader")?.isNotEmpty() ?: false
        } ?: return

        val fader = officerPanel.safeInvoke("getMouseoverHighlightFader") as? Fader ?: return

        if (!fader.isHovered()) return

        val member = thing.safeInvoke("getMember") as? FleetMemberAPI ?: return

        member.captain?.let {
            ClipboardHotkeyHandlerUtils.personClick(event, it)
        }
    }

    fun handleUIFleetCopy(sector: SectorAPI, isShiftDown: Boolean = false): Boolean {
        val playerFleet = sector.playerFleet?.fleetData ?: return false

        val settings = FleetSettings().apply {
            includeIdleOfficers = false
        }

        val fleetToCopy = getViewedFleetInFleetPanel() ?: playerFleet
        val uiShowsSubmarketFleet = fleetToCopy !== playerFleet

        hotkeySafe {
            val fleetGrid = ReflectionMisc.getFleetPanel()
                ?.findChildWithMethod("removeItem")
                ?: return false

            @Suppress("UNCHECKED_CAST")
            val items = fleetGrid.safeInvoke("getItems") as? List<UIPanelAPI?> ?: return false

            val visibleMemberIds = buildSet {
                for (item in items) {
                    val member = item?.safeInvoke("getMember") as? FleetMemberAPI
                    member?.id?.let(::add)
                }
            }

            for (member in fleetToCopy.membersListCopy) {
                if (member.id !in visibleMemberIds) {
                    settings.excludeMembersWithID.add(member.id)
                }
            }
        }

        val key = if (isShiftDown) {
            val json = saveFleetToJson(fleetToCopy, settings)
            ClipboardUtil.setClipboardText(json.toString(4))
            when {
                settings.excludeMembersWithID.isEmpty() && !uiShowsSubmarketFleet -> "copied_entire_fleet_to_clipboard"
                settings.excludeMembersWithID.isEmpty() -> "copied_entire_submarket_to_clipboard"
                !uiShowsSubmarketFleet -> "copied_visible_fleet_to_clipboard"
                else -> "copied_visible_submarket_to_clipboard"
            }
        } else {
            val comp = CompressedFleet.saveFleetToCompString(fleetToCopy)
            ClipboardUtil.setClipboardText(comp)
            when {
                settings.excludeMembersWithID.isEmpty() && !uiShowsSubmarketFleet -> "copied_entire_fleet_to_clipboard_compressed"
                settings.excludeMembersWithID.isEmpty() -> "copied_entire_submarket_to_clipboard_compressed"
                !uiShowsSubmarketFleet -> "copied_visible_fleet_to_clipboard_compressed"
                else -> "copied_visible_submarket_to_clipboard_compressed"
            }
        }

        DisplayMessage.showMessage(FBTxt.txt(key))

        return true
    }

    //
    // Require reflection
    //

    fun handleSaveTransfer(event: InputEventAPI, ui: CampaignUIAPI) {
        if (ReflectionMisc.isCodexOpen()) return

        if (ui.getActualCurrentTab() == null &&
            ui.currentInteractionDialog == null
        ) {
            event.consume()
            HotkeyHandlerDialogs.createSaveTransferDialog()
        }
    }

    fun handleCreateOfficer(event: InputEventAPI, ui: CampaignUIAPI) {
        if (ReflectionMisc.getCodexDialog() != null) return

        if (ui.getActualCurrentTab() == CoreUITabId.FLEET ||
            (ui.getActualCurrentTab() == null && ui.currentInteractionDialog == null)
        ) {
            event.consume()
            if (!requireCheatsOrWarn()) return
            HotkeyHandlerDialogs.createOfficerCreatorDialog()
        }
    }

    fun handleDevModeHotkey(event: InputEventAPI) {
        if (ReflectionMisc.isCodexOpen()) return

        event.consume()
        createDevModeDialog()
    }

    fun handleInteractionCopy(ui: CampaignUIAPI, isAltDown: Boolean = false, isShiftDown: Boolean = false): Boolean {
        val interaction = ui.currentInteractionDialog
        val plugin = interaction?.plugin
        val battle = (plugin?.context as? FleetEncounterContext)?.battle

        val fleet = if (battle != null && !isAltDown) {
            battle.nonPlayerCombined
        } else {
            interaction?.interactionTarget as? CampaignFleetAPI
        }

        fleet?.let { fleetToCopy ->
            val txt = if (isShiftDown) {
                val json = saveFleetToJson(
                    fleetToCopy,
                    FleetSettings().apply {
                        memberSettings.personSettings.handleXpAndPoints = false
                    }
                )

                ClipboardUtil.setClipboardText(json.toString(4))

                if (!isAltDown && (battle?.nonPlayerSide?.size ?: 1) > 1)
                    FBTxt.txt("copied_interaction_fleet_with_supporting_to_clipboard")
                else
                    FBTxt.txt("copied_interaction_fleet_to_clipboard")
            } else {
                val comp = CompressedFleet.saveFleetToCompString(
                    fleetToCopy.fleetData,
                    settings = FleetSettings().apply {
                        memberSettings.personSettings.handleXpAndPoints = false
                    }
                )
                ClipboardUtil.setClipboardText(comp)

                if (!isAltDown && (battle?.nonPlayerSide?.size ?: 1) > 1)
                    FBTxt.txt("copied_interaction_fleet_with_supporting_to_clipboard_compressed")
                else
                    FBTxt.txt("copied_interaction_fleet_to_clipboard_compressed")
            }

            DisplayMessage.showMessage(txt)

            return true
        }
        return false
    }

    fun handleRefitPaste(): Boolean {
        val missing = MissingContent()

        var data = ClipboardMisc.extractDataFromClipboard(missing) ?: return false

        if (data is DataMember.ParsedMemberData && data.variantData != null) {
            data = data.variantData
        }
        if (data !is DataVariant.ParsedVariantData) {
            DisplayMessage.showMessage(FBTxt.txt("data_valid_but_no_variant"), Color.YELLOW)
            return true
        }

        val variant = buildVariantFull(data, missing = missing)

        if (missing.hullIds.isNotEmpty()) {
            DisplayMessage.showMessage(
                FBTxt.txt("failed_to_import_loadout", missing.hullIds.first()),
                Color.YELLOW
            )
            return true
        }


        val loadoutExists = doesLoadoutExist(FBSettings.defaultPrefix, variant)

        if (!loadoutExists) {
            HotkeyHandlerDialogs.createImportLoadoutDialog(variant, missing)
        } else {
            DisplayMessage.showMessage(
                FBTxt.txt("loadout_already_exists", variant.hullSpec.hullId),
                variant.hullSpec.hullName,
                Misc.getHighlightColor()
            )
        }

        return true
    }

    fun handleRefitCopy(isShiftDown: Boolean): Boolean {
        val baseVariant = ReflectionMisc.getCurrentVariantInRefitTab() ?: return false

        ClipboardMisc.saveVariantToClipboard(baseVariant, isShiftDown)
        return true
    }

    fun personClick(event: InputEventAPI, person: PersonAPI) {
        if (event.isLMBDownEvent) {
            if (event.isCtrlDown) {
                ClipboardMisc.savePersonToClipboard(person, event.isShiftDown)
                event.consume()
            }
        }
        if (event.isRMBDownEvent) {
            if (person.isPlayer) {
                val isShuttle = Global.getSector().playerFleet.fleetData.membersListCopy.find { it.captain === person }?.variant?.hasHullMod(FBConst.COMMAND_SHUTTLE_ID) == true

                /*if (event.isLMBDownEvent && isShuttle) { // Eat attempt to open captain picker dialog for shuttle. The shuttle is player only
                    event.consume()
                    return
                }*/

                when {
                    isShuttle -> {
                        if (Global.getSector()?.playerFleet?.fleetSizeCount == 1)
                            DisplayMessage.showMessage(FBTxt.txt("cannot_remove_last_ship_in_fleet"), Color.YELLOW)
                        else
                            CommanderShuttle.removePlayerShuttle()
                    }

                    FBSettings.unassignPlayer() -> {
                        CommanderShuttle.addPlayerShuttle()
                    }

                    else -> {
                        DisplayMessage.showMessage(
                            FBTxt.txt("enable_unassign_player", FBSettings.getModName()),
                            Color.YELLOW
                        )
                    }
                }

                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)
                event.consume()
            }
        }
    }

    fun memberClick(event: InputEventAPI, member: FleetMemberAPI) {
        if (event.isCtrlDown) {
            ClipboardMisc.saveMemberToClipboard(member, event.isShiftDown)
            event.consume()
        }
    }

    fun pasteFleet(
        data: Any,
        missing: MissingContent = MissingContent()
    ): DialogPanel? {
        var newData = data
        if (newData !is DataFleet.ParsedFleetData) {
            fun hackTogetherFleet(member: FleetMemberAPI) {
                val fleet = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, FleetTypes.TASK_FORCE, true)
                fleet.fleetData.addFleetMember(member)
                if (!member.captain.isDefault) {
                    fleet.fleetData.addOfficer(member.captain)
                    fleet.commander = member.captain
                }
                //fleet.fleetData.setFlagship(member)

                newData = getFleetDataFromFleet(fleet)
            }
            if (newData is DataMember.ParsedMemberData) {
                val member = buildMemberFull(newData as DataMember.ParsedMemberData)
                hackTogetherFleet(member)
            } else if (newData is DataVariant.ParsedVariantData) {
                val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, buildVariantFull(newData as DataVariant.ParsedVariantData))
                hackTogetherFleet(member)
            } else if (newData is DataPerson.ParsedPersonData) {
                DisplayMessage.showMessage(FBTxt.txt("campaign_officer_spawn"), Color.YELLOW)
                return null
            } else {
                DisplayMessage.showMessage(FBTxt.txt("data_valid_but_no_campaign_paste"), Color.YELLOW)
                return null
            }
        }

        return HotkeyHandlerDialogs.pasteFleetDialog(newData as DataFleet.ParsedFleetData, missing)
    }

    fun pasteIntoPlayerFleetPanel(
        data: Any,
        missing: MissingContent = MissingContent()
    ) {
        val playerFleet = Global.getSector().playerFleet.fleetData

        var uiShowsSubmarketFleet = false

        val fleetToAddTo = getViewedFleetInFleetPanel() ?: playerFleet
        if (fleetToAddTo !== playerFleet)
            uiShowsSubmarketFleet = true

        when (data) {
            is DataPerson.ParsedPersonData -> {
                // Officer
                val person = buildPersonFull(data, missing = missing)

                if (randomPastedCosmetics) {
                    PersonUtils.randomizePersonCosmetics(person, playerFleet.fleet.faction)
                }
                playerFleet.addOfficer(person)
                showMessage(FBTxt.txt("added_officer_to_fleet"))
            }

            is DataVariant.ParsedVariantData -> {
                // Variant
                val variant = buildVariantFull(data, missing = missing)

                if (missing.hullIds.size > 1) {
                    reportMissingContentIfAny(missing, FBTxt.txt("could_not_find_hullid_when_variant", missing.hullIds.first()))
                    return
                }

                val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant)

                if (randomPastedCosmetics)
                    randomizeMemberCosmetics(member, fleetToAddTo)

                fleetToAddTo.addFleetMember(member)

                val shipName = variant.hullSpec.hullName

                val message = if (uiShowsSubmarketFleet)
                    FBTxt.txt("added_ship_to_submarket", shipName)
                else
                    FBTxt.txt("added_ship_to_fleet", shipName)

                showMessage(message, shipName, Misc.getHighlightColor())

                updateFleetPanelContents()
            }

            is DataMember.ParsedMemberData -> {
                // Fleet member
                val member = buildMemberFull(data, missing = missing)

                if (missing.hullIds.size > 1) {
                    reportMissingContentIfAny(missing, FBTxt.txt("could_not_find_hullid_when_member", missing.hullIds.first()))
                    return
                }

                if (randomPastedCosmetics)
                    randomizeMemberCosmetics(member, fleetToAddTo)

                fleetToAddTo.addFleetMember(member)
                if (!member.captain.isDefault && !member.captain.isAICore && !uiShowsSubmarketFleet)
                    fleetToAddTo.addOfficer(member.captain)

                val shipName = member.hullSpec.hullName
                val message = if (uiShowsSubmarketFleet) {
                    if (member.captain.isDefault) {
                        FBTxt.txt("added_ship_to_submarket", shipName)
                    } else {
                        if (member.captain.faction.id != "tahlan_allmother") {
                            member.captain.memoryWithoutUpdate.set(FBConst.STORED_OFFICER_TAG, true)
                            member.captain.memoryWithoutUpdate.set(Misc.CAPTAIN_UNREMOVABLE, true)
                        }
                        FBTxt.txt("added_ship_to_submarket_with_officer", shipName)
                    }
                } else {
                    if (member.captain.isDefault) {
                        FBTxt.txt("added_ship_to_fleet", shipName)
                    } else {
                        FBTxt.txt("added_ship_to_fleet_with_officer", shipName)
                    }
                }

                showMessage(message, shipName, Misc.getHighlightColor())

                updateFleetPanelContents()
            }

            else -> {
                DisplayMessage.showMessage(FBTxt.txt("data_valid_but_not_fleet_member_variant_person"), Color.YELLOW)
            }
        }

        reportMissingContentIfAny(missing)
    }
}