package fleetBuilder.integration.campaign

import MagicLib.height
import MagicLib.width
import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.SkillPickPreference
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.Fonts
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.codex2.CodexDialog
import com.fs.starfarer.coreui.CaptainPickerDialog
import com.fs.starfarer.coreui.refit.ModWidget
import com.fs.starfarer.loading.specs.HullVariantSpec
import fleetBuilder.config.ModSettings
import fleetBuilder.features.CommanderShuttle
import fleetBuilder.persistence.FleetSerialization
import fleetBuilder.persistence.MemberSerialization
import fleetBuilder.persistence.PersonSerialization
import fleetBuilder.persistence.VariantSerialization
import fleetBuilder.ui.PopUpUI.PopUpUIDialog
import fleetBuilder.ui.autofit.AutofitPanel
import fleetBuilder.ui.autofit.AutofitSelector
import fleetBuilder.ui.autofit.AutofitSpec
import fleetBuilder.util.*
import fleetBuilder.util.ClipboardUtil.getClipboardJson
import fleetBuilder.util.ClipboardUtil.setClipboardText
import fleetBuilder.util.FBMisc.campaignPaste
import fleetBuilder.util.FBMisc.compilePlayerSaveJson
import fleetBuilder.util.FBMisc.createDevModeDialog
import fleetBuilder.util.FBMisc.fleetPaste
import fleetBuilder.util.FBMisc.initPopUpUI
import fleetBuilder.util.FBMisc.loadPlayerCompiledSave
import fleetBuilder.util.FBMisc.reportMissingElementsIfAny
import fleetBuilder.util.ReflectionMisc.getMemberUIHoveredInFleetTabLowerPanel
import fleetBuilder.util.ReflectionMisc.getViewedFleetInFleetPanel
import fleetBuilder.variants.LoadoutManager.doesLoadoutExist
import fleetBuilder.variants.LoadoutManager.importShipLoadout
import fleetBuilder.variants.VariantLib
import org.lazywizard.lazylib.MathUtils
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
            Keyboard.KEY_O -> handleCreateOfficer(event, ui)
            Keyboard.KEY_I -> handleSaveTransfer(event, ui)
        }
    }

    private fun handleSaveTransfer(event: InputEventAPI, ui: CampaignUIAPI) {
        //if (!Global.getSettings().isDevMode) return
        if (ReflectionMisc.isCodexOpen() || FBMisc.isPopUpUIOpen()) return
        if ((ui.getActualCurrentTab() == null && ui.currentInteractionDialog == null)) {
            event.consume()

            val initialDialog = PopUpUIDialog("Save Transfer", addCancelButton = false, addConfirmButton = false, addCloseButton = true)

            initialDialog.addButton("Copy Save") { _ ->
                val dialog = PopUpUIDialog("Copy Save", addConfirmButton = true, addCancelButton = true)
                dialog.confirmButtonName = "Copy"
                dialog.confirmAndCancelAlignment = Alignment.MID

                dialog.addButton("Flip All Values", dismissOnClick = false) { fields ->
                    dialog.toggleRefs.values.forEach { it.isChecked = !it.isChecked }
                }
                dialog.addPadding(dialog.buttonHeight)
                dialog.addToggle("Include Blueprints", true)
                dialog.addToggle("Include Hullmods", true)
                dialog.addToggle("Include Player", true)
                dialog.addToggle("Include Fleet", true)
                dialog.addToggle("Include Officers", true)
                dialog.addToggle("Include Reputation", true)
                dialog.addToggle("Include Cargo", true)
                dialog.addToggle("Include Credits", true)

                dialog.onConfirm { fields ->
                    val json = FBMisc.createPlayerSaveJson(
                        handleCargo = fields["Include Cargo"] as Boolean,
                        handleRelations = fields["Include Reputation"] as Boolean,
                        handleKnownBlueprints = fields["Include Blueprints"] as Boolean,
                        handlePlayer = fields["Include Player"] as Boolean,
                        handleFleet = fields["Include Fleet"] as Boolean,
                        handleCredits = fields["Include Credits"] as Boolean,
                        handleKnownHullmods = fields["Include Hullmods"] as Boolean,
                        handleOfficers = fields["Include Officers"] as Boolean
                    )

                    setClipboardText(json.toString(4))

                    DisplayMessage.showMessage("Save copied to clipboard")
                }

                initPopUpUI(dialog, 360f, 375f)
            }

            initialDialog.addButton("Load Save") { _ ->
                //val clipboardContents = getClipboardJson()

                val dialog = PopUpUIDialog("Load Save", addConfirmButton = true, addCancelButton = true)
                dialog.confirmButtonName = "Load"
                dialog.confirmAndCancelAlignment = Alignment.MID

                //dialog.addParagraph("Clipboard contains: ")

                dialog.addButton("Flip All Values", dismissOnClick = false) { fields ->
                    dialog.toggleRefs.values.forEach { it.isChecked = !it.isChecked }
                }
                dialog.addPadding(dialog.buttonHeight)
                dialog.addToggle("Include Blueprints", true)
                dialog.addToggle("Include Hullmods", true)
                dialog.addToggle("Include Player", true)
                dialog.addToggle("Include Fleet", true)
                dialog.addToggle("Include Officers", true)
                dialog.addToggle("Include Reputation", true)
                dialog.addToggle("Include Cargo", true)
                dialog.addToggle("Include Credits", true)

                dialog.onConfirm { fields ->
                    val json = getClipboardJson()

                    if (json == null) {
                        DisplayMessage.showMessage("Failed to read json in clipboard\n")
                        return@onConfirm
                    }

                    val (compiled, missing) = compilePlayerSaveJson(json)

                    if (compiled.isEmpty()) {
                        reportMissingElementsIfAny(missing)
                        DisplayMessage.showMessage("Could not find contents of save in clipboard. Are you sure you copied a valid save?", Color.RED)
                        return@onConfirm
                    }

                    loadPlayerCompiledSave(
                        compiled,
                        handleCargo = fields["Include Cargo"] as Boolean,
                        handleRelations = fields["Include Reputation"] as Boolean,
                        handleKnownBlueprints = fields["Include Blueprints"] as Boolean,
                        handlePlayer = fields["Include Player"] as Boolean,
                        handleFleet = fields["Include Fleet"] as Boolean,
                        handleCredits = fields["Include Credits"] as Boolean,
                        handleKnownHullmods = fields["Include Hullmods"] as Boolean,
                        handleOfficers = fields["Include Officers"] as Boolean
                    )

                    DisplayMessage.showMessage("Save loaded from clipboard")

                    reportMissingElementsIfAny(missing)
                }

                initPopUpUI(dialog, 360f, 375f)
            }

            initPopUpUI(initialDialog, 300f, 110f)
        }
    }

    private fun handleCreateOfficer(event: InputEventAPI, ui: CampaignUIAPI) {
        if (!Global.getSettings().isDevMode) return
        if (FBMisc.isPopUpUIOpen()) return
        if (ReflectionMisc.getCodexDialog() != null) return
        if (ui.getActualCurrentTab() == CoreUITabId.FLEET || (ui.getActualCurrentTab() == null && ui.currentInteractionDialog == null)) {
            event.consume()

            var officerSkillCount = 0

            Global.getSettings().skillIds.forEach { skill ->
                val spec = Global.getSettings().getSkillSpec(skill)
                if (spec.isCombatOfficerSkill && !spec.isAdminSkill && !spec.isAdmiralSkill && !spec.isAptitudeEffect && !spec.isPermanent && !spec.hasTag("npc_only") && !spec.hasTag("deprecated"))
                    officerSkillCount += 1
            }

            val dialog = PopUpUIDialog("Add Officer to Fleet", addCancelButton = true, addConfirmButton = true)
            dialog.confirmButtonName = "Create"

            fun addClampedNumericField(
                dialog: PopUpUIDialog,
                fieldId: String,
                maxValue: Int
            ) {
                dialog.addTextField(fieldId) { fields ->
                    val rawValue = fields[fieldId] as String
                    val cleanedValue = rawValue.replace("\\D+".toRegex(), "")

                    if (cleanedValue.isEmpty()) {
                        dialog.textFieldRefs[fieldId]?.text = ""
                        return@addTextField
                    }

                    val numericValue = cleanedValue.toIntOrNull() ?: return@addTextField
                    val clampedValue = numericValue.coerceAtMost(maxValue)

                    if (clampedValue.toString() != rawValue) {
                        dialog.textFieldRefs[fieldId]?.text = clampedValue.toString()
                    }
                }
            }

            dialog.addParagraph("Max Level")
            addClampedNumericField(dialog, "MaxLevel", officerSkillCount)
            dialog.addParagraph("Max Elite Skills")
            addClampedNumericField(dialog, "MaxElite", officerSkillCount)

            dialog.addPadding(8f)
            dialog.addToggle("Max XP", default = true)
            dialog.addToggle("Max Skills Pick Per Level", default = true)

            dialog.addPadding(8f)
            dialog.addParagraph("Personality")


            var personality = "Steady"
            dialog.addRadioGroup(
                listOf("Timid", "Cautious", "Steady", "Aggressive", "Reckless"), personality
            ) { select ->
                personality = select
            }


            dialog.onConfirm { fields ->
                var maxLevel = (fields["MaxLevel"] as String).toIntOrNull()
                val maxElite = (fields["MaxElite"] as String).toIntOrNull()

                val playerFleet = Global.getSector().playerFleet.fleetData


                val person = OfficerManagerEvent.createOfficer(
                    Global.getSector().playerFaction, 1, SkillPickPreference.ANY,
                    false, null, false, false, -1, MathUtils.getRandom()
                )
                person.stats.skillsCopy.forEach { person.stats.setSkillLevel(it.skill.id, 0f) }
                person.stats.level = 0

                person.setPersonality(personality.lowercase());

                if (fields["Max Skills Pick Per Level"] as Boolean)
                    person.memoryWithoutUpdate.set("\$officerSkillPicksPerLevel", officerSkillCount)
                if (maxLevel != null)
                    person.memoryWithoutUpdate.set("\$officerMaxLevel", maxLevel)
                if (maxElite != null)
                    person.memoryWithoutUpdate.set("\$officerMaxEliteSkills", maxElite)

                playerFleet.addOfficer(person);

                val plugin = Global.getSettings().getPlugin("officerLevelUp") as? OfficerLevelupPlugin
                if (plugin != null && fields["Max XP"] as Boolean) {
                    if (maxLevel == null)
                        maxLevel = Misc.MAX_OFFICER_LEVEL.toInt()
                    playerFleet.getOfficerData(person).addXP(plugin.getXPForLevel(maxLevel));
                }
            }
            initPopUpUI(dialog, 500f, 348f)
        }
    }

    private fun handleMouseDownEvents(event: InputEventAPI, sector: SectorAPI, ui: CampaignUIAPI) {
        if (ReflectionMisc.isCodexOpen()) return//If codex is open, halt.

        val captainPicker = ReflectionMisc.getCaptainPickerDialog()
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
        if (!event.isShiftDown) return
        if (ReflectionMisc.isCodexOpen() || FBMisc.isPopUpUIOpen()) return
        event.consume()

        createDevModeDialog()
    }

    private fun handleCopyHotkey(event: InputEventAPI, sector: SectorAPI, ui: CampaignUIAPI) {
        if (FBMisc.isPopUpUIOpen()) return

        try {
            val codex = ReflectionMisc.getCodexDialog()
            when {
                codex != null -> handleCodexCopy(event, codex)
                ui.getActualCurrentTab() == CoreUITabId.FLEET -> handleFleetCopy(event, sector)
                ui.getActualCurrentTab() == CoreUITabId.REFIT -> handleRefitCopy(event)
                ui.currentInteractionDialog != null -> handleInteractionCopy(event, ui)
            }
        } catch (e: Exception) {
            DisplayMessage.showError("FleetBuilder hotkey failed", e)
        }
    }

    private fun handleCodexCopy(event: InputEventAPI, codex: CodexDialog) {
        FBMisc.codexEntryToClipboard(codex)
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
                    memberSettings.personSettings.handleXpAndPoints = false
                }
            )

            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(
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
        settings.includeIdleOfficers = false

        var fleetToCopy: FleetDataAPI? = null
        var uiShowsSubmarketFleet = false

        try {
            fleetToCopy = getViewedFleetInFleetPanel() ?: playerFleet
            if (fleetToCopy !== playerFleet)
                uiShowsSubmarketFleet = true

            val fleetGrid = ReflectionMisc.getFleetPanel()?.findChildWithMethod("removeItem") ?: return

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
            DisplayMessage.showError("FleetBuilder hotkey had an error", e)
        }
        if (fleetToCopy == null) {
            DisplayMessage.showError("FleetBuilder hotkey failed")
            return
        }

        val json = FleetSerialization.saveFleetToJson(fleetToCopy, settings)
        ClipboardUtil.setClipboardText(json.toString(4))
        DisplayMessage.showMessage("Copied ${if (settings.excludeMembersWithID.isEmpty()) "entire" else "visible"} ${if (uiShowsSubmarketFleet) "submarket fleet" else "fleet"} to clipboard")
        event.consume()
    }

    private fun handleRefitCopy(event: InputEventAPI) {

        val baseVariant = ReflectionMisc.getCurrentVariantInRefitTab() ?: return

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

        DisplayMessage.showMessage("Variant copied to clipboard")
        event.consume()
    }

    private fun handlePasteHotkey(event: InputEventAPI, ui: CampaignUIAPI, sector: SectorAPI) {
        if (ReflectionMisc.isCodexOpen() || FBMisc.isPopUpUIOpen()) return

        if (ui.getActualCurrentTab() == CoreUITabId.REFIT) {
            handleRefitPaste(event)
        } else if (Global.getSettings().isDevMode) {
            handleOtherPaste(event, sector, ui)
        }
    }

    private fun handleRefitPaste(event: InputEventAPI) {
        val json = ClipboardUtil.getClipboardJson() ?: return
        val (variant, missing) = json.optJSONObject("variant")?.let { variantJson ->
            VariantSerialization.getVariantFromJsonWithMissing(variantJson)//JSON is of a FleetMemberAPI
        } ?: VariantSerialization.getVariantFromJsonWithMissing(json)//JSON is of a ShipVariantAPI (also fallback)

        event.consume()
        if (missing.hullIds.isNotEmpty()) {
            DisplayMessage.showMessage(
                "Failed to import loadout. Could not find hullId ${json.optString("hullId", "")}",
                Color.YELLOW
            )
            return
        }


        val loadoutExists = doesLoadoutExist(variant)


        if (!loadoutExists) {
            val baseHullSpec = Global.getSettings().allShipHullSpecs.find { it.hullId == variant.hullSpec.getEffectiveHullId() }
                ?: return
            val loadoutBaseHullName = baseHullSpec.hullName
                ?: return

            val dialog = PopUpUIDialog("Import loadout", addCancelButton = true, addConfirmButton = true)
            dialog.confirmButtonName = "Import"
            dialog.confirmAndCancelAlignment = Alignment.MID

            //val selectorPanel = Global.getSettings().createCustom(250f, 250f, plugin)

            val shipPreviewWidth = 375f
            val popUpHeight = 520f

            dialog.addParagraph(
                loadoutBaseHullName,
                alignment = Alignment.MID,
                font = Fonts.ORBITRON_24AABOLD,
                highlights = arrayOf(Color.YELLOW),
                highlightWords = arrayOf(loadoutBaseHullName)
            )


            val tempPanel = Global.getSettings().createCustom(shipPreviewWidth, shipPreviewWidth - (dialog.x * 2) + AutofitSelector.descriptionHeight, null)
            val tempTMAPI = tempPanel.createUIElement(tempPanel.position.width, tempPanel.position.height, false)

            val selectorPanel = AutofitSelector.createAutofitSelector(
                variant as HullVariantSpec, paintjobSpec = AutofitSpec(variant, name = variant.displayName, description = "", spriteId = variant.hullSpec.spriteName),
                shipPreviewWidth - (dialog.x * 2)
            )
            tempTMAPI.addComponent(selectorPanel)
            AutofitPanel.makeTooltip(tempTMAPI, selectorPanel, variant)

            tempPanel.addUIElement(tempTMAPI).inTL(0f, 0f)

            dialog.addCustom(tempPanel)


            dialog.onConfirm { fields ->

                importShipLoadout(variant, missing)

                DisplayMessage.showMessage(
                    " Loadout imported for hull: $loadoutBaseHullName",
                    variant.hullSpec.hullId,
                    Misc.getHighlightColor()
                )
            }

            initPopUpUI(dialog, shipPreviewWidth, popUpHeight)

        } else {
            DisplayMessage.showMessage(
                "Loadout already exists, cannot import loadout with hull: ${variant.hullSpec.hullId}",
                variant.hullSpec.hullId,
                Misc.getHighlightColor()
            )
        }
    }

    private fun handleOtherPaste(event: InputEventAPI, sector: SectorAPI, ui: CampaignUIAPI) {
        val json = ClipboardUtil.getClipboardJson() ?: run {
            DisplayMessage.showMessage("No valid json in clipboard", Color.YELLOW)
            event.consume()
            return
        }

        if (ui.getActualCurrentTab() == CoreUITabId.FLEET) {
            fleetPaste(sector, json)
        } else if (ui.currentInteractionDialog == null &&// Handle campaign map paste (no dialog/menu showing)
            !ui.isShowingDialog &&
            !ui.isShowingMenu
        ) {
            campaignPaste(sector, json)
        }

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
            DisplayMessage.showMessage("Officer copied to clipboard")
            event.consume()
        } catch (e: Exception) {
            DisplayMessage.showError("FleetBuilder hotkey failed", e)
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
                    DisplayMessage.showMessage("Officer copied to clipboard")
                } else {
                    val json = MemberSerialization.saveMemberToJson(mouseOverMember)
                    ClipboardUtil.setClipboardText(json.toString(4))
                    DisplayMessage.showMessage("Fleet member copied to clipboard")
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
                                DisplayMessage.showMessage("Cannot remove last ship in fleet", Color.YELLOW)
                            else
                                CommanderShuttle.removePlayerShuttle()
                        }

                        ModSettings.unassignPlayer -> {
                            CommanderShuttle.addPlayerShuttle()
                        }

                        else -> {
                            DisplayMessage.showMessage(
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
            DisplayMessage.showError("FleetBuilder hotkey failed", e)
        }
    }

    private fun handleRefitMouseEvents(event: InputEventAPI) {
        try {
            val refitTab = ReflectionMisc.getRefitTab() ?: return
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
            DisplayMessage.showMessage("Officer copied to clipboard")
            event.consume()
        } catch (e: Exception) {
            DisplayMessage.showError("FleetBuilder hotkey failed", e)
        }
    }

    private fun handleRefitRemoveHullMod(event: InputEventAPI) {
        try {
            val refitTab = ReflectionMisc.getRefitTab() ?: return
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

                    val variant = ReflectionMisc.getCurrentVariantInRefitTab()
                    if (variant != null) {
                        if (variant.hullSpec.builtInMods.contains(hullModID.id)) {//Built in SMod?
                            if (variant.sModdedBuiltIns.contains(hullModID.id)) {
                                variant.completelyRemoveMod(hullModID.id)
                                refitPanel.invoke("syncWithCurrentVariant")

                                DisplayMessage.showMessage("Removed sModdedBuiltIn in with ID '${hullModID.id}'")
                            } else if (VariantLib.getAllDMods().contains(hullModID.id)) {//Built in DMod?
                                variant.hullMods.remove(hullModID.id)
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

    override fun processCampaignInputPreFleetControl(events: MutableList<InputEventAPI>) = Unit

    override fun processCampaignInputPostCore(events: MutableList<InputEventAPI>) = Unit
}