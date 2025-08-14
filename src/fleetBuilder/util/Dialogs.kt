package fleetBuilder.util

import com.fs.starfarer.api.Global
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
import fleetBuilder.persistence.fleet.FleetSerialization
import fleetBuilder.ui.CargoAutoManageUIPlugin
import fleetBuilder.ui.autofit.AutofitPanel
import fleetBuilder.ui.autofit.AutofitSelector
import fleetBuilder.ui.autofit.AutofitSpec
import fleetBuilder.ui.popUpUI.PopUpUIDialog
import fleetBuilder.util.ClipboardUtil.getClipboardJson
import fleetBuilder.util.ClipboardUtil.setClipboardText
import fleetBuilder.util.DialogUtil.initPopUpUI
import fleetBuilder.util.DisplayMessage.showMessage
import fleetBuilder.variants.LoadoutManager.importShipLoadout
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib
import fleetBuilder.variants.reportMissingElementsIfAny
import org.histidine.chatter.ChatterDataManager
import org.histidine.chatter.combat.ChatterCombatPlugin
import org.lazywizard.lazylib.MathUtils
import starficz.onClick
import java.awt.Color


object Dialogs {
    fun pasteFleetIntoPlayerFleetDialog(
        data: FleetSerialization.ParsedFleetData,
        validatedFleet: FleetSerialization.ParsedFleetData,
    ) {
        val dialog = PopUpUIDialog("Paste Fleet into Player Fleet", addCloseButton = true)

        val memberCount = validatedFleet.members.size
        val officerCount = validatedFleet.members.count { it.personData != null }
        dialog.addParagraph(
            "Pasted fleet contains $memberCount member${if (memberCount != 1) "s" else ""}" +
                    if (officerCount > 0) " and $officerCount officer${if (officerCount != 1) "s" else ""}" else ""
        )

        val missingHullCount = validatedFleet.members.count { it.variantData == null || it.variantData.tags.contains(VariantLib.errorTag) }
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

            val addMissing = MissingElements()
            val fleet = FleetSerialization.createCampaignFleetFromData(
                data, false,
                settings = FleetSerialization.FleetSettings().apply {
                    excludeMembersWithMissingHullSpec = fields["Exclude Ships From Missing Mods"] as Boolean
                    memberSettings.includeOfficer = fields["Include Officers"] as Boolean
                    includeCommanderAsOfficer = fields["Include Commander as Officer"] as Boolean
                },
                missing = addMissing
            )

            fleet.fleetData.membersListCopy.forEach { member ->
                playerFleet.addFleetMember(member)

                val captain = member.captain
                if (!captain.isDefault && !captain.isAICore) {
                    playerFleet.addOfficer(captain)
                }
            }

            reportMissingElementsIfAny(addMissing)

            if (fields["Fulfill cargo, fuel, crew, and repair for fleet"] as Boolean)
                FBMisc.fulfillPlayerFleet()

            ReflectionMisc.updateFleetPanelContents()
        }
        dialog.addPadding(24f)

        dialog.addButton("Replace Player Fleet") { fields ->
            val settings = FleetSerialization.FleetSettings()
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
        data: FleetSerialization.ParsedFleetData,
        validatedData: FleetSerialization.ParsedFleetData
    ) {
        val dialog = PopUpUIDialog("Spawn Fleet in Campaign", addConfirmButton = true, addCancelButton = true)
        dialog.confirmButtonName = "Spawn Fleet"

        val memberCount = validatedData.members.size
        val officerCount = validatedData.members.count { it.personData != null }
        dialog.addParagraph(
            "Pasted fleet contains $memberCount member${if (memberCount != 1) "s" else ""}" +
                    if (officerCount > 0) " and $officerCount officer${if (officerCount != 1) "s" else ""}" else ""
        )

        val missingHullCount = validatedData.members.count { it.variantData == null || it.variantData.tags.contains(VariantLib.errorTag) }
        if (missingHullCount > 0)
            dialog.addParagraph("Fleet contains $missingHullCount hull${if (missingHullCount != 1) "s" else ""} from missing mods")


        dialog.addPadding(8f)

        dialog.addToggle("Include Officers", default = true)
        dialog.addToggle("Include Commander as Commander", default = true)
        dialog.addToggle("Include Commander as Officer", default = true)
        dialog.addToggle("Set Aggression Doctrine", default = true)
        dialog.addPadding(dialog.buttonHeight / 2)
        dialog.addToggle("Set Faction to Pirate", default = true)
        dialog.addToggle("Fight To The Last", default = true)
        dialog.addPadding(dialog.buttonHeight / 2)
        dialog.addToggle("Repair and Set Max CR", default = true)
        dialog.addToggle("Exclude Ships From Missing Mods", default = true)

        dialog.onConfirm { fields ->

            val settings = FleetSerialization.FleetSettings()
            settings.includeAggression = fields["Set Aggression Doctrine"] as Boolean
            settings.memberSettings.includeOfficer = fields["Include Officers"] as Boolean
            settings.includeCommanderSetFlagship = fields["Include Commander as Commander"] as Boolean
            settings.includeCommanderAsOfficer = fields["Include Commander as Officer"] as Boolean
            settings.excludeMembersWithMissingHullSpec = fields["Exclude Ships From Missing Mods"] as Boolean
            val repairAndSetMaxCR = fields["Repair and Set Max CR"] as Boolean
            val setFactionToPirates = (fields["Set Faction to Pirate"] as Boolean)
            val missing = MissingElements()
            missing.gameMods.addAll(data.gameMods)

            val fleet = FleetSerialization.createCampaignFleetFromData(
                if (setFactionToPirates) data.copy(factionID = Factions.PIRATES) else data,
                true, settings = settings, missing = missing
            )

            reportMissingElementsIfAny(missing)

            if (repairAndSetMaxCR)
                FBMisc.fullFleetRepair(fleet.fleetData)

            sector.playerFleet.containingLocation.spawnFleet(sector.playerFleet, 0f, 0f, fleet)
            Global.getSector().campaignUI.showInteractionDialog(fleet)
            if (fields["Fight To The Last"] as Boolean)
                fleet.memoryWithoutUpdate[MemFlags.FLEET_FIGHT_TO_THE_LAST] = true

            showMessage("Fleet from clipboard added to campaign")
        }

        initPopUpUI(dialog, 500f, 350f)
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
        val baseHullSpec = Global.getSettings().allShipHullSpecs.find { it.hullId == variant.hullSpec.getEffectiveHullId() }
            ?: return
        val loadoutBaseHullName = baseHullSpec.hullName
            ?: return

        val dialog = PopUpUIDialog("Import loadout", addCancelButton = true, addConfirmButton = true)
        dialog.confirmButtonName = "Import"
        dialog.confirmAndCancelAlignment = Alignment.MID

        //val selectorPanel = Global.getSettings().createCustom(250f, 250f, plugin)

        val shipPreviewWidth = 375f
        val popUpHeight = 490f

        dialog.addParagraph(
            loadoutBaseHullName,
            alignment = Alignment.MID,
            font = Fonts.ORBITRON_24AABOLD,
            highlights = arrayOf(Color.YELLOW),
            highlightWords = arrayOf(loadoutBaseHullName)
        )

        val height = shipPreviewWidth - (dialog.x * 2)

        val tempPanel = Global.getSettings().createCustom(shipPreviewWidth, height, null)
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

        dialog.addCustom(tempPanel)


        dialog.onConfirm {

            importShipLoadout(variant, missing)

            DisplayMessage.showMessage(
                " Loadout imported for hull: $loadoutBaseHullName",
                variant.hullSpec.hullId,
                Misc.getHighlightColor()
            )
        }

        initPopUpUI(dialog, shipPreviewWidth, popUpHeight)
    }

    fun createSaveTransferDialog() {
        val initialDialog = PopUpUIDialog("Save Transfer", addCancelButton = false, addConfirmButton = false, addCloseButton = true)
        initialDialog.addButton("Flip All Values", dismissOnClick = false) {
            initialDialog.toggleRefs.values.forEach { it.isChecked = !it.isChecked }
        }
        initialDialog.addPadding(initialDialog.buttonHeight / 4f)
        initialDialog.addToggle("Include Blueprints", true)
        initialDialog.addToggle("Include Hullmods", true)
        initialDialog.addToggle("Include Player", true)
        initialDialog.addToggle("Include Fleet", true)
        initialDialog.addToggle("Include Officers", true)
        initialDialog.addToggle("Include Reputation", true)
        initialDialog.addToggle("Include Cargo", true)
        initialDialog.addToggle("Include Credits", true)
        initialDialog.addPadding(initialDialog.buttonHeight)

        initialDialog.addButton("Copy Save To Clipboard") { fields ->
            val json = PlayerSaveUtil.createPlayerSaveJson(
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

        initialDialog.addButton("Load Save From Clipboard") { fields ->

            val json = getClipboardJson()

            if (json == null) {
                DisplayMessage.showMessage("Failed to read json in clipboard", Color.YELLOW)
                return@addButton
            }

            val (compiled, missing) = PlayerSaveUtil.compilePlayerSaveJson(json)

            if (compiled.isEmpty()) {
                reportMissingElementsIfAny(missing)
                DisplayMessage.showMessage("Could not find contents of save in clipboard. Are you sure you copied a valid save?", Color.YELLOW)
                return@addButton
            }

            PlayerSaveUtil.loadPlayerCompiledSave(
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

        initPopUpUI(initialDialog, 300f, 360f)
    }
}