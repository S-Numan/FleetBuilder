package fleetBuilder.variants

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.config.ModSettings
import fleetBuilder.config.ModSettings.defaultPrefix
import fleetBuilder.config.ModSettings.importPrefix
import fleetBuilder.persistence.VariantSerialization.getVariantFromJsonWithMissing
import fleetBuilder.ui.autofit.AutofitSpec
import fleetBuilder.variants.VariantLib.compareVariantContents
import fleetBuilder.variants.VariantLib.getCoreVariantsForEffectiveHullspec
import org.json.JSONObject

object LoadoutManager {

    const val primaryDir = "FleetBuilder/"
    const val packDir = (primaryDir + "LoadoutPacks/")
    const val fleetDir = (primaryDir + "Fleets/")
    const val directoryConfigName = "directory"

    val shipDirectories: MutableList<ShipDirectory> = mutableListOf()

    fun generatePrefixes(): List<String> {
        val letters = ('A'..'Z') + ('a'..'z')

        return letters.flatMap { c1 ->
            listOf(c1.toString()) + letters.map { c2 -> "$c1$c2" }
        }
    }

    fun loadAllDirectories() {

        //Make directories
        Global.getSettings().writeTextFileToCommon("$fleetDir/deleteme", ".")
        Global.getSettings().deleteTextFileFromCommon("$fleetDir/deleteme")

        // Ensure import exists
        if (!Global.getSettings().fileExistsInCommon("$packDir$importPrefix/$directoryConfigName")) {
            val json = JSONObject()
            json.put("description", "Imported Loadout")
            Global.getSettings().writeJSONToCommon("$packDir$importPrefix/$directoryConfigName", json, false)
        }
        // Ensure default exists
        if (!Global.getSettings().fileExistsInCommon("$packDir$defaultPrefix/$directoryConfigName")) {
            val json = JSONObject()
            json.put("description", "Default Loadout")
            Global.getSettings().writeJSONToCommon("$packDir$defaultPrefix/$directoryConfigName", json, false)
        }


        shipDirectories.clear()

        // Load all prefixed ship directories
        generatePrefixes().forEach { prefix ->
            loadShipDirectory(packDir, prefix)?.let { shipDirectories.add(it) }
        }
    }

    fun loadShipDirectory(dirPath: String, prefix: String): ShipDirectory? {
        val configFilePath = "$dirPath$prefix/$directoryConfigName"
        return try {
            val directory: JSONObject
            if (Global.getSettings().fileExistsInCommon(configFilePath)) {
                if (shipDirectories.any { it.dir + it.prefix == "$dirPath$prefix" })
                    throw Error("Loadout pack name conflict.\nThe prefix '$prefix' is already taken. You must rename the folder '/saves/common/$dirPath$prefix' to something other than '$prefix', as '$prefix' is already in use")
                directory = Global.getSettings().readJSONFromCommon(configFilePath, false)
            } else
                return null

            val ships = mutableMapOf<String, ShipVariantAPI>()
            val shipPaths = mutableMapOf<String, String>()
            val shipMissings = mutableMapOf<String, MissingElements>()

            val description = directory.optString("description", "$prefix Loadout")

            directory.optJSONArray("shipPaths")?.let { jsonArray ->
                val seenPaths = mutableSetOf<String>()

                for (i in 0 until jsonArray.length()) {
                    val shipPath = jsonArray.getString(i)

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
                        if (missings.hullIds.size != 0) {//Could not find hullSpec. Most likely it is a hullspec from a mod which was disabled.
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
                }
            }

            ShipDirectory("$dirPath$prefix/", configFilePath, prefix, ships, shipPaths, shipMissings, description)
        } catch (e: Exception) {
            Global.getLogger(this.javaClass).error("Failed to read JSON from within /saves/common/$dirPath\n", e)
            null
        }
    }


    fun getAllAutofitSpecsForShip(hullSpec: ShipHullSpecAPI): List<AutofitSpec> {

        val variants = getCoreVariantsForEffectiveHullspec(hullSpec)

        val variantSpecs = mutableListOf<AutofitSpec>()

        for (variant in variants) {
            if (variant.isGoalVariant && ModSettings.showCoreGoalVariants)
                variantSpecs.add(
                    AutofitSpec(
                        variant.hullVariantId,
                        variant.displayName,
                        "Core Autofit Variant",
                        variant.hullSpec.spriteName
                    )
                )
            else if (!variant.isGoalVariant && ModSettings.showCoreNonGoalVariants)
                variantSpecs.add(
                    AutofitSpec(
                        variant.hullVariantId,
                        variant.displayName,
                        "Core Variant",
                        variant.hullSpec.spriteName
                    )
                )
        }

        val insertname = getLoadoutVariantsAndMissingsAndSourceForHullspec(hullSpec)

        val loadouts = insertname.first
        val missings = insertname.second
        val source = insertname.third

        for ((i, variant) in loadouts.withIndex()) {
            variantSpecs.add(
                AutofitSpec(
                    variant.hullVariantId,
                    variant.displayName,
                    source[i].getDescription(),
                    variant.hullSpec.spriteName,
                    missings[i]
                )
            )
        }

        return variantSpecs
    }

    fun getShipDirectoryWithPrefix(prefix: String): ShipDirectory? {
        for (dir in shipDirectories) {
            if (dir.prefix == prefix) {
                return dir
            }
        }
        return null
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

    fun getLoadoutVariantsAndMissingsAndSourceForHullspec(hullSpec: ShipHullSpecAPI): Triple<List<ShipVariantAPI>, List<MissingElements>, List<ShipDirectory>> {
        val ships = getLoadoutVariantsForHullspec(hullSpec)

        val shipSources: MutableList<ShipDirectory> = mutableListOf()
        val shipMissings: MutableList<MissingElements> = mutableListOf()
        for (ship in ships) {
            for (dir in shipDirectories) {
                if (ship.hullVariantId.removePrefix(dir.prefix).length == ship.hullVariantId.length)//Did this come from this database? (check with prefix)
                    continue
                if (!dir.containsShip(ship.hullVariantId)) {
                    Global.getLogger(this.javaClass)
                        .error("Ship of variant id ${ship.hullVariantId} has a prefix of ${dir.prefix} but was not in the ship directory of that prefix.")
                    shipMissings.add(MissingElements())
                } else {
                    shipMissings.add(dir.getShipMissings(ship.hullVariantId) ?: throw Exception("Should never happen"))
                }
                shipSources.add(dir)
                break
            }
        }
        return Triple(ships, shipMissings, shipSources)
    }

    fun saveLoadoutVariant(
        variant: ShipVariantAPI,
        prefix: String = "DF",
        missingFromVariant: MissingElements = MissingElements(),
        applySMods: Boolean = true,
        includeDMods: Boolean = true,
        includeTags: Boolean = true,
        includeTime: Boolean = true
    ): String {//TODO, implement "includeHiddenHullmods"
        return getShipDirectoryWithPrefix(prefix)?.addShip(
            variant,
            missingFromVariant,
            applySMods,
            includeDMods,
            includeTags,
            includeTime
        ) ?: return ""
    }

    fun deleteLoadoutVariant(variantId: String) {
        for (shipDir in shipDirectories) {
            shipDir.removeShip(variantId)
        }
    }

    fun importShipLoadout(variant: ShipVariantAPI, missing: MissingElements): Boolean {
        //variantToSave.hullVariantId = makeVariantID(saveVariant)

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

        if (!loadoutExists)
            saveLoadoutVariant(variant, importPrefix, missing)

        return loadoutExists
    }

}