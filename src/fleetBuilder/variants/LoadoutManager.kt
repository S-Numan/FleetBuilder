package fleetBuilder.variants

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.config.ModSettings
import fleetBuilder.config.ModSettings.DIRECTORYCONFIGNAME
import fleetBuilder.config.ModSettings.FLEETDIR
import fleetBuilder.config.ModSettings.PACKDIR
import fleetBuilder.config.ModSettings.defaultPrefix
import fleetBuilder.persistence.variant.DataVariant
import fleetBuilder.persistence.variant.VariantSettings
import fleetBuilder.ui.autofit.AutofitSpec
import fleetBuilder.util.DisplayMessage
import fleetBuilder.util.FBMisc.extractDataFromString
import fleetBuilder.variants.VariantLib.compareVariantContents
import fleetBuilder.variants.VariantLib.getCoreVariantsForEffectiveHullspec
import org.apache.log4j.Level
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Color
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

object LoadoutManager {

    private val shipDirectories: MutableList<ShipDirectory> = mutableListOf()
    fun getShipDirectories(): List<ShipDirectory> = shipDirectories
    fun getShipDirectoriesNotEmpty(): List<ShipDirectory> = shipDirectories.filter { it.getRawShipEntries().isNotEmpty() }

    fun generatePrefixes(): List<String> {
        val letters = ('A'..'Z') + ('a'..'z')

        return letters.flatMap { c1 ->
            listOf(c1.toString()) + letters.map { c2 -> "$c1$c2" }
        }
    }

    private fun setupDefaultDirectory() {
        val json = JSONObject()
        json.put("description", "Default Loadout")
        json.put("name", "Default")
        Global.getSettings().writeJSONToCommon("$PACKDIR$defaultPrefix/$DIRECTORYCONFIGNAME", json, false)
    }

    fun loadAllDirectories() {

        //Make directories
        Global.getSettings().writeTextFileToCommon("$FLEETDIR/deleteme", ".")
        Global.getSettings().deleteTextFileFromCommon("$FLEETDIR/deleteme")
        Global.getSettings().writeTextFileToCommon("${PACKDIR}IN/deleteme", ".")
        Global.getSettings().deleteTextFileFromCommon("${PACKDIR}IN/deleteme")

        // Ensure default exists

        if (!Global.getSettings().fileExistsInCommon("$PACKDIR$defaultPrefix/$DIRECTORYCONFIGNAME")) {
            setupDefaultDirectory()
        }

        shipDirectories.clear()

        // Load all prefixed ship directories
        generatePrefixes().forEach { prefix ->
            loadShipDirectory(PACKDIR, prefix)
        }

        // Import all import directory variants into default directory, then remove import directory.
        val importDirectory = shipDirectories.find { it.prefix == "IN" }
        val defaultDirectory = shipDirectories.find { it.prefix == defaultPrefix }
        if (importDirectory != null && defaultDirectory != null) {
            Global.getLogger(this.javaClass).info("Importing IN directory variants into $defaultPrefix directory")

            importDirectory.getRawShipEntries().forEach { (_, entry) ->
                defaultDirectory.addShip(entry.copy(isImport = true))
            }

            shipDirectories.remove(importDirectory)
            Global.getSettings().deleteTextFileFromCommon("${PACKDIR}IN/$DIRECTORYCONFIGNAME")

            Global.getLogger(this.javaClass).info("Deleting IN directory")
        }
    }

    fun loadShipDirectory(dirPath: String, prefix: String): ShipDirectory? {

        val configFilePath = "$dirPath$prefix/$DIRECTORYCONFIGNAME"
        if (!Global.getSettings().fileExistsInCommon(configFilePath)) {
            return null
        }

        if (shipDirectories.any { it.dir + it.prefix == "$dirPath$prefix" }) {
            throw Error(
                "Loadout pack name conflict.\n" +
                        "The prefix '$prefix' is already taken. You must rename the folder " +
                        "'/saves/common/$dirPath$prefix' to something other than '$prefix', as '$prefix' is already in use"
            )
        }

        val directory: JSONObject = try {
            Global.getSettings().readJSONFromCommon(configFilePath, false)
        } catch (e: Exception) {
            var _directory: JSONObject? = null

            val message = buildString {
                appendLine("Failed to read '$prefix' loadout directory at /saves/common/$configFilePath")
                appendLine("All previous loadouts in this loadout directory are unable to be accessed.")

                if (prefix == defaultPrefix) {
                    appendLine("A new loadout directory will be made to prevent autofit functionality from failing.")

                    val oldFile = runCatching { Global.getSettings().readTextFileFromCommon(configFilePath) }.getOrNull()

                    if (!oldFile.isNullOrBlank()) {
                        appendLine("Appending old loadout directory with -CORRUPT")
                        appendLine("Consider fixing the formatting in the -CORRUPT file and replacing the non -CORRUPT file with it.")

                        Global.getSettings().writeTextFileToCommon("$configFilePath-CORRUPT", oldFile)
                    }

                    setupDefaultDirectory()

                    _directory = runCatching { Global.getSettings().readJSONFromCommon(configFilePath, false) }.getOrNull()
                        ?: return null // Safety check
                } else {
                    appendLine("Loadout directory will remain inaccessible until the issue is resolved.")
                }

                appendLine()
                appendLine()
                appendLine(e.toString())
            }

            DisplayMessage.dialogMessage(
                "Failed to read the $prefix loadout directory",
                message
            )
            DisplayMessage.logMessage(message, Level.ERROR)

            _directory ?: return null
        }

        return try {
            upgradeLegacyShipPaths(directory, configFilePath)

            val name = directory.optString("name", prefix)
            val description = directory.optString("description", "$prefix Loadout")

            val shipDirectory = ShipDirectory("$dirPath$prefix/", configFilePath, prefix, name = name, description = description)

            directory.optJSONArray("shipPaths")?.let { shipJsonPaths ->
                val seenPaths = mutableSetOf<String>()

                for (i in 0 until shipJsonPaths.length()) {
                    val shipJson = shipJsonPaths.getJSONObject(i)

                    val shipPath = shipJson.getString("shipPath")

                    val parsedDate: Date = try {
                        val dateString = shipJson.getString("modifyTime")
                        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        formatter.parse(dateString) ?: Date(0)
                    } catch (_: Exception) {
                        Date(0) // Oldest possible date (Unix epoch start)
                    }

                    val parsedEffectiveIndex = shipJson.optInt("desiredIndexInMenu", -1)

                    val parsedIsImport = shipJson.optBoolean("isImport", false)

                    // Check for duplicate ship path
                    if (!seenPaths.add(shipPath)) {
                        throw Error("Duplicate ship path detected: \"$shipPath\" in \"/saves/common/$configFilePath\"\nRemove the duplicates to continue.")
                    }

                    var data: DataVariant.ParsedVariantData? = null
                    val missing = MissingElements()

                    if (Global.getSettings().fileExistsInCommon("$dirPath$prefix/$shipPath")) {
                        val variantString: String
                        try {
                            variantString = Global.getSettings().readTextFileFromCommon("$dirPath$prefix/$shipPath")
                        } catch (e: Exception) {
                            Global.getLogger(this.javaClass).error(
                                "Failed to read ship variant at /saves/common/$dirPath$prefix/$shipPath\n",
                                e
                            )
                            continue
                        }
                        val tempData = extractDataFromString(variantString)
                        if (tempData == null) {
                            Global.getLogger(this.javaClass).error("Failed to get ship variant at /saves/common/$dirPath$prefix/$shipPath\n")
                            continue
                        } else if (tempData is DataVariant.ParsedVariantData) {
                            data = tempData
                        } else {
                            Global.getLogger(this.javaClass).error("Ship variant was not of data type ParsedVariantData at /saves/common/$dirPath$prefix/$shipPath\nHow?")
                            continue
                        }
                    } else {//Failed to find ship at specific path.
                        Global.getLogger(this.javaClass)
                            .warn("shipPath in path directory /saves/common/$dirPath$prefix linked to ship variant at /saves/common/$dirPath$prefix/$shipPath\nHowever, no file was found at that location")
                        //Global.getLogger(this.javaClass).info("Failed to find ship with path $dirPath$prefix/$shipPath . Removing from directory")
                        //TODO, remove this shipPath from shipPaths
                        continue
                    }

                    if (shipDirectory.containsShip(data.variantId)) {
                        throw Error(
                            "Duplicate variant ID in ships directory $prefix\n" +
                                    "Ship path 1: $dirPath$prefix/$shipPath\n" +
                                    "Ship path 2: $dirPath$prefix/${shipDirectory.getShipEntry(data.variantId)?.path}\n" +
                                    "The variantID must be changed on one or the other, or one must be removed."
                        )
                    }

                    if (data.hullId !in VariantLib.getHullIDSet() // Could not find hullId. Most likely it is a hullspec from a mod which was disabled.
                        || data.moduleVariants.any { it.value.hullId !in VariantLib.getHullIDSet() } // Also check hullIds from modules
                    ) {
                        shipDirectory.setRawShipEntry(data.variantId, ShipEntry(null, data, shipPath, missing, parsedDate, parsedEffectiveIndex, parsedIsImport, shipDirectory))
                    } else {
                        val variant = DataVariant.buildVariantFull(data, missing = missing)
                        variant.addTag("#PREFIX_$prefix")
                        shipDirectory.setRawShipEntry(data.variantId, ShipEntry(variant, data, shipPath, missing, parsedDate, parsedEffectiveIndex, parsedIsImport, shipDirectory))
                    }
                }
            }

            shipDirectories.add(shipDirectory)

            // Assure indexes aren't missing
            shipDirectory.getRawShipEntries().map { it.value.variant }.forEach { variant ->
                if (variant == null) return@forEach

                fun remakeShip() {
                    val missing = shipDirectory.getShipEntry(variant.hullVariantId)?.missingElements ?: return
                    val isImport = shipDirectory.isShipImported(variant.hullVariantId)
                    shipDirectory.removeShip(variant.hullVariantId, editVariantFile = false)
                    shipDirectory.addShip(
                        variant,
                        setVariantID = variant.hullVariantId,
                        missingFromVariant = missing, editVariantFile = false, tagAsImport = isImport
                    )
                    Global.getLogger(this.javaClass).warn("Rebuilding variant ${variant.hullVariantId} to add new index, as index was missing")
                }

                val thisIndex = shipDirectory.getShipIndexInMenu(variant.hullVariantId)
                if (thisIndex < 0) { // Missing?
                    remakeShip()
                } //else if (shipDirectory.getHullSpecIndexes(variant, thisIndex) != thisIndex) {
                //    remakeShip(thisIndex)
                //}
            }

            shipDirectory
        } catch (e: Exception) {
            Global.getLogger(this.javaClass).error("Failed to read JSON from within /saves/common/$dirPath\n", e)
            null
        }
    }

    private fun upgradeLegacyShipPaths(directory: JSONObject, configFilePath: String) {

        // Convert pre-1.4.0 ship dir files into post-1.4.0 format if needed.
        val isPreOnePointFour: Boolean = directory.optJSONArray("shipPaths")?.let { shipJsonPaths ->
            (0 until shipJsonPaths.length()).any { i ->
                val item = shipJsonPaths.get(i)
                item is String && item.isNotEmpty()
            }
        } ?: false

        if (isPreOnePointFour) {
            val oldArray = directory.optJSONArray("shipPaths")
            val newArray = JSONArray()

            for (i in 0 until oldArray.length()) {
                val path = oldArray.optString(i, "")
                if (path.isNotEmpty()) {
                    val obj = JSONObject()
                    obj.put("shipPath", path)

                    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    val timeString = formatter.format(Date(0))
                    obj.put("modifyTime", timeString)

                    newArray.put(obj)
                }
            }

            // Replace the old array with the new formatted one
            directory.put("shipPaths", newArray)
        }



        Global.getSettings().writeJSONToCommon(configFilePath, directory, false)
    }


    fun getCoreAutofitSpecsForShip(hullSpec: ShipHullSpecAPI, indexOffset: Int = 0): List<AutofitSpec> {
        val variants = getCoreVariantsForEffectiveHullspec(hullSpec)

        val coreVariantSpecs = mutableListOf<AutofitSpec>()

        var variantCount = 0
        variants.forEach { variant ->
            val shouldShow = when {
                variant.isGoalVariant -> ModSettings.showCoreGoalVariants
                else -> ModSettings.showCoreNonGoalVariants
            }

            if (shouldShow) {
                val label = if (variant.isGoalVariant) "Goal Variant" else "Core Variant"
                coreVariantSpecs.add(
                    AutofitSpec(
                        variant,
                        source = null,
                        desiredIndexInMenu = variantCount + indexOffset,
                        label
                    )
                )
                variantCount++
            }
        }

        return coreVariantSpecs
    }

    fun getLoadoutAutofitSpecsForShip(
        prefix: String,
        hullSpec: ShipHullSpecAPI,
        indexOffset: Int = 0
    ): List<AutofitSpec> {
        val autofitSpecs = mutableListOf<AutofitSpec>()

        val dir = getShipDirectoryWithPrefix(prefix) ?: return autofitSpecs

        val ships = dir.getShips(hullSpec)
        ships.forEach { variant ->
            val missing = dir.getShipEntry(variant.hullVariantId)?.missingElements ?: return@forEach
            val index = dir.getShipIndexInMenu(variant.hullVariantId)

            autofitSpecs.add(
                AutofitSpec(
                    variant,
                    description = dir.getDescription(variant.hullVariantId),
                    source = dir,
                    missing = missing,
                    desiredIndexInMenu = index + indexOffset
                )
            )
        }


        return autofitSpecs
    }

    fun getShipDirectoryWithPrefix(prefix: String): ShipDirectory? {
        return shipDirectories.firstOrNull { it.prefix == prefix }
    }

    fun getVariantSourceShipDirectory(variant: ShipVariantAPI): ShipDirectory? {
        val sourceTag = variant.tags.firstOrNull { it.startsWith("#PREFIX_") }?.substringAfter("_")

        if (sourceTag != null)
            return getShipDirectoryWithPrefix(sourceTag)

        return null
    }

    fun getHighestIndexInEffectiveMenu(prefix: String, hullSpec: ShipHullSpecAPI): Int {
        var maxIndex = -1
        val dir = getShipDirectoryWithPrefix(prefix) ?: return maxIndex

        val ships = dir.getShips(hullSpec)
        for (ship in ships) {
            val index = dir.getShipIndexInMenu(ship.hullVariantId)
            maxIndex = max(maxIndex, index)
        }

        return maxIndex
    }

    //fun getAllVariantsForHullspec(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
    //    return (getCoreVariantsForEffectiveHullspec(hullSpec)) + shipDirectories.flatMap { it.getShips(hullSpec) }
    //}

    fun getLoadoutVariantsForHullspec(prefix: String, hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
        val dir = getShipDirectoryWithPrefix(prefix) ?: return emptyList<ShipVariantAPI>()

        return dir.getShips(hullSpec)
    }

    fun saveLoadoutVariant(
        prefix: String,
        variant: ShipVariantAPI,
        missingFromVariant: MissingElements = MissingElements(),
        settings: VariantSettings = VariantSettings(),
        desiredIndexInMenu: Int = -1,
        tagAsImport: Boolean = false,
    ): String {
        return getShipDirectoryWithPrefix(prefix)?.addShip(
            variant,
            missingFromVariant,
            settings,
            desiredIndexInMenu,
            tagAsImport = tagAsImport
        ) ?: ""
    }

    fun deleteLoadoutVariant(prefix: String, variantId: String) {
        getShipDirectoryWithPrefix(prefix)?.removeShip(variantId)
    }

    fun importShipLoadout(prefix: String, variant: ShipVariantAPI, missing: MissingElements): Boolean {
        //variantToSave.hullVariantId = makeVariantID(saveVariant)

        if (doesLoadoutExist(prefix, variant))
            return true

        saveLoadoutVariant(prefix, variant, missing, tagAsImport = true)

        return false
    }

    fun doesLoadoutExist(prefix: String, variant: ShipVariantAPI): Boolean {
        var loadoutExists = false
        val hullspecVariants = getLoadoutVariantsForHullspec(prefix, variant.hullSpec)
        for (hullspecVariant in hullspecVariants) {
            if (compareVariantContents(
                    variant,
                    hullspecVariant
                )
            ) {//If the variants are equal
                loadoutExists = true
                break
            }
        }
        return loadoutExists
    }
}