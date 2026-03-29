package fleetBuilder.features.recentBattles

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.Alignment
import fleetBuilder.features.hotkeyHandler.HotkeyHandlerDialogs.pasteFleetDialog
import fleetBuilder.features.recentBattles.fleetDirectory.FleetDirectory
import fleetBuilder.features.recentBattles.fleetDirectory.FleetDirectoryService
import fleetBuilder.otherMods.starficz.onClick
import fleetBuilder.ui.customPanel.common.DialogPanel
import java.text.SimpleDateFormat
import java.util.*

object RecentBattleDialog {
    fun recentBattleDialog() {
        val fleetDirectory = FleetDirectoryService.getDirectory() ?: return
        showDialog(fleetDirectory, null, true)
    }

    private fun showDialog(
        directory: FleetDirectory,
        selectedFaction: String?,
        sortNewestFirst: Boolean
    ) {
        val dialog = DialogPanel()

        val width = 1200f
        val height = 800f

        dialog.show(width = width, height = height) { ui ->

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
            ui.addSectionHeading("Filters & Sorting", Alignment.MID, 10f)

            val factionLabel = selectedFaction ?: "All"

            ui.addButton("Faction: $factionLabel", null, 250f, 30f, 5f).onClick {
                val options = listOf<String?>(null) + factions
                val index = options.indexOf(selectedFaction)
                val next = options[(index + 1) % options.size]

                showDialog(directory, next, sortNewestFirst)
            }

            ui.addButton(
                if (sortNewestFirst) "Sort: Newest First" else "Sort: Oldest First",
                null,
                250f,
                30f,
                5f
            ).onClick {
                showDialog(directory, selectedFaction, !sortNewestFirst)
            }

            // ===== Scrollable Fleet List =====

            ui.addSectionHeading("Saved Fleets", Alignment.MID, 10f)

            val panelHeight = height - 180f

            val scrollPanel = Global.getSettings().createCustom(width, panelHeight, null)

            // TRUE = scrollable
            val listUI = scrollPanel.createUIElement(width - 20f, panelHeight, true)

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
                        width - 40f,
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