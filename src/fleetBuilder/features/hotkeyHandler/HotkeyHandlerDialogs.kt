package fleetBuilder.features.hotkeyHandler

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.Fonts
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.ModSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.core.shipDirectory.ShipDirectoryService
import fleetBuilder.features.autofit.ui.AutofitPanel
import fleetBuilder.features.autofit.ui.AutofitSelector
import fleetBuilder.features.autofit.ui.AutofitSpec
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.PlayerSaveUtils
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.serialization.fleet.FleetSettings
import fleetBuilder.serialization.reportMissingElementsIfAny
import fleetBuilder.ui.customPanel.common.BasePopUpPanel
import fleetBuilder.util.*
import fleetBuilder.util.api.FleetUtils
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.lib.ClipboardUtil
import org.lazywizard.lazylib.MathUtils
import starficz.addTooltip
import starficz.height
import starficz.onClick
import starficz.width
import java.awt.Color

object HotkeyHandlerDialogs {
    fun pasteFleetIntoPlayerFleetDialog(
        data: DataFleet.ParsedFleetData,
        validatedData: DataFleet.ParsedFleetData,
    ) {
        val buttonHeight = 24f

        val playerFleet = Global.getSector()?.playerFleet?.fleetData ?: return


        val dialog = BasePopUpPanel(FBTxt.txt("paste_fleet_into_player_fleet"))

        dialog.onCreateUI(500f, 380f) { ui ->

            val memberCount = validatedData.members.size
            val officerCount = validatedData.members.count { it.personData != null }
            ui.addPara(
                "Pasted fleet contains $memberCount member${if (memberCount != 1) "s" else ""}" +
                        if (officerCount > 0) " and $officerCount officer${if (officerCount != 1) "s" else ""}" else "", 0f
            )

            val missingHullCount = validatedData.members.count { it.variantData == null || it.variantData.tags.contains(VariantUtils.getFBVariantErrorTag()) }
            if (missingHullCount > 0)
                ui.addPara("Fleet contains $missingHullCount hull${if (missingHullCount != 1) "s" else ""} from missing mods", 0f)


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

                dialog.forceDismiss()
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

                dialog.forceDismiss()
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
                                        //val fleetGrid = MagicLib.ReflectionUtils.instantiate(fleetGridClass, 2, 2, 32f, 32f, 8f) as? UIComponentAPI

                                        val fleetGridConstructor = starficz.ReflectionUtils.getConstructorsMatching(fleetGridClass, numOfParams = 6).getOrNull(0)
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

    fun createDevModeDialog() {
        val dialog = BasePopUpPanel("Dev Options")

        dialog.onCreateUI(500f, 200f) { ui ->
            val toggleDev = ui.addToggle("Toggle Dev Mode", Global.getSettings().isDevMode)
            toggleDev.onClick {
                Global.getSettings().isDevMode = toggleDev.isChecked
            }
            val triggerTestMessage = ui.addButton(
                "Trigger Test Message",
                null,
                160f, 24f, 8f
            )
            triggerTestMessage.onClick {
                DisplayMessage.showMessageCustom("Test Message!", Color.RED)
            }

            dialog.addCloseButton()
        }
    }

    fun spawnFleetInCampaignDialog(
        sector: SectorAPI,
        data: DataFleet.ParsedFleetData,
        validatedData: DataFleet.ParsedFleetData
    ) {
        val dialog = BasePopUpPanel(FBTxt.txt("spawn_fleet_in_campaign"))

        dialog.onCreateUI(500f, 350f) { ui ->

            val memberCount = validatedData.members.size
            val officerCount = validatedData.members.count { it.personData != null }
            ui.addPara(
                "Pasted fleet contains $memberCount member${if (memberCount != 1) "s" else ""}" +
                        if (officerCount > 0) " and $officerCount officer${if (officerCount != 1) "s" else ""}" else "",
                0f
            )
            val missingHullCount = validatedData.members.count { it.variantData == null || it.variantData.tags.contains(VariantUtils.getFBVariantErrorTag()) }
            if (missingHullCount > 0)
                ui.addPara("Fleet contains $missingHullCount hull${if (missingHullCount != 1) "s" else ""} from missing mods", 0f)


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

            dialog.setupConfirmCancelSection(confirmText = FBTxt.txt("spawn_fleet"))

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
                Global.getSector().campaignUI.showInteractionDialog(fleet)
                if (fightToTheLast.isChecked)
                    fleet.memoryWithoutUpdate[MemFlags.FLEET_FIGHT_TO_THE_LAST] = true

                DisplayMessage.showMessage("Fleet from clipboard added to campaign")
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


        val initialDialog = BasePopUpPanel(headerTitle = "Add Officer to Fleet")

        val buttonHeight = 24f

        initialDialog.onCreateUI(width, height) { ui ->
            ui.addPara("Max Level", 0f)
            val maxLevel = ui.addNumericTextField(ui.width, buttonHeight, font = Fonts.DEFAULT_SMALL, initialValue = null, maxValue = officerSkillCount)
            ui.addPara("Max Elite Skills", 0f)
            val maxEliteSkills = ui.addNumericTextField(ui.width, buttonHeight, font = Fonts.DEFAULT_SMALL, initialValue = null, maxValue = officerSkillCount)

            ui.addSpacer(buttonHeight / 3)
            val maxXP = ui.addToggle("Max XP", isChecked = true)
            val maxSkillPicksPerLevel = ui.addToggle("Max Skill Picks Per Level", isChecked = true)

            ui.addSpacer(8f)
            ui.addPara("Personality", 0f)

            var currentPersonality = "steady"
            val internalPersonalities = listOf("timid", "cautious", "steady", "aggressive", "reckless")
            val externalPersonalities = internalPersonalities.map { FBTxt.txt(it) }
            val toggles = internalPersonalities.indices.map { index ->
                val internalName = internalPersonalities[index]
                val externalName = externalPersonalities.getOrNull(index) ?: "ERROR"

                ui.addToggle(
                    name = externalName,
                    data = internalName,
                    isChecked = internalName == currentPersonality
                )
            }

            toggles.forEach { toggle ->
                toggle.onClick {
                    currentPersonality = toggle.customData as String
                    toggles.forEach { it.isChecked = it.customData == currentPersonality }
                }
            }

            initialDialog.setupConfirmCancelSection(confirmText = "Create")

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
        val loadoutBaseHullName = baseHullSpec.hullName
            ?: return

        val dialog = BasePopUpPanel(headerTitle = "Import Loadout")

        dialog.onCreateUI(375f, 490f) { ui ->

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

            //tempTMAPI.addComponent(selectorPanel)
            AutofitPanel.makeTooltip(selectorPanel, variant)

            tempPanel.addUIElement(tempTMAPI).inTL(0f, 0f)


            ui.addCustom(tempPanel, 0f)


            dialog.setupConfirmCancelSection(confirmText = "Import", alignment = Alignment.MID)

            dialog.confirmButton?.addTooltip(TooltipMakerAPI.TooltipLocation.ABOVE, 600f) { tooltip ->
                tooltip.addPara("This will import this loadout under the hull class ${variant.hullSpec.getEffectiveHull().hullName} within the ${ShipDirectoryService.getShipDirectoryWithPrefix(ModSettings.defaultPrefix)?.name} (${ModSettings.defaultPrefix}) directory", 0f)
            }
        }

        dialog.onConfirm {
            ShipDirectoryService.importShipLoadout(ModSettings.defaultPrefix, variant, missing)

            DisplayMessage.showMessage(
                " Loadout imported for hull: $loadoutBaseHullName",
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
        CREDITS(FBTxt.txt("include_cargo"), true),
        ABILITYBAR(FBTxt.txt("include_abilitybar"), true, FBTxt.txt("include_abilitybar_tooltip"));
    }


    fun createSaveTransferDialog() {
        val dialog = BasePopUpPanel(headerTitle = FBTxt.txt("save_transfer"))

        dialog.onCreateUI(300f, 384f) { ui ->

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
                val json = PlayerSaveUtils.createPlayerSaveJson(
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

                ClipboardUtil.setClipboardText(json.toString(4))
                DisplayMessage.showMessage(FBTxt.txt("save_copied_to_clipboard"))

                dialog.forceDismiss()
            }

            ui.addButton(FBTxt.txt("load_save_from_clipboard"), null, ui.width, buttonHeight, 3f).onClick {

                val json = ClipboardUtil.getClipboardJson()

                if (json == null) {
                    DisplayMessage.showMessage(FBTxt.txt("failed_to_read_json_in_clipboard"), Color.YELLOW)
                    return@onClick
                }

                val (compiled, missing) = PlayerSaveUtils.compilePlayerSaveJson(json)

                if (compiled.isEmpty()) {
                    reportMissingElementsIfAny(missing)
                    DisplayMessage.showMessage(
                        FBTxt.txt("failed_to_find_save_in_clipboard"),
                        Color.YELLOW
                    )
                    return@onClick
                }

                PlayerSaveUtils.loadPlayerCompiledSave(
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

                dialog.forceDismiss()
            }

            dialog.addCloseButton()
        }
    }
}