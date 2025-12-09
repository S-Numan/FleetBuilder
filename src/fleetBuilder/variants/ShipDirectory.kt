package fleetBuilder.variants

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.config.ModSettings
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
    val isImport: Boolean,
    val dir: ShipDirectory,
)

class ShipDirectory(
    val dir: String,
    val configPath: String,
    val prefix: String,
    val name: String,
    val description: String,
) {
    private var shipEntries: MutableMap<String, ShipEntry> = mutableMapOf()
    fun getRawShipEntries(): Map<String, ShipEntry> {
        return shipEntries
    }

    fun setRawShipEntry(variantId: String, entry: ShipEntry) {
        shipEntries[variantId] = entry
    }

    fun containsShip(variantId: String): Boolean {
        return shipEntries.containsKey(variantId)
    }

    fun getDescription(variantId: String): String {
        return description + if (isShipImported(variantId)) " (I)" else ""
    }

    fun getShipEntry(variantId: String): ShipEntry? {
        val entry = shipEntries[variantId] ?: return null

        return entry.copy(
            variant = entry.variant?.clone(),
            variantData = entry.variantData
        )
    }

    fun getShip(variantId: String): ShipVariantAPI? {
        return shipEntries[variantId]?.variant?.clone()
    }

    fun getShips(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
        val hullId = hullSpec.getEffectiveHullId()

        return shipEntries.values
            .filter { it.variant != null && it.variant.hullSpec.getEffectiveHullId() == hullId }
            .map { it.variant!!.clone() }
    }

    fun getShipIndexInMenu(variantId: String): Int {
        return shipEntries[variantId]?.indexInMenu ?: -1
    }

    fun isShipImported(variantId: String): Boolean {
        return shipEntries[variantId]?.isImport ?: false
    }

    fun removeShip(
        variantId: String,
        editDirectoryFile: Boolean = true,
        editVariantFile: Boolean = true
    ) {
        val entry = shipEntries[variantId] ?: return

        // Save the updated directory
        if (editDirectoryFile) {
            updateShipDirectoryJson { shipPathsJson ->
                containsAndRemoveShipName(shipPathsJson, entry.path)
            }
        }

        // Delete the variant file
        if (editVariantFile)
            Global.getSettings().deleteTextFileFromCommon("$dir${entry.path}")

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
        tagAsImport: Boolean = false,
        setVariantID: String? = null
    ): String {
        val currentTime = Date()

        val variantToSave = inputVariant.clone().apply {
            hullVariantId = setVariantID ?: makeVariantID(inputVariant)
        }

        val comp = CompressedVariant.saveVariantToCompString(variantToSave, settings, includePrepend = false)

        //Ensures end result is readable, and uses the saved version of the variant to guarantee consistency across game restarts.
        val parsedVariant = extractVariantDataFromCompString(comp) ?: run {
            DisplayMessage.showError("Failed to save variant", "Failed to extract variant data from comp string after just saving: $comp")
            return ""
        }

        val savedVariant = buildVariantFull(parsedVariant)


        val shipPath = "${savedVariant.hullSpec.getEffectiveHullId()}/${savedVariant.hullVariantId}"

        val newIndex = if (inputDesiredIndexInMenu < 0)
            LoadoutManager.getHighestIndexInEffectiveMenu(ModSettings.defaultPrefix, variantToSave.hullSpec) + 1
        else
            inputDesiredIndexInMenu

        if (containsShip(savedVariant.hullVariantId))
            DisplayMessage.showError("The variantID of ${savedVariant.hullVariantId} already exists in the directory of prefix $prefix . Replacing existing variant.")

        // Save the updated directory
        if (editDirectoryFile)
            updateDirectory(shipPath, currentTime, newIndex, tagAsImport)
        // Save the variant file
        if (editVariantFile)
            Global.getSettings().writeTextFileToCommon("$dir$shipPath", comp)

        savedVariant.addTag("#PREFIX_$prefix")
        val shipEntry = ShipEntry(savedVariant, parsedVariant, shipPath, missingFromVariant, currentTime, newIndex, tagAsImport, this)

        // Add the variant to this class
        shipEntries[savedVariant.hullVariantId] = shipEntry

        return savedVariant.hullVariantId
    }

    fun addShip(shipEntry: ShipEntry) {
        val variantID = makeVariantID(shipEntry.variantData.hullId, shipEntry.variantData.displayName)

        val data = shipEntry.variantData.copy(variantId = variantID)
        val variant = shipEntry.variant?.clone()?.apply { hullVariantId = variantID }

        val comp = CompressedVariant.saveVariantToCompString(data, includePrepend = false)

        val shipPath = "${shipEntry.path.substringBefore("/")}/${data.variantId}"

        updateDirectory(shipPath, shipEntry.timeSaved, shipEntry.indexInMenu, shipEntry.isImport)
        Global.getSettings().writeTextFileToCommon("$dir${shipPath}", comp)

        variant?.tags?.toList()?.forEach { if (it.startsWith("#PREFIX_")) variant.removeTag(it) }
        variant?.addTag("#PREFIX_$prefix")
        shipEntries[data.variantId] = ShipEntry(variant, data, shipPath, shipEntry.missingElements, shipEntry.timeSaved, shipEntry.indexInMenu, shipEntry.isImport, this)
    }

    private fun updateShipDirectoryJson(modify: (JSONArray) -> Unit) {
        val shipDirJson = try {
            Global.getSettings().readJSONFromCommon(configPath, false)
        } catch (e: Exception) {
            DisplayMessage.showError("Failed to update ship directory. File was likely changed during runtime!", "Failed to update ship directory. File was likely changed during runtime!\nFailed to read ship directory at /saves/common/$configPath\n", e)
            return
        }

        val shipPathsJson = shipDirJson.optJSONArray("shipPaths") ?: JSONArray()
        modify(shipPathsJson)
        shipDirJson.put("shipPaths", shipPathsJson)
        Global.getSettings().writeJSONToCommon(configPath, shipDirJson, false)
    }

    private fun updateDirectory(
        shipPath: String,
        currentTime: Date,
        newIndex: Int,
        tagAsImport: Boolean
    ) {
        updateShipDirectoryJson { shipPathsJson ->
            if (containsAndRemoveShipName(shipPathsJson, shipPath)) {
                DisplayMessage.showError("$shipPath already existed in JSONArray. The old file will be overwritten.")
            }

            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val timeString = formatter.format(currentTime)

            val shipPathJson = JSONObject().apply {
                put("shipPath", shipPath)
                put("modifyTime", timeString)
                put("desiredIndexInMenu", newIndex)
                if (tagAsImport)
                    put("isImport", true)
            }

            shipPathsJson.put(shipPathJson)
        }
    }

    fun getHullSpecIndexes(
        variantToSave: ShipVariantAPI
    ): List<Int> {
        return getShips(variantToSave.hullSpec).map { it.hullVariantId }.map { getShipIndexInMenu(it) }
    }

    fun makeVariantID(variant: ShipVariantAPI): String {
        return makeVariantID(variant.hullSpec.getCompatibleDLessHullId(true), variant.displayName)
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

    //private val fullPrefix = "${prefix}_"
    //private fun String.stripPrefix() = removePrefix(fullPrefix)
    //private fun ShipVariantAPI.cloneWithPrefix() =
    //    clone().apply { hullVariantId = hullVariantId.prependPrefix() }

    //private fun String.prependPrefix() =
    //    fullPrefix + this
}