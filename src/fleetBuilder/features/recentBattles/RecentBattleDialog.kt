package fleetBuilder.features.recentBattles

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.Alignment
import fleetBuilder.core.FBSettings
import fleetBuilder.features.hotkeyHandler.HotkeyHandlerDialogs.pasteFleetDialog
import fleetBuilder.features.recentBattles.fleetDirectory.RBFleetDirectory
import fleetBuilder.features.recentBattles.fleetDirectory.RBFleetDirectoryService
import fleetBuilder.otherMods.starficz.*
import fleetBuilder.ui.customPanel.DialogUtils
import fleetBuilder.ui.customPanel.presets.DialogPanel
import fleetBuilder.ui.customPanel.common.ModalPanel
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.kotlin.isIdle
import java.text.SimpleDateFormat
import java.util.*

// TODO
//  A toggle to show/hide fleets with missing elements.
//  A visual indicator that fleets have missing elements. Maybe a strike through?
//  A DP indicator. This does require building every fleet to see this, so make sure to cache every fleet somewhere while the dialog remains open to avoid having to constantly re-build fleets while navigating the menu.
//      Upon the dialog closing, remove all the cached fleets. (If it's not done already)
//      CTRL Click to copy fleet

object RecentBattleDialog {
    fun recentBattleDialog(event: InputEventAPI, ui: CampaignUIAPI) {
        if (!FBSettings.recentBattleTracker || !ui.isIdle() || ReflectionMisc.isCodexOpen() || DialogUtils.isPopUpPanelOpen())
            return
        val fleetDirectory = RBFleetDirectoryService.getDirectory() ?: return
        showDialog(fleetDirectory)

        event.consume()
    }

    enum class FleetType {
        ALL,
        PERSON_BOUNTY,
        BOUNTY_BOARD,
    }

    private fun showDialog(
        directory: RBFleetDirectory,
    ) {
        var selectedFaction: String? = null
        var sortNewestFirst = true
        var fleetType = FleetType.ALL

        val dialog = DialogPanel()

        dialog.show(width = 1200f, height = 800f) { ui ->

            val allEntries = directory.getRawFleetEntries().values.toList()

            val factions = allEntries
                .mapNotNull { it.fleetData.factionID }
                .toSet()
                .sorted()

            val filtered = allEntries
                .asSequence()
                .filter { entry ->
                    val factionMatches = selectedFaction == null || entry.fleetData.factionID == selectedFaction

                    val typeMatches = when (fleetType) {
                        FleetType.PERSON_BOUNTY ->
                            entry.fleetData.memKeys["\$fleetType"] == "personBounty" && entry.fleetData.memKeys["\$MagicLib_Bounty_target_fleet"] != true
                        FleetType.BOUNTY_BOARD ->
                            entry.fleetData.memKeys["\$MagicLib_Bounty_target_fleet"] == true
                        else -> true
                    }

                    factionMatches && typeMatches
                }
                .sortedBy { it.timeSaved }
                .let { if (sortNewestFirst) it.toList().asReversed() else it.toList() }

            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            // ===== Controls =====
            ui.addSectionHeading("Filters & Sorting", Alignment.MID, 0f).position.setXAlignOffset(0f)

            val factionLabel = selectedFaction ?: "All"

            val factionButton = ui.addButton(
                "Faction: $factionLabel",
                null,
                250f,
                30f,
                5f
            )

            factionButton.onClick {
                val dropDown = ModalPanel()
                dropDown.consumeAllEvents = false
                dropDown.anyOuterMouseClickQuits = true
                dropDown.hotkeyQuitConsumesInput = false

                dropDown.show(
                    width = 300f,
                    height = 400f,
                    xOffset = factionButton.x,
                    yOffset = factionButton.y,
                    withScroller = true
                ) { ddUI ->
                    // ===== "All" option =====
                    ddUI.addButton("All", null, ddUI.width, 28f, 0f).onClick {
                        dropDown.dismiss()
                        selectedFaction = null
                        dialog.recreateUI()
                    }

                    // ===== Faction options =====
                    for (factionId in factions) {
                        val faction = Global.getSector().getFaction(factionId)
                        val displayName = faction?.displayName ?: factionId
                        val baseColor = faction?.baseUIColor ?: Global.getSettings().basePlayerColor
                        val darkColor = faction?.darkUIColor ?: Global.getSettings().darkPlayerColor

                        ddUI.addButton(
                            displayName,
                            null,
                            baseColor,
                            darkColor,
                            ddUI.width,
                            28f,
                            2f
                        ).onClick {
                            dropDown.dismiss()
                            selectedFaction = factionId
                            dialog.recreateUI()
                        }
                    }
                }
            }

            ui.addButton(
                if (fleetType == FleetType.PERSON_BOUNTY) "Fleet Type: Personal Bounty" else if (fleetType == FleetType.BOUNTY_BOARD) "Fleet Type: Bounty Board" else "Fleet Type: All",
                null,
                250f,
                30f,
                5f
            ).onClick {
                fleetType = FleetType.entries[(fleetType.ordinal + 1) % FleetType.entries.size]
                dialog.recreateUI()
            }

            ui.addButton(
                if (sortNewestFirst) "Sort: Newest First" else "Sort: Oldest First",
                null,
                250f,
                30f,
                5f
            ).onClick {
                sortNewestFirst = !sortNewestFirst
                dialog.recreateUI()
            }

            // ===== Scrollable Fleet List =====

            ui.addSectionHeading("Saved Fleets", Alignment.MID, 10f).position.setXAlignOffset(0f)

            val panelHeight = ui.height - 180f

            val scrollPanel = Global.getSettings().createCustom(ui.width, panelHeight, null)

            val listUI = scrollPanel.createUIElement(ui.width, panelHeight, true)
            listUI.addSpacer(0f).position.inTL(0f, 0f)

            if (filtered.isEmpty()) {
                listUI.addPara("No fleets found.", 10f)
            } else {
                for (entry in filtered) {
                    val name = entry.fleetData.fleetName ?: "Unnamed Fleet"
                    val faction = entry.fleetData.factionID ?: "Unknown"
                    val date = formatter.format(entry.timeSaved)

                    val button = listUI.addButton(
                        "$name  [$faction]  ($date)",
                        entry.id,
                        ui.width,
                        28f,
                        3f
                    )

                    button.onClick {
                        pasteFleetDialog(
                            entry.fleetData,
                            entry.missingContent,
                            true
                        )
                    }
                }
            }

            scrollPanel.addUIElement(listUI).inTL(0f, 0f)

            ui.addCustom(scrollPanel, 10f)
        }
    }
}