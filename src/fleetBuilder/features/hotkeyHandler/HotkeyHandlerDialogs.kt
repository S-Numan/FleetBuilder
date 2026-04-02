package fleetBuilder.features.hotkeyHandler

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.characters.SkillSpecAPI
import com.fs.starfarer.api.combat.BattleCreationContext
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetGoal
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.ids.Skills
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.FaderUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.CharacterStats
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.ui.impl.StandardTooltipV2
import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable
import fleetBuilder.core.FBTxt
import fleetBuilder.core.FBSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.core.makeSaveRemovable.RemoveFromSave.removeModThings
import fleetBuilder.features.autofit.shipDirectory.ShipDirectoryService
import fleetBuilder.features.autofit.ui.AutofitPanel
import fleetBuilder.features.autofit.ui.AutofitSelector
import fleetBuilder.features.autofit.ui.AutofitSpec
import fleetBuilder.features.autofit.ui.ShipPreviewOverlayPlugin
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.pasteFleet
import fleetBuilder.features.recentBattles.RecentBattleReplay
import fleetBuilder.otherMods.starficz.*
import fleetBuilder.serialization.*
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.serialization.fleet.FleetSettings
import fleetBuilder.serialization.member.DataMember
import fleetBuilder.serialization.variant.DataVariant
import fleetBuilder.ui.UIUtils
import fleetBuilder.ui.customPanel.common.DialogPanel
import fleetBuilder.ui.customPanel.common.ModalPanel
import fleetBuilder.util.*
import fleetBuilder.core.FBTxt.txtPlural
import fleetBuilder.util.api.FleetUtils
import fleetBuilder.util.api.PersonUtils
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.kotlin.addNumericTextField
import fleetBuilder.util.kotlin.addCheckboxD
import fleetBuilder.util.kotlin.completelyRemoveMod
import fleetBuilder.util.kotlin.createFleetMember
import fleetBuilder.util.kotlin.getActualCurrentTab
import fleetBuilder.util.kotlin.getEffectiveHull
import fleetBuilder.util.kotlin.safeInvoke
import fleetBuilder.util.deferredAction.CampaignDeferredActionPlugin
import fleetBuilder.util.lib.ClipboardUtil
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.magiclib.kotlin.*
import java.awt.Color
import java.util.*


object HotkeyHandlerDialogs {

    fun createDevModeDialog() {
        val dialog = DialogPanel(FBTxt.txt("dev_options_title"))
        dialog.animation = ModalPanel.PanelAnimation.NONE
        dialog.uiBorderColor = Color(255, 70, 70)

        dialog.show(width = 500f, height = 200f) { ui ->
            val toggleDev = ui.addCheckboxD(FBTxt.txt("toggle_dev_mode"), Global.getSettings().isDevMode)
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
                val removeListeners = ui.addCheckboxD("Remove all listeners")
                removeListeners.addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 400f) {
                    it.addPara("May crash the game", Color.RED, 0f)
                }
                val removeEntities = ui.addCheckboxD("Remove all faction owned entities", isChecked = true)

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

    private sealed class SimulationBlockReason(val color: Color, val message: String, val image: String) {

        object Restricted : SimulationBlockReason(
            Color.RED,
            FBTxt.txt("sim_block_restricted"),
            "graphics/icons/more_info_buttonless.png"
        )

        object NoSim : SimulationBlockReason(
            Color.ORANGE,
            FBTxt.txt("sim_block_no_sim"),
            "graphics/icons/more_info_buttonless.png"
        )

        object Station : SimulationBlockReason(
            Color.BLUE,
            FBTxt.txt("sim_block_station"),
            "graphics/icons/mission_marker.png"
        )

        object Cosmic : SimulationBlockReason(
            Color(128, 0, 128),
            FBTxt.txt("sim_block_cosmic"),
            "graphics/icons/mission_marker.png"
        )

        object MissingHull : SimulationBlockReason(
            Color.RED,
            FBTxt.txt("sim_block_missing_hull"),
            "graphics/icons/mission_marker.png"
        )

        object UnknownHull : SimulationBlockReason(
            Color.RED,
            FBTxt.txt("sim_block_unknown_hull"),
            "graphics/icons/more_info_buttonless.png"
        )
    }

    private fun getBlockReason(
        member: FleetMemberAPI,
        unknown: MissingContent,
        allowSimulationAnyway: Boolean
    ): SimulationBlockReason? {
        if (allowSimulationAnyway || FBSettings.cheatsEnabled()) return null

        return when {
            member.variant.hasTag(VariantUtils.FB_ERROR_TAG) -> SimulationBlockReason.MissingHull
            member.hullSpec.hasTag(Tags.RESTRICTED) || member.variant.hasTag(Tags.RESTRICTED) -> SimulationBlockReason.Restricted
            member.hullSpec.hasTag(Tags.NO_SIM) || member.variant.hasTag(Tags.NO_SIM) -> SimulationBlockReason.NoSim
            member.isStation -> SimulationBlockReason.Station
            member.hullSpec.hasTag(Tags.DWELLER) || member.hullSpec.hasTag(Tags.THREAT) -> SimulationBlockReason.Cosmic
            unknown.hullIds.isNotEmpty() -> SimulationBlockReason.UnknownHull
            else -> null
        }
    }

    fun pasteFleetDialog(
        inputData: DataFleet.ParsedFleetData,
        inputMissing: MissingContent,
        allowSimulationAnyway: Boolean = false
    ): Boolean {
        val sector = Global.getSector()
        val data = inputData.copy(
            members = inputData.members.map { member ->
                member.copy(id = sector.genUID())
            }
        )

        val dialog = DialogPanel()
        dialog.makeCampaignDummyDialogHideUI = true

        val brightColor = Global.getSettings().brightPlayerColor
        val factionColor = Global.getSettings().basePlayerColor//faction.baseUIColor
        val darkColor = Global.getSettings().darkPlayerColor //faction.darkUIColor


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
        var allowObjectives = true
        var forceObjectives = false
        var includeOfficers = true
        var includeCommanderAsCommander = true
        var repairAndSetMaxCR = true
        var setAggressionDoctrine = true

        fun rebuildUI(maxScroll: Float, listUI: TooltipMakerAPI) {
            // Save scrollbar position BEFORE rebuild
            savedScrollRatio = listUI.externalScroller.yOffset / maxScroll

            dialog.recreateUI()
        }

        fun excludeMissingShips(fleet: CampaignFleetAPI) {
            fleet.fleetData.membersListCopy.toList().forEach {
                if (it.variant.hasTag(VariantUtils.FB_ERROR_TAG) || it.variant.hasTag("#FB_IGNORE"))
                    fleet.fleetData.removeFleetMember(it)
            }
        }

        val seed = Random().nextLong()

        dialog.show(1280f, 800f) { ui ->
            ui.setSize(1280f, 800f) // Probably shouldn't be doing this, I should account for side pad properly, but it's too time-consuming to fix this mess of a function.

            val rightWidth = 440f
            val leftWidth = ui.width - rightWidth

            if (previewList.isNotEmpty())
                cleanupShipList()

            val settings = FleetSettings().apply {
                includeAggression = setAggressionDoctrine
                memberSettings.includeOfficer = includeOfficers
                includeCommanderSetFlagship = includeCommanderAsCommander
                memberSettings.applyID = true
            }

            val missingEx = MissingContent()
            missingEx.add(inputMissing)

            val deterministicRandom = Random(seed)

            val fleet = DataFleet.createCampaignFleetFromData(
                data,
                true, settings = settings, missing = missingEx, random = deterministicRandom
            )
            val fleetData = fleet.fleetData

            if (fleet.commander.isDefault && fleet.commander.stats.skillsCopy.count { it.level > 0f } > 0) { // Default commander but has skills?
                fleet.commander.portraitSprite = PersonUtils.getRandomPortrait(factionID = fleet.faction.id, random = deterministicRandom) // Set a random portrait, making isDefault false.
            }

            if (repairAndSetMaxCR)
                FleetUtils.fullFleetRepair(fleet.fleetData)

            if (fightToTheLast)
                fleet.memoryWithoutUpdate[MemFlags.FLEET_FIGHT_TO_THE_LAST] = true

            if (fleetData.membersListCopy.none { it.id == isPressedMemberID })
                isPressedMemberID = null


            val root = dialog.panel.createCustomPanel(ui.width, ui.height, null)

            val leftPanel = dialog.panel.createCustomPanel(leftWidth, ui.height, null)

            val officerPanelHeight = if (isPressedMemberID != null) ui.height * 0.4f else 20f
            val listHeight = ui.height - officerPanelHeight

            val leftRoot = leftPanel.createUIElement(leftPanel.width, ui.height, false)

            // -------------------------
            // SHIP LIST (SCROLLABLE)
            // -------------------------
            val listUI = leftPanel.createUIElement(leftPanel.width, listHeight, true)

            listUI.addSectionHeading(
                FBTxt.txt("fleet_members"),
                factionColor,
                darkColor,
                Alignment.MID,
                0f
            ).position.setXAlignOffset(0f)

            val size = 129f
            val paddingX = 10f
            val paddingY = 14f

            val numPerRow = Math.max(1, (leftWidth / (size + paddingX)).toInt())

            val rows = (fleetData.membersListCopy.size + numPerRow - 1) / numPerRow
            val totalHeight = rows * size + (rows - 1) * paddingY

            val listPanel = dialog.panel.createCustomPanel(leftWidth, totalHeight, null)

            val unknownContentsMap = mutableMapOf<FleetMemberAPI, MissingContent>()

            fleetData.membersListCopy.toList().forEachIndexed { index, member ->
                val col = index % numPerRow
                val row = index / numPerRow

                val x = col * (size + paddingX)
                val y = row * (size + paddingY)

                val unknownContents = MissingContent()
                VariantUtils.whatVariantContentsAreNotKnownToPlayer(member.variant, unknownContents)
                unknownContentsMap[member] = unknownContents

                val dataMember = data.members.find { it.id == member.id }

                val reason = getBlockReason(member, unknownContents, allowSimulationAnyway)
                if (reason != null) {
                    val img = listPanel.addImage(reason.image, size, size)
                    img.position.inTL(x, y)
                    img.sprite.color = reason.color
                    img.uiImage.addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 420f) {
                        if (reason is SimulationBlockReason.MissingHull)
                            it.addPara(FBTxt.txt("sim_block_missing_hull_detail", dataMember?.variantData?.hullId), 0f)
                        else
                            it.addPara(reason.message, 0f)
                    }

                    member.variant.addTag("#FB_IGNORE") // Will cause it to be removed on spawning
                    return@forEachIndexed
                }

                val preview = ShipPreviewOverlayPlugin(
                    member,
                    size,
                    size,
                    scaleDownSmallerShips = true,
                    showOfficersAndFlagship = true,
                    showSModAndDModBars = true,
                    //manualScaleShipsToBetterFit = true,
                    disableScissor = true,
                )
                previewList.add(preview)

                if (dataMember != null)
                    DataMember.validateAndCleanMemberData(dataMember, missing = preview.missingContent)

                preview.onKeyDown { event ->
                    if (event.eventValue == Keyboard.KEY_F2 && UIUtils.isMouseHoveringOverComponent(preview.panel, mouseX = event.x, mouseY = event.y)) {
                        Global.getSettings().showCodex(member)
                        event.consume()
                    }
                }

                var skipMouseOverSoundOnce = false
                if (currentHoverMemberID == member.id) {
                    preview.boxedUIShipPreview?.getHighlightFader()?.forceIn()
                    currentHoverMemberID = null
                    skipMouseOverSoundOnce = true
                }

                preview.onHoverEnter {
                    preview.boxedUIShipPreview?.highlight()
                    if (skipMouseOverSoundOnce)
                        skipMouseOverSoundOnce = false
                    else {
                        UIUtils.playSound("ui_button_mouseover", 1f, 1f)
                    }
                    currentHoverMemberID = member.id
                }
                preview.onHoverExit {
                    preview.boxedUIShipPreview?.unhighlight()
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


                if (!allowSimulationAnyway && !FBSettings.cheatsEnabled() && (unknownContents.weaponIds.isNotEmpty() || unknownContents.wingIds.isNotEmpty() || unknownContents.hullModIds.isNotEmpty())) {
                    val boxedImage = listPanel.addImage("graphics/icons/more_info_buttonless.png", size, size)
                    boxedImage.position.inTL(x, y)
                    boxedImage.sprite.color = Color.YELLOW.setAlpha(70)

                    removeUnknownContent(member, unknownContents)
                }
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

                val member = fleetData.membersListCopy.find { it.id == isPressedMemberID } ?: return@show
                val captain = member.captain

                val officerUI = officerPanel.createUIElement(leftWidth, officerPanelHeight, false)

                officerUI.addSectionHeading(
                    FBTxt.txt("officer_data"),
                    factionColor,
                    darkColor,
                    Alignment.MID,
                    10f
                )

                officerPanel.addUIElement(officerUI).inTL(0f, 0f)


                val shipSize = 240f

                val preview = ShipPreviewOverlayPlugin(
                    member,
                    shipSize,
                    shipSize,
                    showFighters = true,
                    //manualScaleShipsToBetterFit = true,
                    showSModAndDModBars = true,
                    disableScissor = true,
                )
                previewList.add(preview)
                val dataMember = data.members.find { it.id == member.id }
                if (dataMember != null)
                    DataMember.validateAndCleanMemberData(dataMember, missing = preview.missingContent)

                preview.onKeyDown { event ->
                    if (event.eventValue == Keyboard.KEY_F2 && UIUtils.isMouseHoveringOverComponent(preview.panel, mouseX = event.x, mouseY = event.y)) {
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
                    preview.boxedUIShipPreview?.highlight()
                    UIUtils.playSound("ui_button_mouseover", 1f, 1f)
                }
                preview.onHoverExit {
                    preview.boxedUIShipPreview?.unhighlight()
                }

                val tooltipClass = StandardTooltipV2Expandable::class.java
                val memberTooltip = StandardTooltipV2.createFleetMemberTooltipPreDeploy(member as FleetMember, member.captain.stats as CharacterStats)
                tooltipClass.safeInvoke("addTooltipBelow", preview.panel, memberTooltip)

                val shipX = 10f
                val shipY = 40f

                officerPanel.addPara("Personality: " + member.captain.getPersonalityName())

                if (!captain.isDefault && captain.portraitSprite != null) {
                    val officerSprite = officerPanel.addImage(captain.portraitSprite, 75f, 75f)
                    officerSprite.position.inTL(0f, 20f)
                    officerSprite.opacity = 0.3f
                }
                officerPanel.addComponent(preview.panel).inTL(shipX, shipY)

                val unknownContents = unknownContentsMap[member]


                // =========================
                // SKILLS PANEL
                // =========================
                val skillsX = shipX + shipSize + 15f
                val skillsY = 40f

                val skillsWidth = leftWidth - skillsX - 10f
                val skillsHeight = officerPanelHeight - skillsY - 10f

                val skillsUI = officerPanel.createUIElement(skillsWidth, skillsHeight, true)
                if (preview.missingContent.weaponIds.isNotEmpty() || preview.missingContent.wingIds.isNotEmpty() || preview.missingContent.hullModIds.isNotEmpty()) {
                    skillsUI.addPara(preview.missingContent.getMissingContentString(true), Color.YELLOW, 0f)
                }

                if (!allowSimulationAnyway && !FBSettings.cheatsEnabled() && unknownContents != null && (unknownContents.weaponIds.isNotEmpty() || unknownContents.wingIds.isNotEmpty() || unknownContents.hullModIds.isNotEmpty())) {
                    val boxedImage = officerPanel.addImage("graphics/icons/more_info_buttonless.png", shipSize, shipSize)
                    boxedImage.position.inTL(shipX, shipY)
                    boxedImage.sprite.color = Color.YELLOW.setAlpha(70)

                    val parts = listOfNotNull(
                        FBTxt.txt("unknown_weapons").takeIf { unknownContents.weaponIds.isNotEmpty() },
                        FBTxt.txt("unknown_wings").takeIf { unknownContents.wingIds.isNotEmpty() },
                        FBTxt.txt("unknown_hullmods").takeIf { unknownContents.hullModIds.isNotEmpty() }
                    )

                    fun joinNaturalLocalized(parts: List<String>): String {
                        return when (parts.size) {
                            0 -> ""
                            1 -> parts.first()
                            2 -> FBTxt.txt("list_two", parts[0], parts[1])
                            else -> {
                                val allButLast = parts.dropLast(1).joinToString(FBTxt.txt("list_separator"))
                                FBTxt.txt("list_many", allButLast, parts.last())
                            }
                        }
                    }

                    if (parts.isNotEmpty()) {
                        val message = FBTxt.txt("unknown_combined", joinNaturalLocalized(parts))
                        skillsUI.addPara(message, Color.YELLOW, 0f)
                    }
                }

                // =========================
                // ADMIRAL SKILLS (if flagship)
                // =========================
                if (member.isFlagship) {
                    skillsUI.addSectionHeading(
                        FBTxt.txt("admiral_skills"),
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
                    FBTxt.txt("combat_skills"),
                    factionColor,
                    darkColor,
                    Alignment.MID,
                    10f
                )

                val defaultSupportDoctrine = captain.isDefault && fleetData.commander.stats.hasSkill(Skills.SUPPORT_DOCTRINE)
                if (defaultSupportDoctrine) {
                    captain.stats.increaseSkill(Skills.HELMSMANSHIP)
                    captain.stats.increaseSkill(Skills.DAMAGE_CONTROL)
                    captain.stats.increaseSkill(Skills.COMBAT_ENDURANCE)
                    captain.stats.increaseSkill(Skills.ORDNANCE_EXPERTISE)
                }

                skillsUI.addSkillPanel(captain, 0f)

                if (defaultSupportDoctrine) {
                    captain.stats.decreaseSkill(Skills.HELMSMANSHIP)
                    captain.stats.decreaseSkill(Skills.DAMAGE_CONTROL)
                    captain.stats.decreaseSkill(Skills.COMBAT_ENDURANCE)
                    captain.stats.decreaseSkill(Skills.ORDNANCE_EXPERTISE)
                }

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
            val rightPanel = dialog.panel.createCustomPanel(rightWidth, ui.height, null)

            val rightUI = rightPanel.createUIElement(rightWidth - 30f, ui.height, false)

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
                    FBTxt.txt("admiral_skills"),
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

            rightUI.addSectionHeading(FBTxt.txt("summary"), factionColor, darkColor, Alignment.MID, 10f)

            val allowedMemberList =
                if (!FBSettings.cheatsEnabled()) fleetData.membersListCopy.filterNot { it.variant.hasTag(VariantUtils.FB_ERROR_TAG) || it.variant.hasTag("#FB_IGNORE") }
                else fleetData.membersListCopy

            val dp = allowedMemberList.sumOf { it.deploymentPointsCost.toDouble() }
            val memberCount = allowedMemberList.size
            val officerCount = allowedMemberList.count { it.captain != null && !it.captain.isDefault }

            val text = if (officerCount > 0) {
                if (officerCount > 1)
                    txtPlural("pasted_fleet_members_officers", memberCount, memberCount, officerCount)
                else
                    txtPlural("pasted_fleet_members_officer", memberCount, memberCount, officerCount)
            } else {
                txtPlural("pasted_fleet_members_only", memberCount)
            }


            rightUI.addPara(
                text, 5f,
                Misc.getHighlightColor(),
                memberCount.toString(), officerCount.toString()
            )
            rightUI.addPara(
                FBTxt.txt("deployment_points"),
                5f,
                Misc.getHighlightColor(),
                dp.toInt().toString()
            )

            rightUI.addSectionHeading(FBTxt.txt("actions"), factionColor, darkColor, Alignment.MID, 7f)

            val simulatedBattleButton = rightUI.addButton(
                FBTxt.txt("simulated_battle"),
                null, factionColor, darkColor,
                rightWidth - 40f,
                30f,
                5f
            ).apply {
                setShortcut(Keyboard.KEY_T, true)
                onClick {
                    excludeMissingShips(fleet)

                    reportMissingContentIfAny(missingEx)

                    // val battle = Global.getFactory().createBattle(Global.getSector().playerFleet, fleet)

                    // Fleet is not alive (location is null), so battle will see it as empty
                    //battle.genCombinedDoNotRemoveEmpty()

                    val battleContext = BattleCreationContext(Global.getSector().playerFleet, FleetGoal.ATTACK, fleet, FleetGoal.ATTACK)
                    battleContext.fightToTheLast = fightToTheLast
                    battleContext.aiRetreatAllowed = !fightToTheLast
                    battleContext.enemyDeployAll = true
                    battleContext.objectivesAllowed = allowObjectives
                    battleContext.forceObjectivesOnMap = forceObjectives
                    battleContext.playerCommandPoints = 5

                    val campUI = Global.getSector().campaignUI
                    if (campUI.currentInteractionDialog == null && campUI.getActualCurrentTab() != null) { // Tab open, but not at interaction?
                        // Force close the dialog, and the current tab
                        dialog.forceDismiss()
                        campUI.safeInvoke("setNextTransitionFast", true)
                        val coreUI = ReflectionMisc.getCoreUI()
                        coreUI?.safeInvoke("dialogDismissed", coreUI, 0)

                        //Re-open the dialog, will also open the dummy dialog.
                        CampaignDeferredActionPlugin.performLater(1f) {
                            pasteFleet(inputData, inputMissing)
                            RecentBattleReplay.simulateBattle(battleContext)
                        }
                    } else {
                        RecentBattleReplay.simulateBattle(battleContext)
                    }

                    //battle.finish(null, false)
                }
            }

            val fightToTheLastButton = rightUI.addCheckboxD(
                FBTxt.txt("ui.toggle.fight_to_last"),
                fightToTheLast,
                textColor = brightColor
            ).apply {
                onClick {
                    fightToTheLast = !fightToTheLast
                    rebuildUI(totalHeight, listUI)
                }
                addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 400f) {
                    it.addPara(FBTxt.txt("ui.toggle.fight_to_last.tooltip"), 0f)
                }
                position.belowLeft(simulatedBattleButton, 2f)
            }

            rightUI.addCheckboxD(
                FBTxt.txt("ui.toggle.allow_objectives"),
                allowObjectives,
                textColor = brightColor
            ).apply {
                onClick {
                    allowObjectives = !allowObjectives
                    rebuildUI(totalHeight, listUI)
                }
                addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 400f) {
                    it.addPara(FBTxt.txt("ui.toggle.allow_objectives.tooltip"), 0f)
                }
                position.belowMid(simulatedBattleButton, 2f)
            }

            rightUI.addCheckboxD(
                FBTxt.txt("ui.toggle.force_objectives"),
                forceObjectives,
                textColor = brightColor
            ).apply {
                onClick {
                    forceObjectives = !forceObjectives
                    rebuildUI(totalHeight, listUI)
                }
                addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 400f) {
                    it.addPara(FBTxt.txt("ui.toggle.force_objectives.tooltip"), 0f)
                }
                position.belowRight(simulatedBattleButton, 2f)
            }

            rightUI.addCheckboxD(
                FBTxt.txt("ui.toggle.include_officers"),
                includeOfficers,
                textColor = brightColor
            ).apply {
                onClick {
                    includeOfficers = !includeOfficers
                    rebuildUI(totalHeight, listUI)
                }
                position.belowLeft(fightToTheLastButton, 3f)
            }

            rightUI.addCheckboxD(
                FBTxt.txt("ui.toggle.include_commander"),
                includeCommanderAsCommander,
                textColor = brightColor
            ).apply {
                onClick {
                    includeCommanderAsCommander = !includeCommanderAsCommander
                    rebuildUI(totalHeight, listUI)
                }
                addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 400f) {
                    it.addPara(FBTxt.txt("ui.toggle.include_commander.tooltip"), 0f)
                }
            }

            rightUI.addCheckboxD(
                FBTxt.txt("ui.toggle.repair_and_cr"),
                repairAndSetMaxCR,
                textColor = brightColor
            ).onClick {
                repairAndSetMaxCR = !repairAndSetMaxCR
                rebuildUI(totalHeight, listUI)
            }

            rightUI.addCheckboxD(
                FBTxt.txt("ui.toggle.set_aggression"),
                setAggressionDoctrine,
                textColor = brightColor
            ).onClick {
                setAggressionDoctrine = !setAggressionDoctrine
                rebuildUI(totalHeight, listUI)
            }

            if (FBSettings.cheatsEnabled()) {
                rightUI.addSectionHeading(FBTxt.txt("cheats"), factionColor, darkColor, Alignment.MID, 5f)
                rightUI.addButton(
                    FBTxt.txt("spawn_fleet_into_campaign"),
                    null, factionColor, darkColor,
                    rightWidth - 40f,
                    30f,
                    5f,
                ).onClick {
                    excludeMissingShips(fleet)

                    reportMissingContentIfAny(missingEx)

                    sector.playerFleet.containingLocation.spawnFleet(sector.playerFleet, 0f, 0f, fleet)
                    //dialog.onExit {
                    //    Global.getSector().campaignUI.showInteractionDialog(fleet)
                    //}
                    DisplayMessage.showMessage(FBTxt.txt("clipboard_fleet_added_to_campaign"))
                    dialog.dismiss()
                }
                rightUI.addButton(
                    FBTxt.txt("replace_player_fleet_with_this_fleet"),
                    null, factionColor, darkColor,
                    rightWidth - 40f,
                    30f,
                    5f
                ).onClick {
                    //excludeMissingShips(fleet)

                    reportMissingContentIfAny(missingEx)

                    FleetUtils.replacePlayerFleetWith(
                        fleet,
                        aggression = if (setAggressionDoctrine) data.aggression else -1,
                        replacePlayer = includeCommanderAsCommander && fleet.commander != null && !fleet.commander.isDefault && !fleet.commander.isAICore,
                    )

                    if (repairAndSetMaxCR)
                        FleetUtils.fulfillPlayerFleet()

                    ReflectionMisc.updateFleetPanelContents()

                    DisplayMessage.showMessage(FBTxt.txt("player_fleet_replaced"))

                    dialog.dismiss()
                }
                rightUI.addButton(
                    FBTxt.txt("append_to_player_fleet"),
                    null, factionColor, darkColor,
                    rightWidth - 40f,
                    30f,
                    5f
                ).onClick {
                    //excludeMissingShips(fleet)

                    reportMissingContentIfAny(missingEx)

                    val playerFleet = Global.getSector().playerFleet.fleetData

                    fleet.fleetData.membersListCopy.forEach { member ->
                        member.id = Global.getSector().genUID()
                        playerFleet.addFleetMember(member)

                        val captain = member.captain
                        if (!captain.isDefault && !captain.isAICore) {
                            playerFleet.addOfficer(captain)
                        }
                    }
                    sector.memoryWithoutUpdate?.set("\$FB_NO-OVER-OFFICER-LIMIT-MOTHBALL", true)

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

            dialog.clearStarUIFunctions()
            dialog.onKeyDown { event ->
                if (event.isCtrlDown) {
                    if (event.eventValue == Keyboard.KEY_C) {
                        excludeMissingShips(fleet)
                        ClipboardMisc.saveFleetToClipboard(fleet.fleetData, event.isShiftDown)
                    } else if (event.eventValue == Keyboard.KEY_V) {
                        dialog.forceDismiss()
                        val missing = MissingContent()
                        val data = ClipboardMisc.extractDataFromClipboard(missing)
                        if (data != null && (data is DataFleet.ParsedFleetData || data is DataMember.ParsedMemberData || data is DataVariant.ParsedVariantData)) {
                            CampaignDeferredActionPlugin.performLater(0f) {
                                pasteFleet(data, missing)
                            }
                        }
                    }
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

            //dialog.panel.removeComponent(dialog.cancelButton?.parent)
            //dialog.panel.bringComponentToTop(dialog.closeButton?.parent)
        }
        //dialog.addCloseButton()
        dialog.addActionButtons(addConfirmButton = false, alignment = Alignment.RMID)

        return true
    }

    /*private fun pasteFleetDialogLeftPanel(leftPanel: CustomPanelAPI, fleetData: FleetDataAPI, isPressedMemberID: String?): UIPanelAPI {

    }

    private fun pasteFleetDialogLeftPanelMember(): UIPanelAPI {

    }

    private fun pasteFleetDialogRightPanel(): UIPanelAPI {

    }*/

    private fun removeUnknownContent(member: FleetMemberAPI, unknown: MissingContent) {
        val variant = member.variant

        variant.fittedWeaponSlots.toList().forEach { slot ->
            if (variant.getWeaponId(slot) in unknown.weaponIds) {
                variant.clearSlot(slot)
            }
        }

        variant.fittedWings.removeAll { it in unknown.wingIds }

        variant.hullMods.toList().forEach {
            if (!variant.hullSpec.isBuiltInMod(it) && it in unknown.hullModIds) {
                variant.completelyRemoveMod(it)
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
        initialDialog.tooltipPadFromSide = 10f

        val buttonHeight = 24f

        initialDialog.show(width, height) { ui ->
            ui.addPara(FBTxt.txt("max_level"), 0f)
            val maxLevel = ui.addNumericTextField(ui.width, buttonHeight, font = Fonts.DEFAULT_SMALL, initialValue = null, maxValue = officerSkillCount)

            ui.addPara(FBTxt.txt("max_elite_skills"), 0f)
            val maxEliteSkills = ui.addNumericTextField(ui.width, buttonHeight, font = Fonts.DEFAULT_SMALL, initialValue = null, maxValue = officerSkillCount)

            ui.addSpacer(buttonHeight / 3)
            val maxXP = ui.addCheckboxD(FBTxt.txt("max_xp"), isChecked = true)
            val maxSkillPicksPerLevel = ui.addCheckboxD(FBTxt.txt("max_skill_picks_per_level"), isChecked = true)

            ui.addSpacer(8f)
            ui.addPara(FBTxt.txt("personality"), 0f)

            var currentPersonality = "steady"
            val internalPersonalities = listOf("timid", "cautious", "steady", "aggressive", "reckless")
            val externalPersonalities = internalPersonalities.map { FBTxt.txt(it) }

            val toggles = internalPersonalities.indices.map { index ->
                val internalName = internalPersonalities[index]
                val externalName = externalPersonalities.getOrNull(index) ?: "ERROR"

                ui.addCheckboxD(name = externalName, data = internalName, isChecked = internalName == currentPersonality)
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
        missing: MissingContent
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
            selectorPlugin.onKeyDown { event ->
                if (selectorPlugin.autofitSpec == null)
                    return@onKeyDown

                if (event.eventValue == Keyboard.KEY_F2 && UIUtils.isMouseHoveringOverComponent(selectorPlugin.selectorPanel, mouseX = event.x, mouseY = event.y)) {
                    Global.getSettings().showCodex(selectorPlugin.autofitSpec!!.variant.createFleetMember())
                    event.consume()
                }
            }
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
                    getFontPath(Font.INSIGNIA_15),
                    Global.getSettings().brightPlayerColor,
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

                val missing = MissingContentExtended()
                val compiled = PlayerSaveUtils.compileSaveAny(json, missing)

                if (compiled.isEmpty()) {
                    reportMissingContentIfAny(missing)
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
                reportMissingContentIfAny(missing)

                dialog.dismiss()
            }

            dialog.addCloseButton()
        }
    }
}