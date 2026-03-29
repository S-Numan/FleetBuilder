package fleetBuilder.features.recentBattles

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.Alignment
import fleetBuilder.core.FBSettings
import fleetBuilder.features.hotkeyHandler.HotkeyHandlerDialogs.pasteFleetDialog
import fleetBuilder.features.recentBattles.fleetDirectory.FleetDirectory
import fleetBuilder.features.recentBattles.fleetDirectory.FleetDirectoryService
import fleetBuilder.otherMods.starficz.onClick
import fleetBuilder.otherMods.starficz.width
import fleetBuilder.otherMods.starficz.x
import fleetBuilder.otherMods.starficz.y
import fleetBuilder.ui.customPanel.common.DialogPanel
import fleetBuilder.ui.customPanel.common.ModalPanel
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.isIdle
import java.text.SimpleDateFormat
import java.util.*

object RecentBattleDialog {
    fun recentBattleDialog(event: InputEventAPI, ui: CampaignUIAPI) {
        if (!FBSettings.recentBattleTracker || !ui.isIdle() || ReflectionMisc.isCodexOpen())
            return
        val fleetDirectory = FleetDirectoryService.getDirectory() ?: return
        showDialog(fleetDirectory, null, true)

        event.consume()
    }

    private fun showDialog(
        directory: FleetDirectory,
        selectedFaction: String?,
        sortNewestFirst: Boolean
    ) {
        val dialog = DialogPanel()



        dialog.show(width = 1200f, height = 800f) { ui ->
            val allEntries = directory.getRawFleetEntries().values.toList()

            val factions = allEntries
                .mapNotNull { it.fleetData.factionID }
                .toSet()
                .sorted()

            val filtered = allEntries
                .filter {
                    selectedFaction == null || it.fleetData.factionID == selectedFaction
                }
                .sortedBy { it.timeSaved }
                .let { if (sortNewestFirst) it.reversed() else it }

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
                        dialog.forceDismiss(false)
                        showDialog(directory, null, sortNewestFirst)
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
                            dialog.forceDismiss(false)
                            showDialog(directory, factionId, sortNewestFirst)
                        }
                    }
                }
            }

            ui.addButton(
                if (sortNewestFirst) "Sort: Newest First" else "Sort: Oldest First",
                null,
                250f,
                30f,
                5f
            ).onClick {
                dialog.forceDismiss(false)
                showDialog(directory, selectedFaction, !sortNewestFirst)
            }

            // ===== Scrollable Fleet List =====

            ui.addSectionHeading("Saved Fleets", Alignment.MID, 10f).position.setXAlignOffset(0f)

            val panelHeight = ui.width - 180f

            val scrollPanel = Global.getSettings().createCustom(ui.width, panelHeight, null)

            // TRUE = scrollable
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
                            entry.missingElements,
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