package fleetBuilder.features.hotkeyHandler

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.ModSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.core.makeSaveRemovable.RemoveFromSave.removeModThings
import fleetBuilder.core.shipDirectory.ShipDirectoryService
import fleetBuilder.features.autofit.ui.AutofitPanel
import fleetBuilder.features.autofit.ui.AutofitSelector
import fleetBuilder.features.autofit.ui.AutofitSpec
import fleetBuilder.otherMods.starficz.addTooltip
import fleetBuilder.otherMods.starficz.height
import fleetBuilder.otherMods.starficz.onClick
import fleetBuilder.otherMods.starficz.width
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.MissingElementsExtended
import fleetBuilder.serialization.PlayerSaveUtils
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.serialization.fleet.FleetSettings
import fleetBuilder.serialization.reportMissingElementsIfAny
import fleetBuilder.ui.UIUtils
import fleetBuilder.ui.customPanel.common.DialogPanel
import fleetBuilder.ui.customPanel.common.ModalPanel
import fleetBuilder.util.*
import fleetBuilder.util.FBTxt.txtPlural
import fleetBuilder.util.api.FleetUtils
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.lib.ClipboardUtil
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.input.Keyboard
import org.magiclib.kotlin.autoSizeToText
import java.awt.Color


object HotkeyHandlerDialogs {

    fun createDevModeDialog() {
        val dialog = DialogPanel(FBTxt.txt("dev_options_title"))
        dialog.animation = ModalPanel.PanelAnimation.NONE

        dialog.show(width = 500f, height = 200f) { ui ->
            val toggleDev = ui.addToggle(FBTxt.txt("toggle_dev_mode"), Global.getSettings().isDevMode)
            toggleDev.setButtonPressedSound("FB_NONE")
            toggleDev.onClick {
                Global.getSettings().isDevMode = toggleDev.isChecked
                if (toggleDev.isChecked)
                    UIUtils.playSound("FB_ui_char_decrease_skill")
                else
                    UIUtils.playSound("FB_ui_char_increase_skill")
            }
            toggleDev.setShortcut(Keyboard.KEY_D, true)
            toggleDev.addTooltip(TooltipMakerAPI.TooltipLocation.RIGHT, 120f) { tooltip ->
                tooltip.addPara("Press D to toggle", 0f)
            }

            ui.addButton(
                FBTxt.txt("trigger_f8_reload"),
                null,
                160f, 24f, 4f
            ).onClick {
                Global.getSettings().modManager.enabledModPlugins.forEach {
                    it.onDevModeF8Reload()
                }
            }

            val testMessageTrigger = ui.addButton(
                "Trigger Test Message",
                null,
                160f, 24f, 0f
            )
            testMessageTrigger.position.inTL(0f, ui.height - testMessageTrigger.height - 8f)
            testMessageTrigger.onClick {
                DisplayMessage.showMessageCustom("Test Message!", Color.RED)

                /*val state = AppDriver.getInstance().currentState
                if (state is CampaignState) {
                    state.cmdCodex()
                    state.isHideUI
                    //CampaignGameManager().
                    CampaignEngine.getInstance().saveDirName
                    SaveGameData().saveDir
                }*/
            }

            if (Global.getCurrentState() == GameState.CAMPAIGN) {
                removeModButton(ui)
            }

            dialog.addCloseButton()
        }
    }

    private fun removeModButton(
        ui: TooltipMakerAPI
    ) {
        val removeModButton = ui.addButton("Remove Mod", null, 160f, 24f, 0f)

        removeModButton.position.inTL(ui.width - removeModButton.width + 12f, ui.height - removeModButton.height - 8f)
        removeModButton.onClick {
            val dialog = DialogPanel("Remove Mod")
            dialog.show(width = 800f, height = 800f) { ui ->
                ui.addPara("HERE BE DRAGONS!\nPlease note that these are very unsafe options and are very likely to cause issues.", Color.RED, 0f)
                ui.addSpacer(8f)
                val removeListeners = ui.addToggle("Remove all listeners")
                removeListeners.addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 400f) {
                    it.addPara("May crash the game", Color.RED, 0f)
                }
                val removeEntities = ui.addToggle("Remove all faction owned entities", isChecked = true)

                val tempPanel = Global.getSettings().createCustom(ui.width, ui.height - ui.heightSoFar, null)
                val tempTMAPI = tempPanel.createUIElement(ui.width, tempPanel.height, true)

                var yMargin = 16f
                Global.getSettings().modManager.enabledModsCopy.forEach {
                    tempTMAPI.addButton(it.name + " - " + it.id, null, ui.width - 8f, 32f, 4f).onClick {
                        val dialog = DialogPanel("Are you sure?")
                        dialog.show(500f, 200f) { ui ->
                            val removeModLabel = ui.addPara("Remove mod: ${it.name} - ${it.id}", 0f).autoSizeToText()
                            removeModLabel.position.inTMid(0f)
                            ui.addPara("Warning, this may brick your save file.", Color.RED, 0f).autoSizeToText().position.belowMid(removeModLabel as UIComponentAPI, 8f)
                            dialog.addActionButtons()
                        }
                        dialog.onConfirm {
                            runCatching {
                                removeModThings(
                                    listOf(it), removeListeners = removeListeners.isChecked,
                                    removeAllFactionOwnedEntities = removeEntities.isChecked, removeFleets = removeEntities.isChecked, removeMarkets = removeEntities.isChecked
                                )
                                DisplayMessage.showMessageCustom("Removed mod: ${it.name}")
                            }.onFailure { e ->
                                DisplayMessage.showError("Failed to remove mod", "Failed to remove mod:\n${e.message}")
                            }
                        }
                    }
                    yMargin += 40f
                }

                tempPanel.addUIElement(tempTMAPI)
                ui.addCustom(tempPanel, 0f)
            }
        }
    }


    fun pasteFleetIntoPlayerFleetDialog(
        data: DataFleet.ParsedFleetData,
        validatedData: DataFleet.ParsedFleetData,
    ) {
        val buttonHeight = 24f

        val playerFleet = Global.getSector()?.playerFleet?.fleetData ?: return


        val dialog = DialogPanel(FBTxt.txt("paste_fleet_into_player_fleet"))

        dialog.show(500f, 380f) { ui ->

            val memberCount = validatedData.members.size
            val officerCount = validatedData.members.count { it.personData != null }

            val text = if (officerCount > 0) {
                if (officerCount > 1)
                    txtPlural("pasted_fleet_members_officers", memberCount, memberCount, officerCount)
                else
                    txtPlural("pasted_fleet_members_officer", memberCount, memberCount, officerCount)
            } else {
                txtPlural("pasted_fleet_members_only", memberCount)
            }

            ui.addPara(text, 0f)

            val missingHullCount = validatedData.members.count {
                it.variantData == null || it.variantData.tags.contains(VariantUtils.getFBVariantErrorTag())
            }

            if (missingHullCount > 0)
                ui.addPara(txtPlural("fleet_contains_missing_hull", missingHullCount), 0f)

            ui.addSpacer(8f)

            var excludeShipsFromMissing = false
            var includeOfficers = true
            var incCommanderAsOfficer = true
            var setAggression = true
            var replacePlayerWithCommander = false
            var fulfillNeeds = true

            ui.addButton(FBTxt.txt("append_to_player_fleet"), null, ui.width, buttonHeight, 3f).onClick {
                val missing = MissingElements()
                val fleet = DataFleet.createCampaignFleetFromData(
                    data, false,
                    settings = FleetSettings().apply {
                        excludeMembersWithMissingHullSpec = excludeShipsFromMissing
                        memberSettings.includeOfficer = includeOfficers
                        includeCommanderAsOfficer = incCommanderAsOfficer
                    },
                    missing = missing
                )

                fleet.fleetData.membersListCopy.forEach { member ->
                    playerFleet.addFleetMember(member)

                    val captain = member.captain
                    if (!captain.isDefault && !captain.isAICore) {
                        playerFleet.addOfficer(captain)
                    }
                }

                reportMissingElementsIfAny(missing)

                if (fulfillNeeds)
                    FleetUtils.fulfillPlayerFleet()

                ReflectionMisc.updateFleetPanelContents()

                if (fleet.fleetData.membersListCopy.size > 1)
                    DisplayMessage.showMessage(FBTxt.txt("members_appended_into_fleet", fleet.fleetData.membersListCopy.size))
                else
                    DisplayMessage.showMessage(FBTxt.txt("member_appended_into_fleet", fleet.fleetData.membersListCopy.size))

                dialog.dismiss()
            }
            ui.addSpacer(24f)

            ui.addButton(FBTxt.txt("replace_player_fleet"), null, ui.width, buttonHeight, 3f).onClick {
                val settings = FleetSettings()
                settings.memberSettings.includeOfficer = includeOfficers
                settings.excludeMembersWithMissingHullSpec = excludeShipsFromMissing
                settings.includeCommanderAsOfficer = incCommanderAsOfficer
                settings.includeAggression = setAggression

                val replaceMissing = FleetUtils.replacePlayerFleetWith(
                    data,
                    (replacePlayerWithCommander && settings.includeCommanderAsOfficer),
                    settings
                )

                reportMissingElementsIfAny(replaceMissing)

                if (fulfillNeeds)
                    FleetUtils.fulfillPlayerFleet()

                ReflectionMisc.updateFleetPanelContents()

                DisplayMessage.showMessage(FBTxt.txt("player_fleet_replaced"))

                dialog.dismiss()
            }
            ui.addToggle(FBTxt.txt("set_aggression_doctrine"), setAggression).onClick { setAggression = !setAggression }
            ui.addToggle(FBTxt.txt("replace_player_with_commander"), replacePlayerWithCommander).onClick { replacePlayerWithCommander = !replacePlayerWithCommander }

            ui.addSpacer(48f)
            ui.addPara(FBTxt.txt("additional_settings"), 0f)
            ui.addToggle(FBTxt.txt("include_officers"), includeOfficers).onClick { includeOfficers = !includeOfficers }
            ui.addToggle(FBTxt.txt("include_commander_as_officer"), incCommanderAsOfficer).onClick { incCommanderAsOfficer = !incCommanderAsOfficer }
            ui.addToggle(FBTxt.txt("exclude_ships_from_missing_mods"), excludeShipsFromMissing).onClick { excludeShipsFromMissing = !excludeShipsFromMissing }
            ui.addToggle(FBTxt.txt("fulfill_fleet_needs"), fulfillNeeds).onClick { fulfillNeeds = !fulfillNeeds }


            dialog.addCloseButton()
        }

        /*
                                        val tempFleet = Global.getFactory().createEmptyFleet(Factions.INDEPENDENT, FleetTypes.TASK_FORCE, false)
                                        buildFleet(validatedFleet, tempFleet.fleetData, settings = FleetSerialization.FleetSettings())


                                        val fleetGridClass = FleetGrid::class.java
                                        //val fleetGrid = fleetBuilder.otherMods.MagicLib.ReflectionUtils.instantiate(fleetGridClass, 2, 2, 32f, 32f, 8f) as? UIComponentAPI

                                        val fleetGridConstructor = fleetBuilder.otherMods.starficz.ReflectionUtils.getConstructorsMatching(fleetGridClass, numOfParams = 6).getOrNull(0)
                                        val fleetGrid = fleetGridConstructor?.newInstance(2, 2, 32f, 32f, 8f, null) as? UIComponentAPI
                                        if (fleetGrid != null)
                                            dialog.addCustom(fleetGrid)*/

        /*val custom = Global.getSettings().createCustom(200f, 200f, null)
                        val tooltip = custom.createUIElement(200f, 200f, false)
                        tooltip.addShipList(3, 2, 160f, Misc.getBasePlayerColor(), tempFleet.fleetData.membersListCopy, 0f)
                        val panel = tooltip.invoke("getPanel") as? UIPanelAPI
                        val shipList = panel?.getChildrenCopy()?.getOrNull(0) as? S ?: return

                        //shipList.isShowDmods = false

                        //tempFleet.fleetData.membersListCopy.forEach { member ->
                        //    shipList.removeIconFor(member as FleetMember?)
                        //}
                        shipList.clear()

                        shipList.isShowDmods = true
                        shipList.setUseExpandedTooltip(true)
                        shipList.members.forEach { member ->
                            //member.
                        }
                        tempFleet.fleetData.membersListCopy.forEach { member ->
                            shipList.addIconFor(member as FleetMember)
                            val icon = shipList.invoke("getIconForMember", member)
                            icon

                        }

                        //shipList?.invoke("setUseBasicTooltip", true)
                        //val tooltipOptions = shipList?.invoke("getTooltipOptions")
                        //val shipListItems = shipList?.list?.items

                        //shipList?
                        //tooltip.isRecreateEveryFrame = true

                        custom.addUIElement(tooltip)
                        dialog.addCustom(custom)*/

    }

    fun spawnFleetInCampaignDialog(
        sector: SectorAPI,
        data: DataFleet.ParsedFleetData,
        validatedData: DataFleet.ParsedFleetData
    ) {
        val dialog = DialogPanel(FBTxt.txt("spawn_fleet_in_campaign"))

        dialog.show(500f, 350f) { ui ->

            val memberCount = validatedData.members.size
            val officerCount = validatedData.members.count { it.personData != null }

            val text = if (officerCount > 0) {
                if (officerCount > 1)
                    txtPlural("pasted_fleet_members_officers", memberCount, memberCount, officerCount)
                else
                    txtPlural("pasted_fleet_members_officer", memberCount, memberCount, officerCount)
            } else {
                txtPlural("pasted_fleet_members_only", memberCount)
            }

            ui.addPara(text, 0f)

            val missingHullCount = validatedData.members.count {
                it.variantData == null || it.variantData.tags.contains(VariantUtils.getFBVariantErrorTag())
            }

            if (missingHullCount > 0)
                ui.addPara(txtPlural("fleet_contains_missing_hull", missingHullCount), 0f)


            ui.addSpacer(8f)

            val buttonHeight = 24f
            val includeOfficers = ui.addToggle(FBTxt.txt("include_officers"), true)
            val includeCommanderAsCommander = ui.addToggle(FBTxt.txt("include_commander_as_commander"), true)
            val includeCommanderAsOfficer = ui.addToggle(FBTxt.txt("include_commander_as_officer"), true)
            val setAggressionDoctrine = ui.addToggle(FBTxt.txt("set_aggression_doctrine"), true)
            ui.addSpacer(buttonHeight / 2)
            val setFactionToPirates = ui.addToggle(FBTxt.txt("set_faction_to_pirate"), true)
            val fightToTheLast = ui.addToggle(FBTxt.txt("fight_to_the_last"), true)
            ui.addSpacer(buttonHeight / 2)
            val repairAndSetMaxCR = ui.addToggle(FBTxt.txt("repair_and_set_max_cr"), true)
            val excludeMissingShips = ui.addToggle(FBTxt.txt("exclude_ships_from_missing_mods"), true)

            dialog.addActionButtons(confirmText = FBTxt.txt("spawn_fleet"))

            dialog.onConfirm {
                val settings = FleetSettings()
                settings.includeAggression = setAggressionDoctrine.isChecked
                settings.memberSettings.includeOfficer = includeOfficers.isChecked
                settings.includeCommanderSetFlagship = includeCommanderAsCommander.isChecked
                settings.includeCommanderAsOfficer = includeCommanderAsOfficer.isChecked
                settings.excludeMembersWithMissingHullSpec = excludeMissingShips.isChecked
                val repairAndSetMaxCR = repairAndSetMaxCR.isChecked
                val setFactionToPirates = setFactionToPirates.isChecked

                val missing = MissingElements()
                val fleet = DataFleet.createCampaignFleetFromData(
                    if (setFactionToPirates) data.copy(factionID = Factions.PIRATES) else data,
                    true, settings = settings, missing = missing
                )

                reportMissingElementsIfAny(missing)

                if (repairAndSetMaxCR)
                    FleetUtils.fullFleetRepair(fleet.fleetData)

                sector.playerFleet.containingLocation.spawnFleet(sector.playerFleet, 0f, 0f, fleet)
                if (fightToTheLast.isChecked)
                    fleet.memoryWithoutUpdate[MemFlags.FLEET_FIGHT_TO_THE_LAST] = true

                dialog.closeDuration = 0f
                dialog.onExit {
                    Global.getSector().campaignUI.showInteractionDialog(fleet)
                }

                DisplayMessage.showMessage(FBTxt.txt("clipboard_fleet_added_to_campaign"))
            }
        }

    }

    fun createOfficerCreatorDialog() {
        val width = 500f
        val height = 348f

        val playerFleet = Global.getSector()?.playerFleet?.fleetData ?: return

        var officerSkillCount = 0

        Global.getSettings().skillIds.forEach { skill ->
            val spec = Global.getSettings().getSkillSpec(skill)
            if (spec.isCombatOfficerSkill && !spec.isAdminSkill && !spec.isAdmiralSkill && !spec.isAptitudeEffect && !spec.isPermanent && !spec.hasTag("npc_only") && !spec.hasTag("deprecated"))
                officerSkillCount += 1
        }


        val initialDialog = DialogPanel(headerTitle = FBTxt.txt("add_officer_to_fleet"))

        val buttonHeight = 24f

        initialDialog.show(width, height) { ui ->
            ui.addPara(FBTxt.txt("max_level"), 0f)
            val maxLevel = ui.addNumericTextField(ui.width, buttonHeight, font = Fonts.DEFAULT_SMALL, initialValue = null, maxValue = officerSkillCount)

            ui.addPara(FBTxt.txt("max_elite_skills"), 0f)
            val maxEliteSkills = ui.addNumericTextField(ui.width, buttonHeight, font = Fonts.DEFAULT_SMALL, initialValue = null, maxValue = officerSkillCount)

            ui.addSpacer(buttonHeight / 3)
            val maxXP = ui.addToggle(FBTxt.txt("max_xp"), isChecked = true)
            val maxSkillPicksPerLevel = ui.addToggle(FBTxt.txt("max_skill_picks_per_level"), isChecked = true)

            ui.addSpacer(8f)
            ui.addPara(FBTxt.txt("personality"), 0f)

            var currentPersonality = "steady"
            val internalPersonalities = listOf("timid", "cautious", "steady", "aggressive", "reckless")
            val externalPersonalities = internalPersonalities.map { FBTxt.txt(it) }

            val toggles = internalPersonalities.indices.map { index ->
                val internalName = internalPersonalities[index]
                val externalName = externalPersonalities.getOrNull(index) ?: "ERROR"

                ui.addToggle(name = externalName, data = internalName, isChecked = internalName == currentPersonality)
            }

            toggles.forEach { toggle ->
                toggle.onClick {
                    currentPersonality = toggle.customData as String
                    toggles.forEach { it.isChecked = it.customData == currentPersonality }
                }
            }

            initialDialog.addActionButtons(confirmText = FBTxt.txt("create"))


            initialDialog.onConfirm {
                var maxLevelValue = maxLevel.getText().toIntOrNull()
                val maxEliteSkillsValue = maxEliteSkills.getText().toIntOrNull()

                val person = OfficerManagerEvent.createOfficer(
                    Global.getSector().playerFaction, 1, OfficerManagerEvent.SkillPickPreference.ANY,
                    false, null, false, false, -1, MathUtils.getRandom()
                )
                person.stats.skillsCopy.forEach { person.stats.setSkillLevel(it.skill.id, 0f) }
                person.stats.level = 0

                person.setPersonality(currentPersonality.lowercase());

                if (maxSkillPicksPerLevel.isChecked)
                    person.memoryWithoutUpdate.set("\$officerSkillPicksPerLevel", officerSkillCount)
                if (maxLevelValue != null)
                    person.memoryWithoutUpdate.set("\$officerMaxLevel", maxLevelValue)
                if (maxEliteSkillsValue != null)
                    person.memoryWithoutUpdate.set("\$officerMaxEliteSkills", maxEliteSkillsValue)

                playerFleet.addOfficer(person);

                val plugin = Global.getSettings().getPlugin("officerLevelUp") as? OfficerLevelupPlugin
                if (plugin != null && maxXP.isChecked) {
                    if (maxLevelValue == null)
                        maxLevelValue = 99
                    playerFleet.getOfficerData(person).addXP(plugin.getXPForLevel(maxLevelValue));
                }
            }
        }
    }

    fun createImportLoadoutDialog(
        variant: ShipVariantAPI,
        missing: MissingElements
    ) {
        val baseHullSpec = variant.hullSpec.getEffectiveHull()
        val loadoutBaseHullName = baseHullSpec.hullName ?: return

        val dialog = DialogPanel(headerTitle = FBTxt.txt("import_loadout_title"))

        dialog.show(375f, 490f) { ui ->

            ui.setParaFont(Fonts.ORBITRON_24AABOLD)
            ui.addPara(
                loadoutBaseHullName,
                0f,
                arrayOf(Color.YELLOW),
                *arrayOf(loadoutBaseHullName)
            ).setAlignment(Alignment.MID)

            val tempPanel = Global.getSettings().createCustom(ui.width, ui.height, null)
            val tempTMAPI = tempPanel.createUIElement(tempPanel.width, tempPanel.height, false)

            val selectorPanel = AutofitSelector.createAutofitSelector(
                autofitSpec = AutofitSpec(variant, null),
                ui.width,
                addDescription = false,
                centerTitle = true
            )

            ui.addComponent(selectorPanel)
            selectorPanel.position.inTL(0f, dialog.getYTooltipPadding() - 14f)

            AutofitPanel.makeTooltip(selectorPanel, variant)

            tempPanel.addUIElement(tempTMAPI).inTL(0f, 0f)
            ui.addCustom(tempPanel, 0f)

            dialog.addActionButtons(confirmText = FBTxt.txt("import"), alignment = Alignment.MID)

            dialog.confirmButton?.addTooltip(TooltipMakerAPI.TooltipLocation.ABOVE, 600f) { tooltip ->
                tooltip.addPara(
                    FBTxt.txt(
                        "import_loadout_tooltip",
                        variant.hullSpec.getEffectiveHull().hullName,
                        ShipDirectoryService.getShipDirectoryWithPrefix(ModSettings.defaultPrefix)?.name,
                        ModSettings.defaultPrefix
                    ),
                    0f
                )
            }
        }

        dialog.onConfirm {
            ShipDirectoryService.importShipLoadout(ModSettings.defaultPrefix, variant, missing)

            DisplayMessage.showMessage(
                FBTxt.txt("loadout_imported_for_hull", loadoutBaseHullName),
                variant.hullSpec.hullId,
                Misc.getHighlightColor()
            )
        }
    }

    // Top-level declaration — OK
    enum class SaveOption(val displayName: String, val defaultChecked: Boolean, val tooltip: String = "") {
        BLUEPRINTS(FBTxt.txt("include_blueprints"), true),
        HULLMODS(FBTxt.txt("include_hullmods"), true),
        PLAYER(FBTxt.txt("include_player"), true, FBTxt.txt("include_player_tooltip")),
        REPUTATION(FBTxt.txt("include_reputation"), true),
        FLEET(FBTxt.txt("include_fleet"), true),
        OFFICERS(FBTxt.txt("include_officers"), true),
        CARGO(FBTxt.txt("include_cargo"), true),
        CREDITS(FBTxt.txt("include_credits"), true),
        ABILITYBAR(FBTxt.txt("include_abilitybar"), true, FBTxt.txt("include_abilitybar_tooltip"));
    }


    fun createSaveTransferDialog() {
        val dialog = DialogPanel(headerTitle = FBTxt.txt("save_transfer"))

        dialog.show(300f, 384f) { ui ->

            val buttonHeight = 24f

            // Map option → checkbox
            val checkboxes = mutableMapOf<SaveOption, ButtonAPI>()

            fun addToggle(option: SaveOption): ButtonAPI {
                val checkbox = ui.addCheckbox(
                    ui.computeStringWidth(option.displayName) + buttonHeight + 4f,
                    buttonHeight,
                    option.displayName,
                    null,
                    ButtonAPI.UICheckboxSize.SMALL,
                    0f
                )

                if (option.tooltip.isNotEmpty()) {
                    checkbox.addTooltip(TooltipMakerAPI.TooltipLocation.ABOVE, 450f) { tooltip ->
                        tooltip.addPara(option.tooltip, 0f)
                    }
                }

                checkbox.isChecked = option.defaultChecked
                checkboxes[option] = checkbox
                return checkbox
            }

            fun isEnabled(option: SaveOption): Boolean =
                checkboxes[option]?.isChecked ?: false

            val toggles = mutableListOf<ButtonAPI>()
            ui.addButton(FBTxt.txt("flip_all_values"), null, ui.width, buttonHeight, 0f).onClick {
                toggles.forEach { it.isChecked = !it.isChecked }
            }
            ui.addSpacer(buttonHeight / 4f)

            SaveOption.entries.forEach { option ->
                toggles.add(addToggle(option))
            }

            ui.addSpacer(buttonHeight)

            ui.addButton(FBTxt.txt("copy_save_to_clipboard"), null, ui.width, buttonHeight, 3f).onClick {
                val json = PlayerSaveUtils.createSaveJson(
                    handleCargo = isEnabled(SaveOption.CARGO),
                    handleRelations = isEnabled(SaveOption.REPUTATION),
                    handleKnownBlueprints = isEnabled(SaveOption.BLUEPRINTS),
                    handlePlayer = isEnabled(SaveOption.PLAYER),
                    handleFleet = isEnabled(SaveOption.FLEET),
                    handleCredits = isEnabled(SaveOption.CREDITS),
                    handleKnownHullmods = isEnabled(SaveOption.HULLMODS),
                    handleOfficers = isEnabled(SaveOption.OFFICERS),
                    handleAbilityBar = isEnabled(SaveOption.ABILITYBAR)
                )

                ClipboardUtil.setClipboardText(json)
                DisplayMessage.showMessage(FBTxt.txt("save_copied_to_clipboard"))

                dialog.dismiss()
            }

            ui.addButton(FBTxt.txt("load_save_from_clipboard"), null, ui.width, buttonHeight, 3f).onClick {

                val json = ClipboardUtil.getClipboardJson() ?: ClipboardUtil.getClipboardTextSafe()

                val missing = MissingElementsExtended()
                val compiled = PlayerSaveUtils.compileSaveAny(json, missing)

                if (compiled.isEmpty()) {
                    reportMissingElementsIfAny(missing)
                    DisplayMessage.showMessage(
                        FBTxt.txt("failed_to_find_save_in_clipboard"),
                        Color.YELLOW
                    )
                    return@onClick
                }

                PlayerSaveUtils.loadCompiledSave(
                    compiled,
                    handleCargo = isEnabled(SaveOption.CARGO),
                    handleRelations = isEnabled(SaveOption.REPUTATION),
                    handleKnownBlueprints = isEnabled(SaveOption.BLUEPRINTS),
                    handlePlayer = isEnabled(SaveOption.PLAYER),
                    handleFleet = isEnabled(SaveOption.FLEET),
                    handleCredits = isEnabled(SaveOption.CREDITS),
                    handleKnownHullmods = isEnabled(SaveOption.HULLMODS),
                    handleOfficers = isEnabled(SaveOption.OFFICERS),
                    handleAbilityBar = isEnabled(SaveOption.ABILITYBAR)
                )

                DisplayMessage.showMessage(FBTxt.txt("save_loaded_from_clipboard"))
                reportMissingElementsIfAny(missing)

                dialog.dismiss()
            }

            dialog.addCloseButton()
        }
    }
}