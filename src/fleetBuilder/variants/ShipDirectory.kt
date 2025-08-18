package fleetBuilder.variants

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.persistence.variant.JSONVariant.getVariantFromJson
import fleetBuilder.persistence.variant.JSONVariant.saveVariantToJson
import fleetBuilder.persistence.variant.VariantSettings
import fleetBuilder.util.DisplayMessage
import fleetBuilder.util.getEffectiveHullId
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

//TODO, make a data class to handle each of these maps. A single map should link to each of these

class ShipDirectory(
    val dir: String,
    val configPath: String,
    val prefix: String,
    private val ships: MutableMap<String, ShipVariantAPI>,
    private val shipPaths: MutableMap<String, String>,
    private val shipMissings: MutableMap<String, MissingElements>,
    private val shipTimeSaved: MutableMap<String, Date>,
    private val shipIndexInMenu: MutableMap<String, Int>,
    private val description: String,
) {
    fun getAllVariants(): Collection<ShipVariantAPI> {
        return ships.values
    }

    fun getDescription(): String {
        return description
    }

    fun getShip(variantId: String): ShipVariantAPI? {
        val baseId = stripPrefix(variantId)
        return ships[baseId]?.let { cloneWithPrefix(it) }
    }

    fun getShipMissingAnything(variantId: String): Boolean {
        return shipMissings[stripPrefix(variantId)]?.hasMissing() ?: true
    }

    fun getShipMissings(variantId: String): MissingElements? {
        return shipMissings[stripPrefix(variantId)]
    }

    fun getShipIndexInMenu(variantId: String): Int {
        return shipIndexInMenu[stripPrefix(variantId)] ?: -1
    }

    fun getShips(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
        val hullId = hullSpec.getEffectiveHullId()

        return ships.values
            .filter { it.hullSpec.getEffectiveHullId() == hullId }
            .map { original ->
                cloneWithPrefix(original)
            }
    }

    fun getShipPath(variantId: String): String? {
        return shipPaths[stripPrefix(variantId)]
    }

    fun containsShip(variantId: String): Boolean {
        return ships.contains(stripPrefix(variantId))
    }

    fun removeShip(
        _variantId: String,
        editDirectoryFile: Boolean = true,
        editVariantFile: Boolean = true
    ) {
        val variantId = stripPrefix(_variantId)
        if (!containsShip(variantId)) return

        val shipPath = getShipPath(variantId)

        // Save the updated directory
        if (editDirectoryFile) {
            // Read the ship directory JSON
            val shipDirJson = Global.getSettings().readJSONFromCommon(configPath, false)
            val shipPathsJson = shipDirJson.optJSONArray("shipPaths") ?: JSONArray()

            // Remove the shipPath from the array
            if (shipPath != null)
                containsAndRemoveShipName(shipPathsJson, shipPath)
            else
                DisplayMessage.showError("shipPath was null when attempting to remove it")

            shipDirJson.put("shipPaths", shipPathsJson)

            Global.getSettings().writeJSONToCommon(configPath, shipDirJson, false)
        }

        // Delete the variant file
        if (editVariantFile)
            Global.getSettings().deleteTextFileFromCommon("$dir$shipPath")

        // Remove the variant from this class
        ships.remove(variantId)
        shipPaths.remove(variantId)
        shipMissings.remove(variantId)
        shipTimeSaved.remove(variantId)
        shipIndexInMenu.remove(variantId)
    }

    fun addShip(
        inputVariant: ShipVariantAPI,
        missingFromVariant: MissingElements = MissingElements(),
        settings: VariantSettings = VariantSettings(),
        inputDesiredIndexInMenu: Int = -1,
        editDirectoryFile: Boolean = true,
        editVariantFile: Boolean = true,
        setVariantID: String? = null
    ): String {
        val currentTime = Date()

        val variantToSave = inputVariant.clone()
        if (setVariantID != null)
            variantToSave.hullVariantId = setVariantID
        else
            variantToSave.hullVariantId = makeVariantID(inputVariant)

        val json = saveVariantToJson(variantToSave, settings)

        //Ensures the JSON is readable, and uses the saved version of the variant to guarantee consistency across game restarts.
        val savedVariant = getVariantFromJson(json)

        val shipPath = "${savedVariant.hullSpec.getEffectiveHullId()}/${savedVariant.hullVariantId}"

        if (containsShip(savedVariant.hullVariantId)) {
            DisplayMessage.showError("The variantID of ${savedVariant.hullVariantId} already exists in the directory of prefix $prefix . Replacing existing variant.")
        }

        val newIndex = if (inputDesiredIndexInMenu < 0)
            LoadoutManager.getHighestIndexInEffectiveMenu(variantToSave.hullSpec) + 1
        else
            inputDesiredIndexInMenu

        // Save the updated directory
        if (editDirectoryFile) {

            // Read the ship directory JSON
            val shipDirJson = Global.getSettings().readJSONFromCommon(configPath, false)
            val shipPathsJson = shipDirJson.optJSONArray("shipPaths") ?: JSONArray()

            // Add the new ship path
            if (containsAndRemoveShipName(shipPathsJson, shipPath))
                DisplayMessage.showError("$shipPath already exists in JSONArray when adding ship. The old file with be overwritten.")


            val shipPathJson = JSONObject()
            shipPathJson.put("shipPath", shipPath)

            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val timeString = formatter.format(currentTime)
            shipPathJson.put("modifyTime", timeString)

            shipPathJson.put("desiredIndexInMenu", newIndex)

            shipPathsJson.put(shipPathJson)

            shipDirJson.put("shipPaths", shipPathsJson)

            Global.getSettings().writeJSONToCommon(configPath, shipDirJson, false)
        }
        // Save the variant file
        if (editVariantFile)
            Global.getSettings().writeJSONToCommon("$dir$shipPath", json, false)

        // Add the variant to this class
        shipPaths[savedVariant.hullVariantId] = shipPath
        ships[savedVariant.hullVariantId] = savedVariant
        shipMissings[savedVariant.hullVariantId] = missingFromVariant
        shipTimeSaved[savedVariant.hullVariantId] = currentTime
        shipIndexInMenu[savedVariant.hullVariantId] = newIndex

        return "${prefix}_${savedVariant.hullVariantId}"
    }

    fun getHullSpecIndexes(
        variantToSave: ShipVariantAPI
    ): List<Int> {
        return getShips(variantToSave.hullSpec).map { it.hullVariantId }.map { getShipIndexInMenu(it) }
    }

    fun makeVariantID(variant: ShipVariantAPI): String {
        var newVariantId = VariantLib.makeVariantID(variant)

        var iterate = 0
        // Ensure the variant ID is unique
        if (containsShip(newVariantId)) {
            while (containsShip(newVariantId + "_$iterate")) {
                iterate++
            }
            newVariantId += "_$iterate"
        }

        return newVariantId
    }

    private fun stripPrefix(id: String): String = id.removePrefix("${prefix}_")

    private fun cloneWithPrefix(original: ShipVariantAPI): ShipVariantAPI =
        original.clone().apply { hullVariantId = "${prefix}_${original.hullVariantId}" }

    private fun containsAndRemoveShipName(shipPaths: JSONArray, targetName: String): Boolean {
        for (i in 0 until shipPaths.length()) {
            val obj = shipPaths.optJSONObject(i) ?: continue
            val name = obj.optString("shipPath", null)
            if (name == targetName) {
                shipPaths.remove(i)
                return true
            }
        }
        return false
    }
}