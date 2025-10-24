package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.SkillPickPreference
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.Fonts
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.FBTxt
import fleetBuilder.config.ModSettings
import fleetBuilder.persistence.fleet.DataFleet
import fleetBuilder.persistence.fleet.DataFleet.createCampaignFleetFromData
import fleetBuilder.persistence.fleet.FleetSettings
import fleetBuilder.ui.CargoAutoManageUIPlugin
import fleetBuilder.ui.autofit.AutofitPanel
import fleetBuilder.ui.autofit.AutofitSelector
import fleetBuilder.ui.autofit.AutofitSpec
import fleetBuilder.ui.popUpUI.BasePopUpUI
import fleetBuilder.ui.popUpUI.old.PopUpUIDialog
import fleetBuilder.util.lib.ClipboardUtil.getClipboardJson
import fleetBuilder.util.lib.ClipboardUtil.setClipboardText
import fleetBuilder.util.DialogUtil.initPopUpUI
import fleetBuilder.util.DisplayMessage.showMessage
import fleetBuilder.variants.LoadoutManager
import fleetBuilder.variants.LoadoutManager.importShipLoadout
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib
import fleetBuilder.variants.reportMissingElementsIfAny
import org.histidine.chatter.ChatterDataManager
import org.histidine.chatter.combat.ChatterCombatPlugin
import org.lazywizard.lazylib.MathUtils
import starficz.addTooltip
import starficz.onClick
import starficz.width
import java.awt.Color


object Dialogs {
    fun pasteFleetIntoPlayerFleetDialog(
        data: DataFleet.ParsedFleetData,
        validatedData: DataFleet.ParsedFleetData,
    ) {
        val dialog = PopUpUIDialog("Paste Fleet into Player Fleet", addCloseButton = true)

        val memberCount = validatedData.members.size
        val officerCount = validatedData.members.count { it.personData != null }
        dialog.addParagraph(
            "Pasted fleet contains $memberCount member${if (memberCount != 1) "s" else ""}" +
                    if (officerCount > 0) " and $officerCount officer${if (officerCount != 1) "s" else ""}" else ""
        )

        val missingHullCount = validatedData.members.count { it.variantData == null || it.variantData.tags.contains(VariantLib.errorTag) }
        if (missingHullCount > 0)
            dialog.addParagraph("Fleet contains $missingHullCount hull${if (missingHullCount != 1) "s" else ""} from missing mods")

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







        dialog.addPadding(8f)

        dialog.addButton("Append to Player Fleet") { fields ->
            val playerFleet = Global.getSector().playerFleet.fleetData

            val missing = MissingElements()
            val fleet = createCampaignFleetFromData(
                data, false,
                settings = FleetSettings().apply {
                    excludeMembersWithMissingHullSpec = fields["Exclude Ships From Missing Mods"] as Boolean
                    memberSettings.includeOfficer = fields["Include Officers"] as Boolean
                    includeCommanderAsOfficer = fields["Include Commander as Officer"] as Boolean
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

            if (fields["Fulfill cargo, fuel, crew, and repair for fleet"] as Boolean)
                FBMisc.fulfillPlayerFleet()

            ReflectionMisc.updateFleetPanelContents()

            if (fleet.fleetData.membersListCopy.size > 1)
                DisplayMessage.showMessage(FBTxt.txt("members_appended_into_fleet", fleet.fleetData.membersListCopy.size))
            else
                DisplayMessage.showMessage(FBTxt.txt("member_appended_into_fleet", fleet.fleetData.membersListCopy.size))

        }
        dialog.addPadding(24f)

        dialog.addButton("Replace Player Fleet") { fields ->
            val settings = FleetSettings()
            settings.memberSettings.includeOfficer = fields["Include Officers"] as Boolean
            settings.excludeMembersWithMissingHullSpec = fields["Exclude Ships From Missing Mods"] as Boolean
            settings.includeCommanderAsOfficer = fields["Include Commander as Officer"] as Boolean
            settings.includeAggression = fields["Set Aggression Doctrine"] as Boolean

            val replaceMissing = FBMisc.replacePlayerFleetWith(
                data,
                (fields["Replace Player with Commander"] as Boolean && settings.includeCommanderAsOfficer),
                settings
            )

            reportMissingElementsIfAny(replaceMissing)

            if (fields["Fulfill cargo, fuel, crew, and repair for fleet"] as Boolean)
                FBMisc.fulfillPlayerFleet()

            ReflectionMisc.updateFleetPanelContents()

            DisplayMessage.showMessage(FBTxt.txt("player_fleet_replaced"))
        }
        dialog.addToggle("Set Aggression Doctrine", default = true)
        dialog.addToggle("Replace Player with Commander", default = false)

        dialog.addPadding(48f)
        dialog.addParagraph("Additional Settings:")
        dialog.addToggle("Include Officers", default = true)
        dialog.addToggle("Include Commander as Officer", default = true)
        dialog.addToggle("Exclude Ships From Missing Mods", default = false)
        dialog.addToggle("Fulfill cargo, fuel, crew, and repair for fleet", default = true)


        initPopUpUI(dialog, 500f, 380f)
    }

    fun openSubmarketCargoAutoManagerDialog(
        selectedSubmarket: SubmarketAPI,
        instantUp: Boolean = false
    ) {
        CargoAutoManageUIPlugin(selectedSubmarket, 1000f, 1000f, instantUp).getPanel()
    }

    fun createDevModeDialog() {
        val dialog = PopUpUIDialog("Dev Options", addCancelButton = false, addConfirmButton = false, addCloseButton = true)
        dialog.addToggle("Toggle Dev Mode", Global.getSettings().isDevMode)

        dialog.onExit { fields ->
            Global.getSettings().isDevMode = fields["Toggle Dev Mode"] as Boolean
        }
        initPopUpUI(dialog, 500f, 200f)
    }

    fun spawnFleetInCampaignDialog(
        sector: SectorAPI,
        data: DataFleet.ParsedFleetData,
        validatedData: DataFleet.ParsedFleetData,
        uiaaaaRemoveMe: CampaignUIAPI
    ) {
        val dialog = BasePopUpUI("Spawn Fleet in Campaign")

        val buttonHeight = 24f

        dialog.onCreateUI(500f, 350f) { ui ->

            val memberCount = validatedData.members.size
            val officerCount = validatedData.members.count { it.personData != null }
            ui.addPara(
                "Pasted fleet contains $memberCount member${if (memberCount != 1) "s" else ""}" +
                        if (officerCount > 0) " and $officerCount officer${if (officerCount != 1) "s" else ""}" else "",
                0f
            )
            val missingHullCount = validatedData.members.count { it.variantData == null || it.variantData.tags.contains(VariantLib.errorTag) }
            if (missingHullCount > 0)
                ui.addPara("Fleet contains $missingHullCount hull${if (missingHullCount != 1) "s" else ""} from missing mods", 0f)


            ui.addSpacer(8f)
            fun addToggle(name: String, isChecked: Boolean = false): ButtonAPI {
                val checkbox = ui.addCheckbox(
                    ui.computeStringWidth(name) + buttonHeight + 4f,
                    buttonHeight,
                    name,
                    null,
                    ButtonAPI.UICheckboxSize.SMALL,
                    0f,
                )
                checkbox.isChecked = isChecked

                return checkbox
            }

            val includeOfficers = addToggle("Include Officers", true)
            val includeCommanderAsCommander = addToggle("Include Commander as Commander", true)
            val includeCommanderAsOfficer = addToggle("Include Commander as Officer", true)
            val setAggressionDoctrine = addToggle("Set Aggression Doctrine", true)
            ui.addSpacer(buttonHeight / 2)
            val setFactionToPirates = addToggle("Set Faction to Pirate", true)
            val fightToTheLast = addToggle("Fight To The Last", true)
            ui.addSpacer(buttonHeight / 2)
            val repairAndSetMaxCR = addToggle("Repair and Set Max CR", true)
            val excludeMissingShips = addToggle("Exclude Ships From Missing Mods", true)

            dialog.setupConfirmCancelSection(confirmText = "Spawn Fleet")

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
                val fleet = createCampaignFleetFromData(
                    if (setFactionToPirates) data.copy(factionID = Factions.PIRATES) else data,
                    true, settings = settings, missing = missing
                )

                reportMissingElementsIfAny(missing)

                if (repairAndSetMaxCR)
                    FBMisc.fullFleetRepair(fleet.fleetData)

                sector.playerFleet.containingLocation.spawnFleet(sector.playerFleet, 0f, 0f, fleet)
                Global.getSector().campaignUI.showInteractionDialog(fleet)
                if (fightToTheLast.isChecked)
                    fleet.memoryWithoutUpdate[MemFlags.FLEET_FIGHT_TO_THE_LAST] = true

                showMessage("Fleet from clipboard added to campaign")
            }
        }

    }

    fun createOfficerCreatorDialog() {
        val width = 500f
        var height = 348f

        var officerSkillCount = 0

        Global.getSettings().skillIds.forEach { skill ->
            val spec = Global.getSettings().getSkillSpec(skill)
            if (spec.isCombatOfficerSkill && !spec.isAdminSkill && !spec.isAdmiralSkill && !spec.isAptitudeEffect && !spec.isPermanent && !spec.hasTag("npc_only") && !spec.hasTag("deprecated"))
                officerSkillCount += 1
        }

        val initialDialog = PopUpUIDialog("Add Officer to Fleet", addCancelButton = true, addConfirmButton = true)
        initialDialog.confirmButtonName = "Create"

        initialDialog.addParagraph("Max Level")
        initialDialog.addClampedNumericField("MaxLevel", officerSkillCount)
        initialDialog.addParagraph("Max Elite Skills")
        initialDialog.addClampedNumericField("MaxElite", officerSkillCount)

        initialDialog.addPadding(initialDialog.buttonHeight / 3)
        initialDialog.addToggle("Max XP", default = true)
        initialDialog.addToggle("Max Skill Picks Per Level", default = true)

        initialDialog.addPadding(8f)
        initialDialog.addParagraph("Personality")


        var personality = "Steady"
        initialDialog.addRadioGroup(
            listOf("Timid", "Cautious", "Steady", "Aggressive", "Reckless"), personality
        ) { select ->
            personality = select
        }

        var combatChatterCharID: String? = null

        if (Global.getSettings().modManager.isModEnabled("chatter")) {

            initialDialog.addPadding(initialDialog.buttonHeight / 3)
            initialDialog.addButton("Choose Combat Chatter Character", dismissOnClick = false) {
                val dialog = PopUpUIDialog(
                    "Choose Combat Chatter Character",
                    addCancelButton = true,
                    addConfirmButton = true
                )

                val characters = ChatterDataManager.CHARACTERS

                val dialogWidth = 500f
                val dialogHeight = 800f

                val panel = Global.getSettings().createCustom(
                    dialogWidth - dialog.x * 2,
                    dialogHeight - dialog.y * 2,
                    null
                )
                val inner = panel.createUIElement(
                    dialogWidth - dialog.x * 2,
                    dialogHeight - dialog.y * 2,
                    true
                )

                fun addRadioGroup(
                    parent: TooltipMakerAPI,
                    options: List<Pair<String, String>>, // (id, label)
                    defaultId: String = options.first().first,
                    onChanged: (String) -> Unit
                ) {
                    val checkboxes = mutableMapOf<String, ButtonAPI>()

                    for ((id, label) in options) {
                        val checkbox = parent.addCheckbox(
                            parent.computeStringWidth(label) + 28f,
                            dialog.buttonHeight,
                            label,
                            id,
                            ButtonAPI.UICheckboxSize.SMALL,
                            0f
                        )

                        checkbox.isChecked = id == defaultId
                        checkbox.setClickable(id != defaultId)
                        checkboxes[id] = checkbox

                        checkbox.onClick {
                            if (checkbox.isChecked) {
                                for ((_id, otherBox) in checkboxes) {
                                    val isSelected = _id == id
                                    otherBox.isChecked = isSelected
                                    otherBox.setClickable(!isSelected)
                                }
                                onChanged(id)
                            }
                        }
                    }
                }

                addRadioGroup(
                    inner,
                    characters
                        .sortedBy { it.name.lowercase() }
                        .map { it.id to it.name },
                    "none"
                ) { selectedId ->
                    combatChatterCharID = selectedId
                }

                panel.addUIElement(inner).inTL(0f, 0f)
                dialog.addCustom(panel)
                initPopUpUI(dialog, dialogWidth, dialogHeight)
            }
            height += initialDialog.buttonHeight * 3
        }


        initialDialog.onConfirm { fields ->
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

            if (fields["Max Skill Picks Per Level"] as Boolean)
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

            if (combatChatterCharID != null && combatChatterCharID != "none") {
                ChatterDataManager.saveCharacter(person, combatChatterCharID)
                val plugin = ChatterCombatPlugin.getInstance()
                plugin?.setCharacterForOfficer(person, combatChatterCharID)
            }
        }

        initPopUpUI(initialDialog, width, height)
    }

    fun createImportLoadoutDialog(
        variant: ShipVariantAPI,
        missing: MissingElements
    ) {
        val baseHullSpec = variant.hullSpec.getEffectiveHull()
        val loadoutBaseHullName = baseHullSpec.hullName
            ?: return

        val dialog = BasePopUpUI(headerTitle = "Import Loadout")

        dialog.onCreateUI(375f, 490f) { ui ->

            ui.setParaFont(Fonts.ORBITRON_24AABOLD)
            ui.addPara(
                loadoutBaseHullName,
                0f,
                arrayOf(Color.YELLOW),
                *arrayOf(loadoutBaseHullName)
            ).setAlignment(Alignment.MID)


            val height = dialog.panel.width - (dialog.x * 2)

            val tempPanel = Global.getSettings().createCustom(dialog.panel.width, height, null)
            val tempTMAPI = tempPanel.createUIElement(tempPanel.position.width, tempPanel.position.height, false)

            val selectorPanel = AutofitSelector.createAutofitSelector(
                autofitSpec = AutofitSpec(variant, null),
                height,
                addDescription = false,
                centerTitle = true
            )

            tempTMAPI.addComponent(selectorPanel)
            AutofitPanel.makeTooltip(selectorPanel, variant)

            tempPanel.addUIElement(tempTMAPI).inTL(0f, 0f)


            ui.addCustom(tempPanel, 0f)


            dialog.setupConfirmCancelSection(confirmText = "Import", alignment = Alignment.MID)

            dialog.confirmButton?.addTooltip(TooltipMakerAPI.TooltipLocation.ABOVE, 600f) { tooltip ->
                tooltip.addPara("This will import this loadout under the hull class ${variant.hullSpec.getEffectiveHull().hullName} within the ${LoadoutManager.getShipDirectoryWithPrefix(ModSettings.defaultPrefix)?.name} (${ModSettings.defaultPrefix}) directory", 0f)
            }
        }

        dialog.onConfirm {
            importShipLoadout(ModSettings.defaultPrefix, variant, missing)

            DisplayMessage.showMessage(
                " Loadout imported for hull: $loadoutBaseHullName",
                variant.hullSpec.hullId,
                Misc.getHighlightColor()
            )
        }
    }

    // Top-level declaration — OK
    enum class SaveOption(val displayName: String, val defaultChecked: Boolean) {
        BLUEPRINTS("Include Blueprints", true),
        HULLMODS("Include Hullmods", true),
        PLAYER("Include Player", true),
        FLEET("Include Fleet", true),
        OFFICERS("Include Officers", true),
        REPUTATION("Include Reputation", true),
        CARGO("Include Cargo", true),
        CREDITS("Include Credits", true);
    }


    fun createSaveTransferDialog() {
        val dialog = BasePopUpUI(headerTitle = "Save Transfer")

        dialog.onCreateUI(300f, 360f) { ui ->

            val buttonWidth = dialog.panel.position.width - (dialog.x * 2)
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
                checkbox.isChecked = option.defaultChecked
                checkboxes[option] = checkbox
                return checkbox
            }

            fun isEnabled(option: SaveOption): Boolean =
                checkboxes[option]?.isChecked ?: false

            val toggles = mutableListOf<ButtonAPI>()
            ui.addButton("Flip All Values", null, buttonWidth, buttonHeight, 0f).onClick {
                toggles.forEach { it.isChecked = !it.isChecked }
            }
            ui.addSpacer(buttonHeight / 4f)

            SaveOption.entries.forEach { option ->
                toggles.add(addToggle(option))
            }

            ui.addSpacer(buttonHeight)

            ui.addButton("Copy Save To Clipboard", null, buttonWidth, buttonHeight, 3f).onClick {
                val json = PlayerSaveUtil.createPlayerSaveJson(
                    handleCargo = isEnabled(SaveOption.CARGO),
                    handleRelations = isEnabled(SaveOption.REPUTATION),
                    handleKnownBlueprints = isEnabled(SaveOption.BLUEPRINTS),
                    handlePlayer = isEnabled(SaveOption.PLAYER),
                    handleFleet = isEnabled(SaveOption.FLEET),
                    handleCredits = isEnabled(SaveOption.CREDITS),
                    handleKnownHullmods = isEnabled(SaveOption.HULLMODS),
                    handleOfficers = isEnabled(SaveOption.OFFICERS)
                )

                setClipboardText(json.toString(4))
                DisplayMessage.showMessage("Save copied to clipboard")

                dialog.forceDismiss()
            }

            ui.addButton("Load Save From Clipboard", null, buttonWidth, buttonHeight, 3f).onClick {

                val json = getClipboardJson()

                if (json == null) {
                    DisplayMessage.showMessage("Failed to read json in clipboard", Color.YELLOW)
                    return@onClick
                }

                val (compiled, missing) = PlayerSaveUtil.compilePlayerSaveJson(json)

                if (compiled.isEmpty()) {
                    reportMissingElementsIfAny(missing)
                    DisplayMessage.showMessage(
                        "Could not find contents of save in clipboard. Are you sure you copied a valid save?",
                        Color.YELLOW
                    )
                    return@onClick
                }

                PlayerSaveUtil.loadPlayerCompiledSave(
                    compiled,
                    handleCargo = isEnabled(SaveOption.CARGO),
                    handleRelations = isEnabled(SaveOption.REPUTATION),
                    handleKnownBlueprints = isEnabled(SaveOption.BLUEPRINTS),
                    handlePlayer = isEnabled(SaveOption.PLAYER),
                    handleFleet = isEnabled(SaveOption.FLEET),
                    handleCredits = isEnabled(SaveOption.CREDITS),
                    handleKnownHullmods = isEnabled(SaveOption.HULLMODS),
                    handleOfficers = isEnabled(SaveOption.OFFICERS)
                )

                DisplayMessage.showMessage("Save loaded from clipboard")
                reportMissingElementsIfAny(missing)

                dialog.forceDismiss()
            }

            dialog.addCloseButton()
        }
    }
}