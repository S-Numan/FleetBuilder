package fleetBuilder.features.recentBattles.fleetDirectory

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import fleetBuilder.core.ModSettings.DIRECTORYCONFIGNAME
import fleetBuilder.core.ModSettings.FLEETDIR
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.fleet.CompressedFleet.extractFleetDataFromCompString
import fleetBuilder.serialization.fleet.FleetSettings
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object FleetDirectoryService {

    private var fleetDirectory: FleetDirectory? = null

    fun getDirectory(): FleetDirectory? = fleetDirectory

    private const val directory: String = "$FLEETDIR/BattleTracker/"
    fun loadDirectory(): FleetDirectory? {
        // Ensure config exists
        if (!Global.getSettings().fileExistsInCommon("$directory$DIRECTORYCONFIGNAME")) {
            val json = JSONObject()
            json.put("fleets", JSONObject())
            Global.getSettings().writeJSONToCommon("$directory$DIRECTORYCONFIGNAME", json, false)
        }

        val directoryJson: JSONObject = try {
            Global.getSettings().readJSONFromCommon("$directory$DIRECTORYCONFIGNAME", false)
        } catch (e: Exception) {
            DisplayMessage.showError(
                "Failed to read fleet directory",
                "Failed to read fleet directory at /saves/common/$directory$DIRECTORYCONFIGNAME\n",
                e
            )
            return null
        }

        val fleetDir = FleetDirectory(directory)

        val fleetsJson = directoryJson.optJSONObject("fleets") ?: JSONObject()

        val keys = fleetsJson.keys()
        while (keys.hasNext()) {
            val fleetId = keys.next() as? String ?: continue
            val obj = fleetsJson.optJSONObject(fleetId) ?: continue

            val fleetPath = fleetId

            // File existence check
            if (!Global.getSettings().fileExistsInCommon("$directory$fleetPath")) {
                Global.getLogger(this.javaClass).warn(
                    "Fleet file missing at /saves/common/$directory$fleetPath, removing from directory"
                )

                fleetsJson.remove(fleetId)
                continue
            }

            val parsedDate: Date = try {
                val dateString = obj.getString("modifyTime")
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                formatter.parse(dateString) ?: Date(0)
            } catch (_: Exception) {
                Date(0)
            }

            val missing = MissingElements()

            val fleetString: String = try {
                Global.getSettings().readTextFileFromCommon("$directory$fleetPath")
            } catch (e: Exception) {
                Global.getLogger(this.javaClass).error(
                    "Failed to read fleet at /saves/common/$directory$fleetPath",
                    e
                )
                continue
            }

            val parsed = extractFleetDataFromCompString(fleetString)
            if (parsed == null) {
                Global.getLogger(this.javaClass).error(
                    "Failed to parse fleet at /saves/common/$directory$fleetPath"
                )
                continue
            }

            if (fleetDir.containsFleet(fleetId)) {
                throw Error(
                    "Duplicate fleet ID detected: \"$fleetId\" in /saves/common/$directory$DIRECTORYCONFIGNAME"
                )
            }

            fleetDir.setRawFleetEntry(
                fleetId,
                FleetEntry(parsed, fleetPath, missing, parsedDate, fleetDir, fleetId)
            )
        }

        // Save cleaned JSON (in case missing files were removed)
        directoryJson.put("fleets", fleetsJson)
        Global.getSettings().writeJSONToCommon("$directory$DIRECTORYCONFIGNAME", directoryJson, false)

        fleetDirectory = fleetDir
        return fleetDir
    }

    fun saveFleet(
        fleet: CampaignFleetAPI,
        fleetId: String,
        missing: MissingElements = MissingElements(),
        settings: FleetSettings = FleetSettings()
    ): String {
        return fleetDirectory?.addFleet(
            fleet,
            missingFromFleet = missing,
            settings = settings,
            setFleetID = fleetId
        ) ?: ""
    }

    fun deleteFleet(fleetId: String) {
        fleetDirectory?.removeFleet(fleetId)
    }

    fun getFleet(fleetId: String): FleetEntry? {
        return fleetDirectory?.getFleetEntry(fleetId)
    }

    fun getAllFleets(): List<FleetEntry> {
        return fleetDirectory?.getRawFleetEntries()?.values?.toList() ?: emptyList()
    }
}