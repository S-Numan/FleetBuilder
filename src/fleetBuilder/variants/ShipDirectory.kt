package fleetBuilder.variants

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.persistence.variant.CompressedVariant
import fleetBuilder.persistence.variant.CompressedVariant.extractVariantDataFromCompString
import fleetBuilder.persistence.variant.DataVariant
import fleetBuilder.persistence.variant.DataVariant.buildVariantFull
import fleetBuilder.persistence.variant.VariantSettings
import fleetBuilder.util.DisplayMessage
import fleetBuilder.util.getCompatibleDLessHullId
import fleetBuilder.util.getEffectiveHullId
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class ShipEntry(
    val variant: ShipVariantAPI?,
    val variantData: DataVariant.ParsedVariantData,
    val path: String,
    val missingElements: MissingElements,
    val timeSaved: Date,
    val indexInMenu: Int,
    val isImport: Boolean
)

class ShipDirectory(
    val dir: String,
    val configPath: String,
    val prefix: String,
    private val shipEntries: MutableMap<String, ShipEntry>,
    private val description: String,
    private val name: String,
) {
    fun getName(): String {
        return name
    }

    fun getDescription(): String {
        return description
    }

    fun getDescription(variantId: String): String {
        return description + if (isShipImported(variantId)) " (i)" else ""
    }

    fun getRawShipEntries(): Map<String, ShipEntry> {
        return shipEntries
    }

    fun getShip(variantId: String): ShipVariantAPI? {
        return shipEntries[stripPrefix(variantId)]?.variant?.let { cloneWithPrefix(it) }
    }

    fun getShipMissings(variantId: String): MissingElements? {
        return shipEntries[stripPrefix(variantId)]?.missingElements
    }

    fun getShipIndexInMenu(variantId: String): Int {
        return shipEntries[stripPrefix(variantId)]?.indexInMenu ?: -1
    }

    fun getShips(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
        val hullId = hullSpec.getEffectiveHullId()

        return shipEntries.values
            .filter { it.variant != null && it.variant.hullSpec.getEffectiveHullId() == hullId }
            .map { original ->
                cloneWithPrefix(original.variant!!)
            }
    }

    fun isShipImported(variantId: String): Boolean {
        return shipEntries[stripPrefix(variantId)]?.isImport ?: false
    }

    fun getShipPath(variantId: String): String? {
        return shipEntries[stripPrefix(variantId)]?.path
    }

    fun getShipData(variantId: String): DataVariant.ParsedVariantData? {
        return shipEntries[stripPrefix(variantId)]?.variantData
    }

    fun containsShip(variantId: String): Boolean {
        return shipEntries.contains(stripPrefix(variantId))
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
        shipEntries.remove(variantId)
    }

    fun addShip(
        inputVariant: ShipVariantAPI,
        missingFromVariant: MissingElements = MissingElements(),
        settings: VariantSettings = VariantSettings(),
        inputDesiredIndexInMenu: Int = -1,
        editDirectoryFile: Boolean = true,
        editVariantFile: Boolean = true,
        tagAsImport: Boolean = false
    ): String {
        val currentTime = Date()

        val variantToSave = inputVariant.clone()

        variantToSave.hullVariantId = makeVariantID(variantToSave)


        val comp = CompressedVariant.saveVariantToCompString(variantToSave, settings, includePrepend = false)

        //Ensures end result is readable, and uses the saved version of the variant to guarantee consistency across game restarts.
        val parsedVariant = extractVariantDataFromCompString(comp) ?: run {
            DisplayMessage.showError("Failed to save variant", "Failed to extract variant data from comp string after just saving: $comp")
            return ""
        }

        val savedVariant = buildVariantFull(parsedVariant)


        val shipPath = "${savedVariant.hullSpec.getEffectiveHullId()}/${savedVariant.hullVariantId}"

        val newIndex = if (inputDesiredIndexInMenu < 0)
            LoadoutManager.getHighestIndexInEffectiveMenu(variantToSave.hullSpec) + 1
        else
            inputDesiredIndexInMenu

        if (containsShip(savedVariant.hullVariantId)) {
            DisplayMessage.showError("The variantID of ${savedVariant.hullVariantId} already exists in the directory of prefix $prefix . Replacing existing variant.")
        }

        // Save the updated directory
        if (editDirectoryFile)
            updateDirectory(shipPath, currentTime, newIndex, tagAsImport)
        // Save the variant file
        if (editVariantFile)
            Global.getSettings().writeTextFileToCommon("$dir$shipPath", comp)

        val shipEntry = ShipEntry(savedVariant, parsedVariant, shipPath, missingFromVariant, currentTime, newIndex, tagAsImport)

        // Add the variant to this class
        shipEntries[savedVariant.hullVariantId] = shipEntry

        return "${prefix}_${savedVariant.hullVariantId}"
    }

    fun addShip(shipEntry: ShipEntry) {
        val variantID = makeVariantID(shipEntry.variantData.hullId, shipEntry.variantData.displayName)

        val data = shipEntry.variantData.copy(variantId = variantID)
        val variant = shipEntry.variant?.clone()
        if (variant != null)
            variant.hullVariantId = variantID

        val comp = CompressedVariant.saveVariantToCompString(data, includePrepend = false)

        val shipPath = "${shipEntry.path.substringBefore("/")}/${data.variantId}"

        updateDirectory(shipPath, shipEntry.timeSaved, shipEntry.indexInMenu, shipEntry.isImport)
        Global.getSettings().writeTextFileToCommon("$dir${shipPath}", comp)

        shipEntries[data.variantId] = ShipEntry(variant, data, shipPath, shipEntry.missingElements, shipEntry.timeSaved, shipEntry.indexInMenu, shipEntry.isImport)
    }

    private fun updateDirectory(
        shipPath: String,
        currentTime: Date,
        newIndex: Int,
        tagAsImport: Boolean
    ) {
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

        if (tagAsImport)
            shipPathJson.put("isImport", true)

        shipPathsJson.put(shipPathJson)

        shipDirJson.put("shipPaths", shipPathsJson)

        Global.getSettings().writeJSONToCommon(configPath, shipDirJson, false)
    }

    fun getHullSpecIndexes(
        variantToSave: ShipVariantAPI
    ): List<Int> {
        return getShips(variantToSave.hullSpec).map { it.hullVariantId }.map { getShipIndexInMenu(it) }
    }

    fun makeVariantID(variant: ShipVariantAPI): String {
        return makeVariantID(variant.hullSpec.getCompatibleDLessHullId(), variant.displayName)
    }

    fun makeVariantID(hullId: String, displayName: String): String {
        var newVariantId = VariantLib.makeVariantID(hullId, displayName)

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