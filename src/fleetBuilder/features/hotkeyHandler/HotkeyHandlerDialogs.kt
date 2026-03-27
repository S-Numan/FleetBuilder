package fleetBuilder.features.hotkeyHandler

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.characters.SkillSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.FaderUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.CharacterStats
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.ui.impl.StandardTooltipV2
import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable
import fleetBuilder.core.FBSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.core.makeSaveRemovable.RemoveFromSave.removeModThings
import fleetBuilder.features.autofit.shipDirectory.ShipDirectoryService
import fleetBuilder.features.autofit.ui.AutofitPanel
import fleetBuilder.features.autofit.ui.AutofitSelector
import fleetBuilder.features.autofit.ui.AutofitSpec
import fleetBuilder.features.autofit.ui.ShipPreviewOverlayPlugin
import fleetBuilder.otherMods.starficz.*
import fleetBuilder.serialization.*
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.serialization.fleet.FleetSettings
import fleetBuilder.serialization.member.DataMember
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
import org.lwjgl.opengl.GL11
import org.magiclib.kotlin.*
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
            testMessageTrigger.position.inTR(0f, ui.height - testMessageTrigger.height)
            testMessageTrigger.onClick {
                DisplayMessage.showMessageCustom("Test Message!", Color.RED)

                /*val state = AppDriver.getInstance().currentState
                if (state is CampaignState) {
                    state.cmdCodex()
                    state.isHideUI
                    //CampaignGameManager().
                    CampaignEngine.getInstance().saveDirName
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

        removeModButton.position.inTL(0f, ui.height - removeModButton.height)
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


    fun pasteFleetDialog(
        inputData: DataFleet.ParsedFleetData,
        missing: MissingElements
    ): Boolean {
        val sector = Global.getSector()
        val data = inputData.copy(
            members = inputData.members.map { it.copy(id = sector.genUID()) }
        )

        val faction = Global.getSector().allFactions.find { data.factionID == it.id }
            ?: Global.getSector().getFaction("neutral") ?: Global.getSector().playerFaction

        val dialog = DialogPanel()

        val width = 1280f
        val height = 800f

        val rightWidth = 440f
        val leftWidth = width - rightWidth

        val factionColor = faction.baseUIColor
        val darkColor = faction.darkUIColor

        // IMPORTANT: persist outside UI rebuild
        var isPressedMemberID: String? = null
        var currentHoverMemberID: String? = null
        var savedScrollRatio = 0f
        val previewList = mutableListOf<ShipPreviewOverlayPlugin>()
        fun cleanupShipList() {
            previewList.forEach { it.cleanup() }
            previewList.clear()
        }
        dialog.onExit { cleanupShipList() }

        var fightToTheLast = true
        var includeOfficers = true
        var includeCommanderAsCommander = true
        var repairAndSetMaxCR = true
        var setAggressionDoctrine = true
        var excludeMissingShips = true

        fun rebuildUI(maxScroll: Float, listUI: TooltipMakerAPI) {
            // Save scrollbar position BEFORE rebuild
            savedScrollRatio = listUI.externalScroller.yOffset / maxScroll

            dialog.recreateUI()
        }

        fun excludeMissingShips(fleet: CampaignFleetAPI) {
            fleet.fleetData.membersListCopy.toList().forEach {
                if (it.variant.hasTag(VariantUtils.getFBVariantErrorTag()))
                    fleet.fleetData.removeFleetMember(it)
            }
        }

        dialog.show(width, height) { ui ->
            if (previewList.isNotEmpty())
                cleanupShipList()

            val settings = FleetSettings().apply {
                includeAggression = setAggressionDoctrine
                memberSettings.includeOfficer = includeOfficers
                includeCommanderSetFlagship = includeCommanderAsCommander
                memberSettings.applyID = true
            }

            val missingEx = MissingElements()
            missingEx.add(missing)
            val fleet = DataFleet.createCampaignFleetFromData(
                data,
                true, settings = settings, missing = missingEx
            )

            if (repairAndSetMaxCR)
                FleetUtils.fullFleetRepair(fleet.fleetData)

            if (fightToTheLast)
                fleet.memoryWithoutUpdate[MemFlags.FLEET_FIGHT_TO_THE_LAST] = true

            val members = fleet.fleetData.membersListCopy

            val memberCount = members.size
            val officerCount = members.count { it.captain != null && !it.captain.isDefault }

            val text = if (officerCount > 0) {
                if (officerCount > 1)
                    txtPlural("pasted_fleet_members_officers", memberCount, memberCount, officerCount)
                else
                    txtPlural("pasted_fleet_members_officer", memberCount, memberCount, officerCount)
            } else {
                txtPlural("pasted_fleet_members_only", memberCount)
            }

            val missingHullCount = members.count {
                it.variant.hasTag(VariantUtils.getFBVariantErrorTag())
            }

            if (members.none { it.id == isPressedMemberID })
                isPressedMemberID = null


            val root = dialog.panel.createCustomPanel(width, height, null)

            // =========================
            // LEFT PANEL
            // =========================
            val leftPanel = dialog.panel.createCustomPanel(leftWidth, height, null)

            val officerPanelHeight = if (isPressedMemberID != null) height * 0.4f else 20f
            val listHeight = height - officerPanelHeight

            val leftRoot = leftPanel.createUIElement(leftWidth, height, false)

            // -------------------------
            // SHIP LIST (SCROLLABLE)
            // -------------------------
            val listUI = leftPanel.createUIElement(leftWidth, listHeight, true)

            listUI.addSectionHeading(
                "Fleet Members",
                factionColor,
                darkColor,
                Alignment.MID,
                0f
            )

            val size = 129f
            val padding = 10f
            val numPerRow = Math.max(1, (leftWidth / (size + padding)).toInt())

            val rows = (members.size + numPerRow - 1) / numPerRow
            val totalHeight = rows * (size + padding)

            val listPanel = dialog.panel.createCustomPanel(leftWidth, totalHeight, null)

            members.forEachIndexed { index, member ->
                val col = index % numPerRow
                val row = index / numPerRow
                val x = col * (size + padding)
                val y = row * (size + padding)

                val preview = ShipPreviewOverlayPlugin(
                    member,
                    size,
                    size,
                    scaleDownSmallerShips = true,
                    showOfficersAndFlagship = true,
                    showSModAndDModBars = true,
                    manualScaleShipsToBetterFit = true
                )
                previewList.add(preview)

                val dataMember = data.members.find { it.id == member.id }
                if (dataMember != null)
                    DataMember.validateAndCleanMemberData(dataMember, missing = preview.missingElements)

                preview.onKeyDown { event ->
                    if (event.eventValue == Keyboard.KEY_F2 && UIUtils.isMouseHoveringOverComponent(preview.panel)) {
                        Global.getSettings().showCodex(member)
                        event.consume()
                    }
                }

                var skipMouseOverSoundOnce = false
                if (currentHoverMemberID == member.id) {
                    preview.boxedUIShipPreview.getHighlightFader().forceIn()
                    currentHoverMemberID = null
                    skipMouseOverSoundOnce = true
                }

                preview.onHoverEnter {
                    preview.boxedUIShipPreview.highlight()
                    if (skipMouseOverSoundOnce)
                        skipMouseOverSoundOnce = false
                    else {
                        UIUtils.playSound("ui_button_mouseover", 1f, 1f)
                    }
                    currentHoverMemberID = member.id
                }
                preview.onHoverExit {
                    preview.boxedUIShipPreview.unhighlight()
                    currentHoverMemberID = null
                }

                val clickFader = FaderUtil(0.0F, 0.05F, 0.25F)
                val defaultBGColor: Color = Color.BLACK
                val clickedBGColor: Color = Misc.getDarkPlayerColor()

                preview.renderBelow { alphaMult ->
                    val isSelected = isPressedMemberID == member.id
                    val brightness = if (isSelected) 1f else clickFader.brightness

                    val panelColor = Misc.interpolateColor(defaultBGColor, clickedBGColor, brightness)
                    val panelAlpha = panelColor.alphaf * alphaMult

                    GL11.glColor4f(panelColor.redf, panelColor.greenf, panelColor.bluef, panelAlpha)
                    GL11.glRectf(preview.panel.left, preview.panel.bottom, preview.panel.right, preview.panel.top)
                }

                preview.advance { amount ->
                    clickFader.advance(amount)
                }

                preview.onClick { event ->
                    if (!event.isLMBDownEvent) return@onClick
                    if (event.isCtrlDown) {
                        ClipboardMisc.saveMemberToClipboard(member!!, event.isShiftDown)
                    } else {
                        isPressedMemberID = if (isPressedMemberID == member.id) null else member.id

                        UIUtils.playSound("ui_button_pressed")

                        rebuildUI(totalHeight, listUI)
                    }
                }
                val tooltipClass = StandardTooltipV2Expandable::class.java
                val memberTooltip = StandardTooltipV2.createFleetMemberTooltipPreDeploy(member as FleetMember, member.captain.stats as CharacterStats)
                tooltipClass.safeInvoke("addTooltipBelow", preview.panel, memberTooltip)

                listPanel.addComponent(preview.panel).inTL(x, y)
            }

            listUI.addCustom(listPanel, 10f)
            leftPanel.addUIElement(listUI).inTL(0f, 0f)

            // Restore scroll AFTER layout is built
            val maxScroll = totalHeight

            val newScroll = (savedScrollRatio * maxScroll).coerceIn(0f, maxScroll)

            listUI.externalScroller.yOffset = newScroll

            // -------------------------
            // OFFICER PANEL
            // -------------------------
            if (isPressedMemberID != null) {
                val officerPanel = leftPanel.createCustomPanel(leftWidth, officerPanelHeight, null)

                val member = members.find { it.id == isPressedMemberID }!!
                val captain = member.captain

                val officerUI = officerPanel.createUIElement(leftWidth, officerPanelHeight, false)

                officerUI.addSectionHeading(
                    "Officer Data",
                    factionColor,
                    darkColor,
                    Alignment.MID,
                    10f
                )

                officerPanel.addUIElement(officerUI).inTL(0f, 0f)

                // =========================
                // SHIP PREVIEW (PLUGIN)
                // =========================
                val shipSize = 240f

                val preview = ShipPreviewOverlayPlugin(
                    member,
                    shipSize,
                    shipSize,
                    showFighters = true,
                    manualScaleShipsToBetterFit = true,
                    showSModAndDModBars = true,
                )
                previewList.add(preview)
                val dataMember = data.members.find { it.id == member.id }
                if (dataMember != null)
                    DataMember.validateAndCleanMemberData(dataMember, missing = preview.missingElements)

                preview.onKeyDown { event ->
                    if (event.eventValue == Keyboard.KEY_F2 && UIUtils.isMouseHoveringOverComponent(preview.panel)) {
                        Global.getSettings().showCodex(member)
                        event.consume()
                    }
                }
                preview.onClick { event ->
                    if (event.isLMBDownEvent && event.isCtrlDown) {
                        ClipboardMisc.saveMemberToClipboard(member, event.isShiftDown)
                    }
                }
                preview.onHoverEnter {
                    preview.boxedUIShipPreview.highlight()
                    UIUtils.playSound("ui_button_mouseover", 1f, 1f)
                }
                preview.onHoverExit {
                    preview.boxedUIShipPreview.unhighlight()
                }

                val tooltipClass = StandardTooltipV2Expandable::class.java
                val memberTooltip = StandardTooltipV2.createFleetMemberTooltipPreDeploy(member as FleetMember, member.captain.stats as CharacterStats)
                tooltipClass.safeInvoke("addTooltipBelow", preview.panel, memberTooltip)

                val shipX = 10f
                val shipY = 40f

                officerPanel.addPara("Personality: " + member.captain.getPersonalityName())

                if (!captain.isDefault) {
                    val officerSprite = officerPanel.addImage(captain.portraitSprite, 75f, 75f)
                    officerSprite.position.inTL(0f, 20f)
                    officerSprite.opacity = 0.3f
                }
                officerPanel.addComponent(preview.panel).inTL(shipX, shipY)


                // =========================
                // SKILLS PANEL
                // =========================
                val skillsX = shipX + shipSize + 15f
                val skillsY = 40f

                val skillsWidth = leftWidth - skillsX - 10f
                val skillsHeight = officerPanelHeight - skillsY - 10f

                val skillsUI = officerPanel.createUIElement(skillsWidth, skillsHeight, true)

                // =========================
                // ADMIRAL SKILLS (if flagship)
                // =========================
                if (member.isFlagship) {
                    skillsUI.addSectionHeading(
                        "Admiral Skills",
                        factionColor,
                        darkColor,
                        Alignment.MID,
                        10f
                    )

                    val tempModified = mutableListOf<SkillSpecAPI>()

                    for (id in Global.getSettings().skillIds) {
                        val spec = Global.getSettings().getSkillSpec(id)

                        if (spec.isAdmiralSkill && !spec.isCombatOfficerSkill) {
                            spec.isCombatOfficerSkill = true
                            tempModified.add(spec)
                        }
                        if (spec.isCombatOfficerSkill && !spec.isAdmiralSkill) {
                            spec.isCombatOfficerSkill = false
                            tempModified.add(spec)
                        }
                    }

                    skillsUI.addSkillPanel(captain, 0f)

                    // revert changes
                    for (spec in tempModified) {
                        spec.isCombatOfficerSkill = !spec.isCombatOfficerSkill
                    }
                }

                // =========================
                // COMBAT SKILLS
                // =========================
                skillsUI.addSectionHeading(
                    "Combat Skills",
                    factionColor,
                    darkColor,
                    Alignment.MID,
                    10f
                )

                skillsUI.addSkillPanel(captain, 0f)

                officerPanel.addUIElement(skillsUI).inTL(skillsX, skillsY)

                // =========================
                // ADD PANEL BELOW LIST
                // =========================
                leftPanel.addComponent(officerPanel).inTL(0f, listHeight)
            }

            leftPanel.addUIElement(leftRoot).inTL(0f, 0f)

            // =========================
            // RIGHT PANEL
            // =========================
            val rightPanel = dialog.panel.createCustomPanel(rightWidth, height, null)

            val rightUI = rightPanel.createUIElement(rightWidth - 30f, height, false)

            val tempModified = mutableListOf<SkillSpecAPI>()

            for (id in Global.getSettings().skillIds) {
                val spec = Global.getSettings().getSkillSpec(id)

                if (spec.isAdmiralSkill && !spec.isCombatOfficerSkill) {
                    spec.isCombatOfficerSkill = true
                    tempModified.add(spec)
                }
                if (spec.isCombatOfficerSkill && !spec.isAdmiralSkill) {
                    spec.isCombatOfficerSkill = false
                    tempModified.add(spec)
                }
            }


            val rightUIAdmiralHeight = 370f
            val rightUIAdmiral = rightPanel.createUIElement(rightWidth - 20f, rightUIAdmiralHeight, true)
            if (fleet.commander != null) {
                rightUIAdmiral.addSectionHeading(
                    "Admiral Skills",
                    factionColor,
                    darkColor,
                    Alignment.MID,
                    10f
                ).apply {
                    position.setSize(position.width - 5f, position.height)
                }
                val admiralSkillPanelUI = rightUIAdmiral.addSkillPanel(fleet.commander, 0f)
                admiralSkillPanelUI.position.inTL(-5f, 15f)
                rightUIAdmiral.heightSoFar -= 15f
            }
            // revert changes
            for (spec in tempModified) {
                spec.isCombatOfficerSkill = !spec.isCombatOfficerSkill
            }

            rightUI.addSectionHeading("Summary", Alignment.MID, 10f)

            val dp = members.sumOf { it.deploymentPointsCost.toDouble() }

            rightUI.addPara(
                text, 5f,
                Misc.getHighlightColor(),
                memberCount.toString(), officerCount.toString()
            )
            rightUI.addPara(
                "Deployment Points: %s",
                5f,
                Misc.getHighlightColor(),
                dp.toInt().toString()
            )

            rightUI.addSectionHeading("Actions", Alignment.MID, 10f)

            rightUI.addButton(
                "Simulated Battle",
                null,
                rightWidth - 40f,
                30f,
                5f
            ).apply {
                setShortcut(Keyboard.KEY_T, true)
                onClick {
                    if (excludeMissingShips)
                        excludeMissingShips(fleet)

                    dialog.dismiss()
                }
            }

            rightUI.addToggle("Fight to the last", fightToTheLast).apply {
                onClick {
                    fightToTheLast = !fightToTheLast
                    rebuildUI(totalHeight, listUI)
                }
                addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 400f) {
                    it.addPara("If checked; no members of the fleet will retreat", 0f)
                }
            }
            rightUI.addSpacer(10f)

            rightUI.addToggle("Include officers", includeOfficers).onClick {
                includeOfficers = !includeOfficers
                rebuildUI(totalHeight, listUI)
            }

            rightUI.addToggle("Include commander as commander", includeCommanderAsCommander).apply {
                onClick {
                    includeCommanderAsCommander = !includeCommanderAsCommander
                    rebuildUI(totalHeight, listUI)
                }
                addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 400f) {
                    it.addPara("If unchecked; the commander will be unset as the commander of the fleet. Their admiral skills will not apply", 0f)
                }
            }

            rightUI.addToggle("Fulfill needs and repair", repairAndSetMaxCR).onClick {
                repairAndSetMaxCR = !repairAndSetMaxCR
                rebuildUI(totalHeight, listUI)
            }

            rightUI.addToggle("Set aggression doctrine", setAggressionDoctrine).onClick {
                setAggressionDoctrine = !setAggressionDoctrine
                rebuildUI(totalHeight, listUI)
            }

            rightUI.addToggle("Exclude ships from missing mods", excludeMissingShips).onClick {
                excludeMissingShips = !excludeMissingShips
                rebuildUI(totalHeight, listUI)
            }

            if (FBSettings.cheatsEnabled()) {
                rightUI.addSectionHeading("Cheats", Alignment.MID, 10f)
                rightUI.addButton(
                    "Spawn Fleet Into Campaign",
                    null,
                    rightWidth - 40f,
                    30f,
                    5f
                ).onClick {
                    if (excludeMissingShips)
                        excludeMissingShips(fleet)


                    sector.playerFleet.containingLocation.spawnFleet(sector.playerFleet, 0f, 0f, fleet)
                    dialog.onExit {
                        Global.getSector().campaignUI.showInteractionDialog(fleet)
                    }
                    DisplayMessage.showMessage(FBTxt.txt("clipboard_fleet_added_to_campaign"))
                    dialog.dismiss()
                }
                rightUI.addButton(
                    "Replace Player Fleet With This Fleet",
                    null,
                    rightWidth - 40f,
                    30f,
                    5f
                ).onClick {
                    if (excludeMissingShips)
                        excludeMissingShips(fleet)

                    reportMissingElementsIfAny(missingEx)

                    FleetUtils.replacePlayerFleetWith(
                        fleet,
                        aggression = if (setAggressionDoctrine) data.aggression else -1,
                        replacePlayer = includeCommanderAsCommander && fleet.commander != null && !fleet.commander.isDefault && !fleet.commander.isAICore
                    )

                    if (repairAndSetMaxCR)
                        FleetUtils.fulfillPlayerFleet()

                    ReflectionMisc.updateFleetPanelContents()

                    DisplayMessage.showMessage(FBTxt.txt("player_fleet_replaced"))

                    dialog.dismiss()
                }
                rightUI.addButton(
                    "Append To Player Fleet",
                    null,
                    rightWidth - 40f,
                    30f,
                    5f
                ).onClick {
                    if (excludeMissingShips)
                        excludeMissingShips(fleet)

                    reportMissingElementsIfAny(missingEx)

                    val playerFleet = Global.getSector().playerFleet.fleetData

                    fleet.fleetData.membersListCopy.forEach { member ->
                        member.id = Global.getSector().genUID()
                        playerFleet.addFleetMember(member)

                        val captain = member.captain
                        if (!captain.isDefault && !captain.isAICore) {
                            playerFleet.addOfficer(captain)
                        }
                    }

                    if (repairAndSetMaxCR)
                        FleetUtils.fulfillPlayerFleet()

                    ReflectionMisc.updateFleetPanelContents()

                    if (fleet.fleetData.membersListCopy.size > 1)
                        DisplayMessage.showMessage(FBTxt.txt("members_appended_into_fleet", fleet.fleetData.membersListCopy.size))
                    else
                        DisplayMessage.showMessage(FBTxt.txt("member_appended_into_fleet", fleet.fleetData.membersListCopy.size))

                    dialog.dismiss()
                }
            }


            rightPanel.addUIElement(rightUIAdmiral).inTL(10f, 0f)
            rightPanel.addUIElement(rightUI).inTL(10f, rightUIAdmiralHeight)

            // =========================
            // ADD TO ROOT
            // =========================
            root.addComponent(leftPanel).inTL(0f, 0f)
            root.addComponent(rightPanel).rightOfTop(leftPanel, 0f)
            ui.addCustom(root, 0f)
        }

        /*
        val tempPanel = Global.getSettings().createCustom(ui.width - 350f, ui.height, null)
            val shipsPanel = tempPanel.createUIElement(tempPanel.width, tempPanel.height, false)
            tempPanel.addUIElement(shipsPanel)
            val iconSize = 80f

            val numPerRow = max(1, ((shipsPanel.width / iconSize)).toInt())

            //val shipPreview = createShipPreview(member, 400f, 400f, showFighters = true, showSModAndDModBars = true)
            shipsPanel.addShipList(numPerRow, (members.size + numPerRow - 1) / numPerRow, iconSize, faction.baseUIColor, members, 0f);

            ui.addCustom(tempPanel, 0f).position.inTL(0f, 0f)
         */

        /*dialog.show(500f, 350f) { ui ->


            ui.addPara(text, 0f)

            if (missingHullCount > 0)
                ui.addPara(txtPlural("fleet_contains_missing_hull", missingHullCount), 0f)


            ui.addSpacer(8f)

            val buttonHeight = 24f
            val includeOfficers = ui.addToggle(FBTxt.txt("include_officers"), true)
            val includeCommanderAsCommander = ui.addToggle(FBTxt.txt("include_commander_as_commander"), true)
            val includeCommanderAsOfficerToggle = ui.addToggle(FBTxt.txt("include_commander_as_officer"), true)
            val setAggressionDoctrine = ui.addToggle(FBTxt.txt("set_aggression_doctrine"), true)
            ui.addSpacer(buttonHeight / 2)
            val setFactionToPirates = ui.addToggle(FBTxt.txt("set_faction_to_pirate"), true)
            val fightToTheLast = ui.addToggle(FBTxt.txt("fight_to_the_last"), true)
            ui.addSpacer(buttonHeight / 2)
            val repairAndSetMaxCR = ui.addToggle(FBTxt.txt("repair_and_set_max_cr"), true)
            val excludeMissingShips = ui.addToggle(FBTxt.txt("exclude_ships_from_missing_mods"), true)

            dialog.addActionButtons(confirmText = FBTxt.txt("spawn_fleet"))

            dialog.onConfirm {
                val settings = FleetSettings().apply {
                    includeAggression = setAggressionDoctrine.isChecked
                    memberSettings.includeOfficer = includeOfficers.isChecked
                    includeCommanderSetFlagship = includeCommanderAsCommander.isChecked
                    includeCommanderAsOfficer = includeCommanderAsOfficerToggle.isChecked
                    excludeMembersWithMissingHullSpec = excludeMissingShips.isChecked
                }
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
        }*/

        return true
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
        initialDialog.tooltipPadFromSide = 10f

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
            val para = ui.addPara(
                loadoutBaseHullName,
                0f,
                arrayOf(Color.YELLOW),
                *arrayOf(loadoutBaseHullName)
            )
            para.autoSizeToText().position.inMid()

            val selectorPlugin = AutofitSelector.createAutofitSelector(
                autofitSpec = AutofitSpec(variant, null),
                ui.width - 12f,
                addDescription = false,
                centerTitle = true
            )
            dialog.onExit {
                selectorPlugin.cleanup()
            }

            ui.addComponent(selectorPlugin.selectorPanel).inTMid(dialog.tooltipPadFromTop - 14f)

            AutofitPanel.makeTooltip(selectorPlugin.selectorPanel, variant)

            dialog.addActionButtons(confirmText = FBTxt.txt("import"), alignment = Alignment.MID)

            dialog.confirmButton?.addTooltip(TooltipMakerAPI.TooltipLocation.ABOVE, 600f) { tooltip ->
                tooltip.addPara(
                    FBTxt.txt(
                        "import_loadout_tooltip",
                        variant.hullSpec.getEffectiveHull().hullName,
                        ShipDirectoryService.getShipDirectoryWithPrefix(FBSettings.defaultPrefix)?.name,
                        FBSettings.defaultPrefix
                    ),
                    0f
                )
            }
        }

        dialog.onConfirm {
            ShipDirectoryService.importShipLoadout(FBSettings.defaultPrefix, variant, missing)

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