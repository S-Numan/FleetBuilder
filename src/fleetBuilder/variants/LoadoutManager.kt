package fleetBuilder.variants

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.config.ModSettings
import fleetBuilder.config.ModSettings.DIRECTORYCONFIGNAME
import fleetBuilder.config.ModSettings.FLEETDIR
import fleetBuilder.config.ModSettings.PACKDIR
import fleetBuilder.config.ModSettings.defaultPrefix
import fleetBuilder.config.ModSettings.importPrefix
import fleetBuilder.persistence.variant.VariantSerialization
import fleetBuilder.persistence.variant.VariantSerialization.getVariantFromJsonWithMissing
import fleetBuilder.ui.autofit.AutofitSpec
import fleetBuilder.variants.VariantLib.compareVariantContents
import fleetBuilder.variants.VariantLib.getCoreVariantsForEffectiveHullspec
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

object LoadoutManager {

    private val shipDirectories: MutableList<ShipDirectory> = mutableListOf()

    fun generatePrefixes(): List<String> {
        val letters = ('A'..'Z') + ('a'..'z')

        return letters.flatMap { c1 ->
            listOf(c1.toString()) + letters.map { c2 -> "$c1$c2" }
        }
    }

    fun loadAllDirectories() {

        //Make directories
        Global.getSettings().writeTextFileToCommon("$FLEETDIR/deleteme", ".")
        Global.getSettings().deleteTextFileFromCommon("$FLEETDIR/deleteme")

        // Ensure import exists
        if (!Global.getSettings().fileExistsInCommon("$PACKDIR$importPrefix/$DIRECTORYCONFIGNAME")) {
            val json = JSONObject()
            json.put("description", "Imported Loadout")
            Global.getSettings().writeJSONToCommon("$PACKDIR$importPrefix/$DIRECTORYCONFIGNAME", json, false)
        }
        // Ensure default exists
        if (!Global.getSettings().fileExistsInCommon("$PACKDIR$defaultPrefix/$DIRECTORYCONFIGNAME")) {
            val json = JSONObject()
            json.put("description", "Default Loadout")
            Global.getSettings().writeJSONToCommon("$PACKDIR$defaultPrefix/$DIRECTORYCONFIGNAME", json, false)
        }


        shipDirectories.clear()

        // Load all prefixed ship directories
        generatePrefixes().forEach { prefix ->
            loadShipDirectory(PACKDIR, prefix)?.let { shipDirectories.add(it) }
        }
    }

    fun loadShipDirectory(dirPath: String, prefix: String): ShipDirectory? {

        val configFilePath = "$dirPath$prefix/$DIRECTORYCONFIGNAME"
        return try {
            val directory: JSONObject
            if (Global.getSettings().fileExistsInCommon(configFilePath)) {
                if (shipDirectories.any { it.dir + it.prefix == "$dirPath$prefix" })
                    throw Error("Loadout pack name conflict.\nThe prefix '$prefix' is already taken. You must rename the folder '/saves/common/$dirPath$prefix' to something other than '$prefix', as '$prefix' is already in use")
                directory = Global.getSettings().readJSONFromCommon(configFilePath, false)
            } else
                return null


            upgradeLegacyShipPaths(directory, configFilePath)

            val ships = mutableMapOf<String, ShipVariantAPI>()
            val shipPaths = mutableMapOf<String, String>()
            val shipMissings = mutableMapOf<String, MissingElements>()
            val shipTimeSaved = mutableMapOf<String, Date>()
            val shipIndexInEffectiveMenu = mutableMapOf<String, Int>()

            val description = directory.optString("description", "$prefix Loadout")

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

                    // Check for duplicate ship path
                    if (!seenPaths.add(shipPath)) {
                        throw Error("Duplicate ship path detected: \"$shipPath\" in \"/saves/common/$configFilePath\"\nRemove the duplicates to continue.")
                    }

                    var variant: ShipVariantAPI
                    var missings: MissingElements

                    if (Global.getSettings().fileExistsInCommon("$dirPath$prefix/$shipPath")) {
                        val variantJson: JSONObject
                        try {
                            variantJson = Global.getSettings().readJSONFromCommon("$dirPath$prefix/$shipPath", false)
                        } catch (e: Exception) {
                            Global.getLogger(this.javaClass).error(
                                "Failed to read ship variant JSON at /saves/common/$dirPath$prefix/$shipPath\n",
                                e
                            )
                            continue
                        }

                        val result = getVariantFromJsonWithMissing(variantJson)
                        variant = result.first
                        missings = result.second
                        if (missings.hullIds.isNotEmpty()) {//Could not find hullSpec. Most likely it is a hullspec from a mod which was disabled.
                            continue
                        }
                    } else {//Failed to find ship at specific path.
                        Global.getLogger(this.javaClass)
                            .warn("shipPath in path directory /saves/common/$dirPath$prefix linked to ship variant at /saves/common/$dirPath$prefix/$shipPath\nHowever, no file was found at that location")
                        //Global.getLogger(this.javaClass).info("Failed to find ship with path $dirPath$prefix/$shipPath . Removing from directory")
                        //TODO, remove this shipPath from shipPaths
                        continue
                    }

                    if (ships.containsKey(variant.hullVariantId)) {
                        throw Error(
                            "Duplicate variant ID in ships directory $prefix\n" +
                                    "Ship path 1: $dirPath$prefix/$shipPath\n" +
                                    "Ship path 2: $dirPath$prefix/${shipPaths[variant.hullVariantId]}\n" +
                                    "The variantID must be changed on one or the other, or one must be removed."
                        )
                    }

                    shipPaths[variant.hullVariantId] = shipPath
                    ships[variant.hullVariantId] = variant
                    shipMissings[variant.hullVariantId] = missings
                    shipTimeSaved[variant.hullVariantId] = parsedDate
                    shipIndexInEffectiveMenu[variant.hullVariantId] = parsedEffectiveIndex
                }
            }

            val shipDirectory = ShipDirectory("$dirPath$prefix/", configFilePath, prefix, ships, shipPaths, shipMissings, shipTimeSaved, shipIndexInEffectiveMenu, description)

            // Assure indexes aren't missing
            shipDirectory.getAllVariants().toList().forEach { variant ->
                fun remakeShip() {
                    val missing = shipDirectory.getShipMissings(variant.hullVariantId) ?: return
                    shipDirectory.removeShip(variant.hullVariantId, editVariantFile = false)
                    shipDirectory.addShip(variant, missing, editVariantFile = false, setVariantID = variant.hullVariantId)
                }

                val thisIndex = shipDirectory.getShipIndexInMenu(variant.hullVariantId)
                if (thisIndex == -1) { // Missing?
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

        for ((i, variant) in variants.withIndex()) {
            if (variant.isGoalVariant && ModSettings.showCoreGoalVariants)
                coreVariantSpecs.add(
                    AutofitSpec(
                        variant,
                        source = null,
                        desiredIndexInMenu = i + indexOffset,
                        "Core Autofit Variant"
                    )
                )
            else if (!variant.isGoalVariant && ModSettings.showCoreNonGoalVariants)
                coreVariantSpecs.add(
                    AutofitSpec(
                        variant,
                        source = null,
                        desiredIndexInMenu = i + indexOffset,
                        "Core Variant"
                    )
                )
        }

        return coreVariantSpecs
    }

    fun getLoadoutAutofitSpecsForShip(
        hullSpec: ShipHullSpecAPI,
        indexOffset: Int = 0
    ): Map<ShipDirectory, List<AutofitSpec>> {
        val loadoutAutofitSpecs = mutableMapOf<ShipDirectory, List<AutofitSpec>>()
        shipDirectories.forEach {
            val autofitSpecs = mutableListOf<AutofitSpec>()

            val ships = it.getShips(hullSpec)
            ships.forEach { variant ->
                val missing = it.getShipMissings(variant.hullVariantId) ?: return@forEach
                val index = it.getShipIndexInMenu(variant.hullVariantId)

                autofitSpecs.add(
                    AutofitSpec(
                        variant,
                        description = it.getDescription(),
                        source = it,
                        missing = missing,
                        desiredIndexInMenu = index + indexOffset
                    )
                )
            }

            loadoutAutofitSpecs[it] = autofitSpecs
        }

        return loadoutAutofitSpecs
    }

    fun getShipDirectoryWithPrefix(prefix: String): ShipDirectory? {
        for (dir in shipDirectories) {
            if (dir.prefix == prefix) {
                return dir
            }
        }
        return null
    }

    fun getVariantSourceShipDirectory(variant: ShipVariantAPI): ShipDirectory? {
        for (dir in shipDirectories) {
            if (variant.hullVariantId.startsWith(dir.prefix)) {
                return dir
            }
        }
        return null
    }

    fun getHighestIndexInEffectiveMenu(hullSpec: ShipHullSpecAPI): Int {
        var maxIndex = 0
        for (dir in shipDirectories) {
            val ships = dir.getShips(hullSpec)
            for (ship in ships) {
                val index = dir.getShipIndexInMenu(ship.hullVariantId)
                maxIndex = max(maxIndex, index)
            }
        }
        return maxIndex
    }

    fun getAnyVariant(variantId: String): ShipVariantAPI? {
        val coreVariant = Global.getSettings().getVariant(variantId)
        return coreVariant?.clone() ?: getLoadoutVariant(variantId)
    }

    fun getLoadoutVariant(variantId: String): ShipVariantAPI? {
        return shipDirectories
            .firstNotNullOfOrNull { shipDir ->
                if (!variantId.startsWith("${shipDir.prefix}_")) return@firstNotNullOfOrNull null
                shipDir.getShip(variantId)
            }
    }

    fun getAnyVariantsForHullspec(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
        return (getCoreVariantsForEffectiveHullspec(hullSpec) + getLoadoutVariantsForHullspec(hullSpec))
    }

    fun getLoadoutVariantsForHullspec(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
        return shipDirectories
            .flatMap { it.getShips(hullSpec) }
    }

    fun saveLoadoutVariant(
        variant: ShipVariantAPI,
        prefix: String = ModSettings.defaultPrefix,
        missingFromVariant: MissingElements = MissingElements(),
        settings: VariantSerialization.VariantSettings = VariantSerialization.VariantSettings(),
        desiredIndexInMenu: Int = 0
    ): String {
        return getShipDirectoryWithPrefix(prefix)?.addShip(
            variant,
            missingFromVariant,
            settings,
            desiredIndexInMenu
        ) ?: ""
    }

    fun deleteLoadoutVariant(variantId: String) {
        for (shipDir in shipDirectories) {
            shipDir.removeShip(variantId)
        }
    }

    fun importShipLoadout(variant: ShipVariantAPI, missing: MissingElements): Boolean {
        //variantToSave.hullVariantId = makeVariantID(saveVariant)

        var loadoutExists = doesLoadoutExist(variant)

        if (!loadoutExists)
            saveLoadoutVariant(variant, importPrefix, missing)

        return loadoutExists
    }

    fun doesLoadoutExist(variant: ShipVariantAPI): Boolean {
        var loadoutExists = false
        val hullspecVariants = getLoadoutVariantsForHullspec(variant.hullSpec)
        for (hullspecVariant in hullspecVariants) {
            if (compareVariantContents(
                    variant,
                    hullspecVariant,
                    compareTags = true
                )
            ) {//If the variants are equal
                loadoutExists = true
                break
            }
        }
        return loadoutExists
    }
}