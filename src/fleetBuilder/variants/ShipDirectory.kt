package fleetBuilder.variants

import fleetBuilder.persistence.VariantSerialization.getVariantFromJson
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.util.containsString
import fleetBuilder.util.getEffectiveHullId
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShipDirectory(
    val dir: String,
    val configPath: String,
    val prefix: String,
    private val ships: MutableMap<String, ShipVariantAPI>,
    private val shipPaths: MutableMap<String, String>,
    private val shipMissings: MutableMap<String, MissingElements>,
    private val description: String,
) {
    fun getDescription(): String {
        return description
    }
    fun getShip(variantId: String): ShipVariantAPI? {
        val baseId = stripPrefix(variantId)
        return ships[baseId]?.let { cloneWithPrefix(it) }
        /*return ships[baseId]?.clone()?.apply {
            hullVariantId = "${prefix}_$baseId"
        }*/
    }
    fun getShipMissingAnything(variantId: String): Boolean {
        return shipMissings[stripPrefix(variantId)]?.hasMissing() ?: true
    }
    fun getShipMissings(variantId: String): MissingElements? {
        return shipMissings[stripPrefix(variantId)]
    }
    fun getShips(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
        val hullId = hullSpec.getEffectiveHullId()

        return ships.values
            .filter { it.hullSpec.getEffectiveHullId() == hullId }
            .map { original ->
                cloneWithPrefix(original)
                /*original.clone().apply {
                    hullVariantId = "${prefix}_${original.hullVariantId}"
                }*/
            }
    }
    fun getShipPath(variantId: String): String? {
        return shipPaths[stripPrefix(variantId)]
    }
    fun containsShip(variantId: String): Boolean {
        return ships.contains(stripPrefix(variantId))
    }
    fun removeShip(_variantId: String) {
        val variantId = stripPrefix(_variantId)
        if (!containsShip(variantId)) return

        val shipPath = getShipPath(variantId)

        // Read the ship directory JSON
        val shipDirJson = Global.getSettings().readJSONFromCommon(configPath, false)
        val jsonArray = shipDirJson.optJSONArray("shipPaths") ?: JSONArray()

        // Remove the shipPath from the array
        for (i in jsonArray.length() - 1 downTo 0) {
            if (jsonArray.optString(i) == shipPath) jsonArray.remove(i)
        }
        shipDirJson.put("shipPaths", jsonArray)

        // Save the updated directory
        Global.getSettings().writeJSONToCommon(configPath, shipDirJson, false)

        // Delete the variant file
        Global.getSettings().deleteTextFileFromCommon("$dir$shipPath")

        // Remove the variant from this class
        ships.remove(variantId)
        shipPaths.remove(variantId)
        shipMissings.remove(variantId)
    }
    fun addShip(variant: ShipVariantAPI, missingFromVariant: MissingElements = MissingElements(), applySMods: Boolean = true, includeDMods: Boolean = true, includeTags: Boolean = true, includeTime: Boolean = true): String{
        val variantToSave = variant.clone()
        variantToSave.hullVariantId = makeVariantID(variant)

        val json = saveVariantToJson(variantToSave, applySMods, includeDMods, includeTags)

        //Ensures the JSON is readable, and uses the saved version of the variant to guarantee consistency across game restarts.
        val savedVariant = getVariantFromJson(json)

        //Add time saved to file
        if(includeTime) {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val currentTime = Date()
            val timeString = formatter.format(currentTime)
            json.put("timeSaved", timeString)

            //Example read time
            //val parsedDate: Date = formatter.parse(timeString)
            //val millis = parsedDate.time  // Get milliseconds since epoch if needed
            //millis
        }

        val shipPath = "${variant.hullSpec.getEffectiveHullId()}/${savedVariant.hullVariantId}"

        if(containsShip(savedVariant.hullVariantId)) {
            Global.getLogger(this.javaClass).error("The variantID of ${savedVariant.hullVariantId} already exists in the directory of prefix $prefix . Replacing existing variant.")
        }

        // Read the ship directory JSON
        val shipDirJson = Global.getSettings().readJSONFromCommon(configPath, false)
        val jsonArray = shipDirJson.optJSONArray("shipPaths") ?: JSONArray()

        // Add the new ship path
        if(!jsonArray.containsString(shipPath)) {
            jsonArray.put(shipPath)
            shipDirJson.put("shipPaths", jsonArray)
        } else {
            Global.getLogger(this.javaClass).error("$shipPath already exists in JSONArray when adding ship. The new file will be overwritten.")
        }

        // Save the updated directory
        Global.getSettings().writeJSONToCommon(configPath, shipDirJson, false)

        // Write the variant file
        Global.getSettings().writeJSONToCommon("$dir$shipPath", json, false)

        // Add the variant to this class
        shipPaths[savedVariant.hullVariantId] = shipPath
        ships[savedVariant.hullVariantId] = savedVariant
        shipMissings[savedVariant.hullVariantId] = missingFromVariant

        return "${prefix}_${savedVariant.hullVariantId}"
    }

    fun makeVariantID(variant: ShipVariantAPI): String {
        var newVariantId = VariantLib.makeVariantID(variant)

        var iterate = 0
        // Ensure the variant ID is unique
        if(containsShip(newVariantId)){
            while(containsShip(newVariantId + "_$iterate")){
                iterate++
            }
            newVariantId += "_$iterate"
        }

        return newVariantId
    }

    private fun stripPrefix(id: String): String = id.removePrefix("${prefix}_")

    private fun cloneWithPrefix(original: ShipVariantAPI): ShipVariantAPI =
        original.clone().apply { hullVariantId = "${prefix}_${original.hullVariantId}" }
}