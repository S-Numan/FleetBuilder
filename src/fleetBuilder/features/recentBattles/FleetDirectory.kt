package fleetBuilder.features.recentBattles

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import fleetBuilder.core.ModSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.fleet.CompressedFleet
import fleetBuilder.serialization.fleet.CompressedFleet.extractFleetDataFromCompString
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.serialization.fleet.DataFleet.filterParsedFleetData
import fleetBuilder.serialization.fleet.FleetSettings
import fleetBuilder.serialization.fleet.JSONFleet
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class FleetEntry(
    val fleetData: DataFleet.ParsedFleetData,
    val path: String,
    val missingElements: MissingElements,
    val timeSaved: Date,
    val dir: FleetDirectory,
    val id: String,
)

class FleetDirectory(
    val dir: String,
    val configPath: String,
) {
    private var fleetEntries: MutableMap<String, FleetEntry> = mutableMapOf()

    fun getRawFleetEntries(): Map<String, FleetEntry> {
        return fleetEntries
    }

    fun setRawFleetEntry(fleetId: String, entry: FleetEntry) {
        fleetEntries[fleetId] = entry
    }

    fun containsFleet(fleetId: String): Boolean {
        return fleetEntries.containsKey(fleetId)
    }

    fun getFleetEntry(fleetId: String): FleetEntry? {
        return fleetEntries[fleetId]
    }

    fun removeFleet(
        fleetId: String,
        editDirectoryFile: Boolean = true,
        editFleetFile: Boolean = true
    ) {
        val entry = fleetEntries[fleetId] ?: return

        if (editDirectoryFile) {
            updateFleetDirectoryJson { fleetsJson ->
                fleetsJson.remove(fleetId)
            }
        }

        if (editFleetFile)
            Global.getSettings().deleteTextFileFromCommon("$dir${entry.path}")

        fleetEntries.remove(fleetId)
    }

    fun addFleet(
        inputFleet: CampaignFleetAPI,
        missingFromFleet: MissingElements = MissingElements(),
        settings: FleetSettings = FleetSettings(),
        editDirectoryFile: Boolean = true,
        editFleetFile: Boolean = true,
        setFleetID: String
    ): String {
        val currentTime = Date()
        var fleetID = setFleetID

        val comp = CompressedFleet.saveFleetToCompString(
            inputFleet,
            settings,
            includePrepend = false
        )

        val parsedFleet = extractFleetDataFromCompString(comp) ?: run {
            DisplayMessage.showError(
                "Failed to save fleet",
                "Failed to extract fleet data from comp string after just saving: $comp"
            )
            return ""
        }

        val comparisonSettings = FleetSettings().apply {
            memberSettings.includeCR = false
            memberSettings.includeHull = false
            memberSettings.personSettings.handleRankAndPost = false
        }

        // DEBUG!
        if (ModSettings.enableDebug) {
            val fleetJSON = JSONFleet.saveFleetToJson(
                inputFleet,
                comparisonSettings
            )
            val fleetUnJSON = JSONFleet.extractFleetDataFromJson(fleetJSON)
            if (fleetUnJSON != filterParsedFleetData(parsedFleet, comparisonSettings)) // If not equal, this means the logic somewhere when saving and getting the fleet to/from JSON or COMP is not correct
                DisplayMessage.showError("DEBUG: Fleet data mismatch", "DEBUG: Fleet data mismatch\n\nfleetUnJSON:\n${fleetUnJSON}\n\nparsedFleet:\n${parsedFleet}")
        }

        var newParsedFleetEntry: DataFleet.ParsedFleetData? = null
        var entryDuplicate = 0
        while (containsFleet(fleetID)) {
            if (newParsedFleetEntry == null)
                newParsedFleetEntry = filterParsedFleetData(parsedFleet, comparisonSettings)

            val fleetEntry = getFleetEntry(fleetID)
            if (fleetEntry != null) {
                val savedParsedFleetEntry = filterParsedFleetData(fleetEntry.fleetData, comparisonSettings)
                if (savedParsedFleetEntry != newParsedFleetEntry) { // Same ID, different entry
                    entryDuplicate++
                    fleetID = setFleetID + "_$entryDuplicate"
                } else { // Exact same fleet entry already exists
                    return fleetID
                }
            }
        }

        val fleetPath = fleetID

        if (editDirectoryFile)
            updateDirectory(fleetID, fleetPath, currentTime)

        if (editFleetFile)
            Global.getSettings().writeTextFileToCommon("$dir$fleetPath", comp)

        val entry = FleetEntry(
            parsedFleet,
            fleetPath,
            missingFromFleet,
            currentTime,
            this,
            fleetID
        )

        fleetEntries[fleetID] = entry

        return fleetID
    }

    fun addFleet(fleetEntry: FleetEntry) {
        val fleetId = fleetEntry.id
        val data = fleetEntry.fleetData.copy()

        val comp = CompressedFleet.saveFleetToCompString(data, includePrepend = false)

        val fleetPath = "${fleetEntry.path.substringBefore("/")}/$fleetId"

        updateDirectory(fleetId, fleetPath, fleetEntry.timeSaved)

        Global.getSettings().writeTextFileToCommon("$dir$fleetPath", comp)

        fleetEntries[fleetId] = FleetEntry(
            data,
            fleetPath,
            fleetEntry.missingElements,
            fleetEntry.timeSaved,
            this,
            fleetId
        )
    }

    private fun updateFleetDirectoryJson(modify: (JSONObject) -> Unit) {
        val fleetDirJson = try {
            Global.getSettings().readJSONFromCommon(configPath, false)
        } catch (e: Exception) {
            DisplayMessage.showError(
                "Failed to update fleet directory.",
                "Failed to read fleet directory at /saves/common/$configPath\n",
                e
            )
            return
        }

        val fleetsJson = fleetDirJson.optJSONObject("fleets") ?: JSONObject()
        modify(fleetsJson)
        fleetDirJson.put("fleets", fleetsJson)

        Global.getSettings().writeJSONToCommon(configPath, fleetDirJson, false)
    }

    private fun updateDirectory(
        fleetId: String,
        fleetPath: String,
        currentTime: Date
    ) {
        updateFleetDirectoryJson { fleetsJson ->
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val timeString = formatter.format(currentTime)

            val obj = JSONObject().apply {
                put("fleetPath", fleetPath)
                put("modifyTime", timeString)
            }

            fleetsJson.put(fleetId, obj)
        }
    }
}