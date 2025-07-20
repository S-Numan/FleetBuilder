package fleetBuilder.persistence

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.loading.WeaponGroupSpec
import com.fs.starfarer.api.loading.WeaponGroupType
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.ModSettings.getHullModsToNeverSave
import fleetBuilder.persistence.VariantSerialization.extractVariantDataFromJson
import fleetBuilder.persistence.VariantSerialization.filterParsedVariantData
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
import fleetBuilder.util.FBMisc
import fleetBuilder.util.completelyRemoveMod
import fleetBuilder.util.containsString
import fleetBuilder.util.optJSONArrayToStringList
import fleetBuilder.util.toBinary
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib
import org.json.JSONArray
import org.json.JSONObject

object VariantSerialization {

    /**
     * Holds settings for [saveVariantToJson] and [getVariantFromJson].
     * @param applySMods If false, SMods will be converted to normal hullmods.
     * @param includeDMods If false, DMods will be excluded from the variant's hullmods.
     * @param includeHiddenMods If false, hidden mods will be excluded from the variant's hullmods.
     * @param includeTags If false, the variant's tags will not be included.
     * @param excludeWeaponsWithID A set of weapon IDs to exclude from the variant's weapons.
     * @param excludeWingsWithID A set of wing IDs to exclude from the variant's wings.
     * @param excludeHullModsWithID A set of hullmod IDs to exclude from the variant's hullmods.
     */
    data class VariantSettings(
        var applySMods: Boolean = true,
        var includeDMods: Boolean = true,
        var includeHiddenMods: Boolean = true,
        var includeTags: Boolean = true,
        var excludeWeaponsWithID: MutableSet<String> = mutableSetOf(),
        var excludeWingsWithID: MutableSet<String> = mutableSetOf(),
        var excludeHullModsWithID: MutableSet<String> = mutableSetOf(),
    )

    data class ParsedVariantData(
        val variantId: String,
        val hullId: String,
        val displayName: String,
        val fluxCapacitors: Int,
        val fluxVents: Int,
        val tags: List<String>,
        val hullMods: List<String>,
        val permaMods: List<String>,
        val sMods: List<String>,
        val sModdedBuiltIns: List<String>,
        val wings: List<String>,
        val weaponGroups: List<ParsedWeaponGroup>,
        val moduleVariants: Map<String, ParsedVariantData>,
        val isGoalVariant: Boolean
    )

    data class ParsedWeaponGroup(
        val isAutofireOnByDefault: Boolean,
        val type: WeaponGroupType,
        val slotToWeaponId: Map<String, String>
    )

    fun extractVariantDataFromJson(json: JSONObject): ParsedVariantData {

        val variantId = json.optString("variantId", "")
        val hullId = json.optString("hullId", "")
        val displayName = json.optString("displayName", "")

        val fluxCapacitors = json.optInt("fluxCapacitors", 0)
        val fluxVents = json.optInt("fluxVents", 0)
        val isGoalVariant = json.optBoolean("goalVariant", false)

        val tags = json.optJSONArrayToStringList("tags")
        val hullMods = json.optJSONArrayToStringList("hullMods")
        val permaMods = json.optJSONArrayToStringList("permaMods")
        val sMods = json.optJSONArrayToStringList("sMods")
        val sModdedBuiltIns = json.optJSONArrayToStringList("sModdedbuiltins")
        val wings = json.optJSONArrayToStringList("wings")

        val weaponGroups = mutableListOf<ParsedWeaponGroup>()

        json.optJSONArray("weaponGroups")?.let { groups ->
            for (i in 0 until groups.length()) {
                val weaponGroup = groups.optJSONObject(i)
                if (weaponGroup == null) {
                    Global.getLogger(this.javaClass)
                        .error("Failed to parse weaponGroup at index $i: not a valid JSONObject. hullSpec=$hullId, variant=$variantId")
                    continue
                }

                val weapons = weaponGroup.optJSONObject("weapons")
                if (weapons == null) {
                    Global.getLogger(this.javaClass)
                        .error("Missing 'weapons' object in weaponGroup at index $i. hullSpec=$hullId, variant=$variantId")
                    continue
                }

                val slotToWeaponId = mutableMapOf<String, String>()
                val slots = weapons.keys()
                while (slots.hasNext()) {
                    val rawSlotId = slots.next()
                    val slotId = rawSlotId?.toString()?.trim()
                    val weaponId = weapons.optString(slotId, "").trim()

                    if (!slotId.isNullOrBlank() && weaponId.isNotBlank()) {
                        slotToWeaponId[slotId] = weaponId
                    } else {
                        Global.getLogger(this.javaClass)
                            .warn("Invalid slot or weapon ID in weaponGroup at index $i: slot='$slotId', weapon='$weaponId'. hullSpec=$hullId, variant=$variantId")
                    }
                }

                val isAutofire = weaponGroup.optBoolean("autofire", false)

                val type = try {
                    WeaponGroupType.valueOf(weaponGroup.optString("mode", WeaponGroupType.LINKED.name))
                } catch (_: Exception) {
                    Global.getLogger(this.javaClass)
                        .warn("Invalid weapon group mode '${weaponGroup.optString("mode")}' at index $i. Defaulting to LINKED. hullSpec=$hullId, variant=$variantId")
                    WeaponGroupType.LINKED
                }

                weaponGroups.add(ParsedWeaponGroup(isAutofire, type, slotToWeaponId))
            }
        }

        val moduleVariants = mutableMapOf<String, ParsedVariantData>()

        //Vanilla method for storing ship modules, Module variants are stored in a separate file
        json.optJSONArray("modules")?.let { arr ->
            repeat(arr.length()) { i ->
                arr.optJSONObject(i)?.names()?.optString(0)?.let { key ->
                    arr.optJSONObject(i)?.optString(key)?.let { value ->
                        val module: ShipVariantAPI
                        try {
                            module = Global.getSettings().getVariant(variantId)
                        } catch (e: Exception) {
                            Global.getLogger(this::class.java).error("Failed to get variant '$variantId' for slot '$key'", e)
                            return@let
                        }

                        val newModule = extractVariantDataFromJson(saveVariantToJson(module))

                        moduleVariants[key] = newModule
                    }
                }
            }
        }

        //FleetBuilder method for storing module variants. Module variants are stored in the same JSON
        json.optJSONObject("moduleVariants")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key is String) {
                    obj.optJSONObject(key)?.let {
                        val newModule = extractVariantDataFromJson(it)
                        moduleVariants[key] = newModule
                    }
                }
            }
        }

        return ParsedVariantData(
            variantId, hullId, displayName, fluxCapacitors, fluxVents,
            tags, hullMods, permaMods, sMods, sModdedBuiltIns, wings,
            weaponGroups, moduleVariants, isGoalVariant
        )
    }

    fun filterParsedVariantData(
        data: ParsedVariantData,
        settings: VariantSettings
    ): ParsedVariantData {

        fun shouldKeepMod(modId: String): Boolean {
            if (getHullModsToNeverSave().contains(modId)) return false
            if (modId in settings.excludeHullModsWithID) return false
            if (!settings.includeDMods && VariantLib.getAllDMods().contains(modId)) return false
            if (!settings.includeHiddenMods && VariantLib.getAllHiddenEverywhereMods().contains(modId)) return false
            return true
        }

        fun shouldKeepWeapon(weaponId: String): Boolean {
            return weaponId !in settings.excludeWeaponsWithID
        }

        fun shouldKeepWing(wingId: String): Boolean {
            return wingId !in settings.excludeWingsWithID
        }

        // Filter hull mods
        val filteredHullMods = data.hullMods.filter(::shouldKeepMod).toMutableList()
        val filteredPermaMods = data.permaMods.filter(::shouldKeepMod)
        val filteredSMods = if (settings.applySMods) {
            data.sMods.filter(::shouldKeepMod)
        } else {
            filteredHullMods.addAll(data.sMods.filter { it !in filteredHullMods })
            emptyList()
        }

        val filteredSModdedBuiltIns = if (settings.applySMods) {
            data.sModdedBuiltIns.filter(::shouldKeepMod)
        } else emptyList()

        val filteredWings = data.wings.filter(::shouldKeepWing)

        val filteredWeaponGroups = data.weaponGroups.map { group ->
            val filteredSlots = group.slotToWeaponId.filterValues(::shouldKeepWeapon)
            group.copy(slotToWeaponId = filteredSlots)
        }

        val filteredTags = if (settings.includeTags) {
            data.tags
        } else emptyList()

        return data.copy(
            hullMods = filteredHullMods,
            permaMods = filteredPermaMods,
            sMods = filteredSMods,
            sModdedBuiltIns = filteredSModdedBuiltIns,
            wings = filteredWings,
            weaponGroups = filteredWeaponGroups,
            tags = filteredTags
        )
    }

    fun validateAndCleanVariantData(
        data: ParsedVariantData
    ): Pair<ParsedVariantData, MissingElements> {
        val missing = MissingElements()

        // --- Hull ID ---
        val validHullId = data.hullId.takeIf { it.isNotBlank() && Global.getSettings().allShipHullSpecs.any { spec -> spec.hullId == it } }
        if (validHullId == null) missing.hullIds.add(data.hullId)

        // --- Variant ID ---
        val fixedVariantId = data.variantId.ifBlank {
            "MissingVariantID_" + Misc.genUID()
        }

        // --- Display Name ---
        val fixedDisplayName = data.displayName.ifBlank {
            "No Name"
        }

        // --- HullMods ---
        val allHullMods = Global.getSettings().allHullModSpecs.map { it.id }.toSet()

        val cleanHullMods = data.hullMods.filter { modId ->
            if (modId !in allHullMods) {
                missing.hullModIds.add(modId)
                false
            } else true
        }

        val cleanPermaMods = data.permaMods.filter { modId ->
            if (modId !in allHullMods) {
                missing.hullModIds.add(modId)
                false
            } else true
        }

        val cleanSMods = data.sMods.filter { modId ->
            if (modId !in allHullMods) {
                missing.hullModIds.add(modId)
                false
            } else true
        }

        val cleanSModdedBuiltIns = data.sModdedBuiltIns.filter { modId ->
            if (modId !in allHullMods) {
                missing.hullModIds.add(modId)
                false
            } else true
        }

        // --- Wings ---
        val allWingIds = Global.getSettings().allFighterWingSpecs.map { it.id }.toSet()
        val cleanWings = data.wings.mapIndexed { _, wingId ->
            if (wingId !in allWingIds && wingId.isNotBlank()) {
                missing.wingIds.add(wingId)
                ""
            } else wingId
        }

        // --- Weapon Groups ---
        val allWeapons = Global.getSettings().actuallyAllWeaponSpecs.map { it.weaponId }.toSet()
        val cleanWeaponGroups = data.weaponGroups.map { wg ->
            val cleanedSlots = wg.slotToWeaponId.filter { (_, weaponId) ->
                val valid = weaponId in allWeapons
                if (!valid) missing.weaponIds.add(weaponId)
                valid
            }
            wg.copy(slotToWeaponId = cleanedSlots)
        }

        // --- Module Variants ---
        val cleanedModuleVariants = mutableMapOf<String, ParsedVariantData>()
        data.moduleVariants.forEach { (slotId, moduleData) ->
            val (cleanedModule, subMissing) = validateAndCleanVariantData(moduleData)
            missing.add(subMissing)
            cleanedModuleVariants[slotId] = cleanedModule
        }

        val cleanedData = data.copy(
            hullId = validHullId ?: data.hullId,
            variantId = fixedVariantId,
            displayName = fixedDisplayName,
            hullMods = cleanHullMods,
            permaMods = cleanPermaMods,
            sMods = cleanSMods,
            sModdedBuiltIns = cleanSModdedBuiltIns,
            wings = cleanWings,
            weaponGroups = cleanWeaponGroups,
            moduleVariants = cleanedModuleVariants
        )

        return cleanedData to missing
    }

    fun buildVariant(
        data: ParsedVariantData
    ): ShipVariantAPI {
        val hullSpec = Global.getSettings().getHullSpec(data.hullId)
        val loadout = Global.getSettings().createEmptyVariant(hullSpec.hullId, hullSpec)

        loadout.hullVariantId = data.variantId
        loadout.setVariantDisplayName(data.displayName)
        loadout.isGoalVariant = data.isGoalVariant
        loadout.numFluxCapacitors = data.fluxCapacitors
        loadout.numFluxVents = data.fluxVents

        data.tags.forEach { loadout.addTag(it) }

        data.hullMods.forEach { modId ->
            loadout.addMod(modId)
        }

        data.permaMods.forEach { modId ->
            loadout.addPermaMod(modId, false)
        }

        data.sMods.forEach { modId ->
            if (hullSpec.builtInMods.contains(modId))
                loadout.sModdedBuiltIns.add(modId)
            else
                loadout.addPermaMod(modId, true)
        }

        data.sModdedBuiltIns.forEach { modId ->
            loadout.sModdedBuiltIns.add(modId)
        }


        data.wings.forEachIndexed { i, wingId ->
            if (wingId.isNotEmpty()) {
                loadout.setWingId(i, wingId)
            }
        }

        data.weaponGroups.forEach { wgData ->
            val wg = WeaponGroupSpec()

            wgData.slotToWeaponId.forEach { (slotId, weaponId) ->
                if (hullSpec.isBuiltIn(slotId)) {
                    wg.addSlot(slotId)
                } else {
                    loadout.addWeapon(slotId, weaponId)
                    wg.addSlot(slotId)
                }
            }

            wg.isAutofireOnByDefault = wgData.isAutofireOnByDefault
            wg.type = wgData.type
            loadout.addWeaponGroup(wg)
        }


        data.moduleVariants.forEach { (slotId, moduleData) ->
            val variant = buildVariant(moduleData)
            loadout.setModuleVariant(slotId, variant)
        }

        return loadout
    }

    fun buildVariantFromParsed(
        data: ParsedVariantData,
        settings: VariantSettings
    ): Pair<ShipVariantAPI, MissingElements> {
        val filteredData = filterParsedVariantData(data, settings)
        val (cleanedData, missing) = validateAndCleanVariantData(filteredData)

        val variant = if (missing.hullIds.isNotEmpty()) {
            val errorVariant = VariantLib.createErrorVariant("NOHUL:${missing.hullIds.first()}")
            if (data.variantId.isNotBlank())
                errorVariant.hullVariantId = data.variantId
            errorVariant
        } else {
            buildVariant(cleanedData)
        }

        return variant to missing
    }

    @JvmOverloads
    fun getVariantFromJsonWithMissing(
        json: JSONObject,
        settings: VariantSettings = VariantSettings()
    ): Pair<ShipVariantAPI, MissingElements> {
        val missing = MissingElements()
        FBMisc.getMissingFromModInfo(json, missing)

        val parsed = extractVariantDataFromJson(json)

        val (variant, newMissing) = buildVariantFromParsed(parsed, settings)
        missing.add(newMissing)

        return variant to missing
    }

    @JvmOverloads
    fun getVariantFromJson(json: JSONObject, settings: VariantSettings = VariantSettings()): ShipVariantAPI {
        return getVariantFromJsonWithMissing(json, settings).first
    }


    @JvmOverloads
    fun saveVariantToJson(
        variant: ShipVariantAPI,
        settings: VariantSettings = VariantSettings(),
        includeModInfo: Boolean = true
    ): JSONObject {
        val jsonVariant = saveModuleVariantToJson(variant, settings)

        val jsonModules = JSONObject()
        for (moduleSlot in variant.moduleSlots) {
            val module = variant.getModuleVariant(moduleSlot)
            val jsonModuleVariant = saveModuleVariantToJson(module, settings)

            jsonModules.put(moduleSlot, jsonModuleVariant)
        }
        if (jsonModules.length() != 0)
            jsonVariant.put("moduleVariants", jsonModules)

        if (includeModInfo)
            addVariantSourceModsToJson(variant, jsonVariant, settings)


        return jsonVariant
    }

    private fun saveModuleVariantToJson(
        insertVariant: ShipVariantAPI,
        settings: VariantSettings
    ): JSONObject {
        val variant = insertVariant.clone()

        if (variant.weaponGroups.isEmpty())
            variant.autoGenerateWeaponGroups()//Sometimes weapon groups on other fleets are empty. Weapons are stored in the json by weapon groups, so make sure to generate some weapon groups before converting to json.


        val json = JSONObject()
        //json.put("goalVariant", true)

        json.put("displayName", variant.displayName)
        json.put("variantId", variant.hullVariantId)
        json.put("hullId", variant.hullSpec.hullId)
        json.put("fluxCapacitors", variant.numFluxCapacitors)
        json.put("fluxVents", variant.numFluxVents)

        val wingsJson = JSONArray()
        for (wing in variant.nonBuiltInWings) {
            if (wing in settings.excludeWingsWithID)
                continue

            wingsJson.put(wing)
        }
        if (wingsJson.length() != 0)
            json.put("wings", wingsJson)

        val weaponGroupsJson = JSONArray()
        for (wg in variant.weaponGroups) {
            val groupJson = JSONObject()

            // Add autofire and mode
            groupJson.put("autofire", wg.isAutofireOnByDefault)
            groupJson.put("mode", wg.type.name)

            // Add weapons map
            val weaponsJson = JSONObject()
            for (slotId in wg.slots) {
                val weaponId = variant.getWeaponId(slotId)
                if (weaponId != null && weaponId !in settings.excludeWeaponsWithID) {
                    weaponsJson.put(slotId, weaponId)
                }
            }
            groupJson.put("weapons", weaponsJson)

            weaponGroupsJson.put(groupJson)
        }
        json.put("weaponGroups", weaponGroupsJson)

        val allDMods = VariantLib.getAllDMods()
        val allHiddenEverywhereMods = VariantLib.getAllHiddenEverywhereMods()

        val allModIds = buildSet {
            addAll(variant.hullMods)
            addAll(variant.sMods)
            addAll(variant.suppressedMods)
            addAll(variant.permaMods)
        }

        allModIds.forEach { modId ->
            when {
                getHullModsToNeverSave().contains(modId) || settings.excludeHullModsWithID.contains(modId) -> {
                    variant.completelyRemoveMod(modId)
                }

                !settings.includeDMods && allDMods.contains(modId) -> {
                    variant.completelyRemoveMod(modId)
                }

                !settings.includeHiddenMods && allHiddenEverywhereMods.contains(modId) -> {
                    variant.completelyRemoveMod(modId)
                }
            }

        }

        val hullMods = JSONArray()
        val sMods = JSONArray()
        val sModdedbuiltins = JSONArray()
        val permaMods = JSONArray()

        if (settings.applySMods) {
            variant.sModdedBuiltIns.forEach { mod ->
                sModdedbuiltins.put(mod)
            }

            variant.sMods.forEach { mod ->
                if (!variant.sModdedBuiltIns.contains(mod))
                    sMods.put(mod)
            }

        } else {
            variant.sMods.forEach { mod ->
                hullMods.put(mod)
            }
        }

        variant.permaMods.forEach { mod ->
            if (!sMods.containsString(mod) && !hullMods.containsString(mod) && !variant.hullSpec.builtInMods.contains(mod)) {
                permaMods.put(mod)
            }
        }

        variant.hullMods.forEach { mod ->
            if (!sMods.containsString(mod) && !permaMods.containsString(mod) && !hullMods.containsString(mod) && !variant.hullSpec.builtInMods.contains(mod))
                hullMods.put(mod)
        }

        json.put("hullMods", hullMods)
        json.put("permaMods", permaMods)
        json.put("sMods", sMods)
        if (sModdedbuiltins.length() > 0)
            json.put("sModdedbuiltins", sModdedbuiltins)


        if (settings.includeTags) {
            val tags = JSONArray()
            variant.tags.forEach { tag ->
                tags.put(tag)
            }
            if (tags.length() > 0)
                json.put("tags", tags)
        }

        return json
    }

    fun addVariantSourceModsToJson(
        variant: ShipVariantAPI,
        json: JSONObject,
        settings: VariantSettings
    ) {
        val addedModIds = mutableSetOf<Triple<String, String, String>>()
        getSourceModsFromVariant(addedModIds, variant, settings)

        if (addedModIds.isNotEmpty()) {
            val existingMods = mutableSetOf<Triple<String, String, String>>()
            val modInfoArray = if (json.has("mod_info")) json.getJSONArray("mod_info") else JSONArray()

            // Collect existing mods to avoid duplicates
            for (i in 0 until modInfoArray.length()) {
                val mod = modInfoArray.getJSONObject(i)
                val modId = mod.optString("mod_id")
                val modName = mod.optString("mod_name")
                val modVersion = mod.optString("mod_version")
                existingMods.add(Triple(modId, modName, modVersion))
            }

            // Add only new entries
            for (mod in addedModIds) {
                if (mod !in existingMods) {
                    val newMod = JSONObject().apply {
                        put("mod_id", mod.first)
                        put("mod_name", mod.second)
                        put("mod_version", mod.third)
                    }
                    modInfoArray.put(newMod)
                }
            }

            // Write back merged array
            json.put("mod_info", modInfoArray)
        }
    }

    fun getSourceModsFromVariant(
        addedModIds: MutableSet<Triple<String, String, String>>,
        variant: ShipVariantAPI,
        settings: VariantSettings
    ) {
        for (moduleSlot in variant.moduleSlots) {
            val module = variant.getModuleVariant(moduleSlot)
            getSourceModsFromVariant(addedModIds, module, settings)
        }

        fun addSourceMod(modId: String, modName: String, modVersion: String) {
            if (Triple(modId, modName, modVersion) !in addedModIds) {
                addedModIds.add(Triple(modId, modName, modVersion))
            }
        }

        //HullSpec
        variant.hullSpec.sourceMod?.let { sm ->
            addSourceMod(sm.id, sm.name, sm.version)
        }

        // HullMods
        for (mod in variant.hullMods) {
            if ((!settings.includeDMods && VariantLib.getAllDMods().contains(mod))
                || (!settings.includeHiddenMods && VariantLib.getAllHiddenEverywhereMods().contains(mod))
                || getHullModsToNeverSave().contains(mod)
            )
                continue

            Global.getSettings().getHullModSpec(mod).sourceMod?.let { sm ->

                addSourceMod(sm.id, sm.name, sm.version)
            }
        }

        // Weapons
        for (slot in variant.fittedWeaponSlots) {
            variant.getWeaponSpec(slot).sourceMod?.let { sm ->
                addSourceMod(sm.id, sm.name, sm.version)
            }
        }

        // Fighter Wings
        for (wing in variant.fittedWings) {
            Global.getSettings().getFighterWingSpec(wing).sourceMod?.let { sm ->
                addSourceMod(sm.id, sm.name, sm.version)
            }
        }
    }


    const val sep = ","
    const val fieldSep = "%"//Only two ascii characters that cannot be in a variant display name
    const val metaSep = "$"//Only two ascii characters that cannot be in a variant display name
    const val weaponGroupSep = ">"

    @JvmOverloads
    fun saveVariantToCompString(
        variant: ShipVariantAPI,
        settings: VariantSettings = VariantSettings(),
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true
    ): String {

        val structureVersion = 0


        val ver = "$metaSep$structureVersion$metaSep"


        var compressedVariant = ""
        compressedVariant += saveModuleVariantToCompString(variant, settings)

        for (moduleSlot in variant.moduleSlots) {
            val module = variant.getModuleVariant(moduleSlot)

            val compressedModuleVariant = saveModuleVariantToCompString(module, settings)

            compressedVariant += ":$compressedModuleVariant"
        }

        var requiredMods = ""//For the user to see
        var addedModDetails = ""//For the computer to see

        if (includeModInfo) {
            val addedModIds = mutableSetOf<Triple<String, String, String>>()
            getSourceModsFromVariant(addedModIds, variant, settings)

            if (addedModIds.isNotEmpty()) {

                requiredMods = "Req Mods: "

                for (mod in addedModIds) {
                    addedModDetails += "${mod.first}$sep${mod.second}$sep${mod.third}$fieldSep"
                    requiredMods += "${mod.second} $sep "
                }
                requiredMods = requiredMods.dropLast(3)
                addedModDetails = addedModDetails.dropLast(1)
            }
        }

        compressedVariant = "$addedModDetails$fieldSep$compressedVariant"

        //TODO, compress compressedVariant string here.

        compressedVariant = "$ver$compressedVariant"//Indicate structure version for compatibility with future compressed format changes

        if (includePrepend)
            compressedVariant = "${variant.displayName} ${variant.hullSpec.hullName} : $requiredMods " + compressedVariant//Prepend for the user to see. Should be ignored by the computer

        return compressedVariant
    }

    private fun saveModuleVariantToCompString(
        insertVariant: ShipVariantAPI,
        settings: VariantSettings = VariantSettings(),
    ): String {
        val variant = insertVariant.clone()

        if (variant.weaponGroups.isEmpty())
            variant.autoGenerateWeaponGroups()//Sometimes weapon groups on other fleets are empty. Weapons are stored in the json by weapon groups, so make sure to generate some weapon groups before converting to json.


        val parts = mutableListOf<String>()

        // Weapon groups (mode;autofire;slot+id,slot+id - ...)
        val weaponGroupStrings = variant.weaponGroups.map { group ->
            val mode = group.type.ordinal // 0 = LINKED, 1 = ALTERNATING
            val autofire = group.isAutofireOnByDefault.toString().toBinary
            val weapons = group.slots.mapIndexedNotNull { _, slotId ->
                val weaponId = variant.getWeaponId(slotId) ?: return@mapIndexedNotNull null
                "$slotId$sep$weaponId"
            }
            "$mode$sep$autofire$sep${weapons.joinToString(sep)}"
        }
        val weaponGroupString = weaponGroupStrings.joinToString(weaponGroupSep)


        // Hullmods logic
        val sModsOriginal = (variant.sMods + variant.sModdedBuiltIns).toSet()
        val permaMods = variant.permaMods.toMutableSet()
        val suppressedMods = variant.suppressedMods.toSet()
        val allHullMods = variant.hullMods.toMutableSet()

        // Remove sMods from permaMods
        permaMods.removeAll(sModsOriginal)

        // Remove all known mod sources from hullMods
        allHullMods.removeAll(sModsOriginal)
        allHullMods.removeAll(permaMods)
        allHullMods.removeAll(suppressedMods)

        // Remove D-mods if requested
        if (!settings.includeDMods) {
            allHullMods.removeAll(VariantLib.getAllDMods())
        }

        // sMods logic
        val sMods = if (settings.applySMods) {
            sModsOriginal
        } else {
            // Move sMods into hullMods if not applying
            allHullMods += sModsOriginal
            emptySet()
        }


        // Join everything
        parts += variant.hullSpec?.hullId ?: "null"
        parts += variant.hullVariantId
        parts += variant.displayName
        parts += variant.numFluxCapacitors.toString()
        parts += variant.numFluxVents.toString()
        parts += weaponGroupString
        parts += variant.fittedWings.joinToString(sep)
        parts += allHullMods.joinToString(sep)
        parts += sMods.joinToString(sep)
        parts += permaMods.joinToString(sep)
        parts += suppressedMods.joinToString(sep)
        parts += if (settings.includeTags) variant.tags.joinToString(sep) else ""



        return parts.joinToString(fieldSep)
    }

    fun getVariantFromCompressedWithMissing(data: String): Pair<ShipVariantAPI, MissingElements> {

        val missingElements = MissingElements()

        /*json.optJSONArray("mod_info")?.let {
            repeat(it.length()) { i ->
                val modSpecJson = it.optJSONObject(i)
                if(modSpecJson != null) {
                    val modSpecId = modSpecJson.optString("mod_id")
                    val modSpecName = modSpecJson.optString("mod_name")
                    val modSpecVersion = modSpecJson.optString("mod_version")

                    var hasMod = false
                    for (modSpecAPI in Global.getSettings().modManager.enabledModsCopy) {
                        if (modSpecAPI.id == modSpecId) {
                            hasMod = true
                            break
                        }
                    }
                    if (!hasMod) {
                        missingElements.gameMods.add(Triple(modSpecId, modSpecName, modSpecVersion))
                    }
                }
            }
        }*/

        val parts = data.split(fieldSep)
        if (parts.size < 11) throw IllegalArgumentException("Invalid serialized variant string")

        val hullId = parts[0]
        val variantId = parts[1]
        val displayName = parts[2]
        val fluxCaps = parts[3].toIntOrNull() ?: 0
        val fluxVents = parts[4].toIntOrNull() ?: 0
        val weaponGroupData = parts[5]
        val fighterWings = parts[6].takeIf { it.isNotEmpty() }?.split(sep) ?: emptyList()
        val hullMods = parts[7].takeIf { it.isNotEmpty() }?.split(sep)?.toMutableSet() ?: mutableSetOf()
        val sMods = parts[8].takeIf { it.isNotEmpty() }?.split(sep)?.toMutableSet() ?: mutableSetOf()
        val permaMods = parts[9].takeIf { it.isNotEmpty() }?.split(sep)?.toMutableSet() ?: mutableSetOf()
        val suppressedMods = parts[10].takeIf { it.isNotEmpty() }?.split(sep)?.toMutableSet() ?: mutableSetOf()
        val tags = parts[11].takeIf { it.isNotEmpty() }?.split(sep)?.toMutableSet() ?: mutableSetOf()

        val variant = Global.getSettings().createEmptyVariant(variantId, Global.getSettings().getHullSpec(""))

        /*variant.setVariantDisplayName(displayName)
        variant.numFluxCapacitors = fluxCaps
        variant.numFluxVents = fluxVents
        fighterWings.forEachIndexed { i, id -> variant.setWingId(i, id) }

        hullMods.forEach { variant.addMod(it) }
        sMods.forEach { variant.addPermaMod(it, true) }
        permaMods.forEach { variant.addPermaMod(it) }
        suppressedMods.forEach { variant.addSuppressedMod(it) }
        tags.forEach { variant.addTag(it) }

        // Weapon groups
        if (weaponGroupData.isNotEmpty()) {
            val groupStrings = weaponGroupData.split(weaponGroupSep)
            for (groupStr in groupStrings) {
                val segments = groupStr.split(sep)
                if (segments.size < 3) continue

                val modeInt = segments[0].toIntOrNull() ?: 0
                val autofireBinary = segments[1]
                val slotAssignments = segments.drop(2)

                val slotIds = mutableListOf<String>()
                val weaponIds = mutableListOf<String>()
                val autofireFlags = mutableListOf<Boolean>()

                slotAssignments.forEachIndexed { i, entry ->
                    val parts = entry.split("+", "@")
                    if (parts.size >= 2) {
                        val slotId = parts[0]
                        val weaponId = parts[1]
                        val auto = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: autofireBinary.getOrNull(i) == '1'

                        //variant.setWeaponId(slotId, weaponId)
                        slotIds += slotId
                        weaponIds += weaponId
                        autofireFlags += auto
                    }
                }

                val group = WeaponGroupSpec()
                group.slots.addAll(slotIds)
                group.mode = WeaponGroupSpec.WGMode.values().getOrElse(modeInt) { WeaponGroupSpec.WGMode.LINKED }
                group.setAutofireOnByDefault(autofireFlags)
                variant.addWeaponGroup(group)
            }
        }*/


        /*
        val hullId = json.optString("hullId")
        if(hullId.isEmpty()) {
            missingElements.hullIds.add("")
            return Pair(MISC.createErrorVariant("MISSINGHULL"), missingElements)
        }
        if (!Global.getSettings().allShipHullSpecs.any { it.hullId == hullId }) {
            missingElements.hullIds.add(hullId)
            return Pair(MISC.createErrorVariant("MISSINGHULL_$hullId"), missingElements)
        }
        val hullSpec = Global.getSettings().getHullSpec(hullId)

        val loadout = Global.getSettings().createEmptyVariant(hullSpec.hullId, hullSpec)

        var variantId = json.optString("variantId")
        if (variantId.isNullOrEmpty()) {
            variantId = "MissingVariantID_" + Misc.genUID()
            Global.getLogger(this.javaClass).warn("When loading JSONObject variant with hullId $hullId, failed to find variantID. Setting a random UID in its place")
        }
        loadout.hullVariantId = variantId

        val displayName = json.optString("displayName")
        if (displayName.isNullOrEmpty())
            Global.getLogger(this.javaClass)
                .warn("When loading JSONObject variant with hullId $hullId and variantId $variantId, failed to find displayName")
        loadout.setVariantDisplayName(displayName ?: "Missing Name")

        loadout.isGoalVariant = json.optBoolean("goalVariant")


        val fluxCaps = if (json.has("fluxCapacitors")) {
            json.optInt("fluxCapacitors", 0)
        } else {
            Global.getLogger(this.javaClass).warn("When loading JSONObject variant with hullId $hullId and variantId $variantId, failed to find fluxCapacitors")
            0
        }
        loadout.numFluxCapacitors = fluxCaps

        val fluxVents = if (json.has("fluxVents")) {
            json.optInt("fluxVents", 0)
        } else {
            Global.getLogger(this.javaClass).warn("When loading JSONObject variant with hullId $hullId and variantId $variantId, failed to find fluxVents")
            0
        }
        loadout.numFluxVents = fluxVents

        /*var jsonArray = json.optJSONArray("tags")
                    if (jsonArray != null) {
                        for (i in 0..<jsonArray.length()) {
                            loadout.addTag(jsonArray.getString(i))
                        }
                    }*/
        //Same function
        json.optJSONArray("tags")?.let {
            repeat(it.length()) { i -> loadout.addTag(it.getString(i)) }
        }

        json.optJSONArray("hullMods")?.let { hullMods ->
            repeat(hullMods.length()) { i ->
                val modId = hullMods.optString(i)
                if (Global.getSettings().allHullModSpecs.any { it.id == modId }) {
                    loadout.addMod(modId)
                } else if (modId !in missingElements.hullModIds) {
                    missingElements.hullModIds.add(modId)
                }
            }
        }

        json.optJSONArray("permaMods")?.let { permaMods ->
            repeat(permaMods.length()) { i ->
                val modId = permaMods.optString(i)
                if (Global.getSettings().allHullModSpecs.any { it.id == modId }) {
                    loadout.addPermaMod(modId, false)
                } else if (modId !in missingElements.hullModIds) {
                    missingElements.hullModIds.add(modId)
                }
            }
        }

        json.optJSONArray("sMods")?.let { sMods ->
            repeat(sMods.length()) { i ->
                val modId = sMods.optString(i)
                if (Global.getSettings().allHullModSpecs.any { it.id == modId }) {
                    loadout.addPermaMod(modId, true)
                    if(loadout.hullSpec.builtInMods.contains(modId))
                        loadout.sModdedBuiltIns.add(modId)
                } else if (modId !in missingElements.hullModIds) {
                    missingElements.hullModIds.add(modId)
                }
            }
        }

        json.optJSONArray("sModdedbuiltins")?.let { sModBuiltIns ->
            repeat(sModBuiltIns.length()) { i ->
                val modId = sModBuiltIns.optString(i)
                if (Global.getSettings().allHullModSpecs.any { it.id == modId }) {
                    loadout.addPermaMod(modId, true)
                    loadout.sModdedBuiltIns.add(modId)
                } else if (modId !in missingElements.hullModIds) {
                    missingElements.hullModIds.add(modId)
                }
            }
        }


        json.optJSONArray("wings")?.let { wings ->
            repeat(wings.length()) { i ->
                val wingId = wings.optString(i)
                if (Global.getSettings().allFighterWingSpecs.any { it.id == wingId }) {
                    loadout.setWingId(i, wingId)
                } else {
                    missingElements.wingIds.add(wingId)
                }
            }
        }


        json.optJSONArray("weaponGroups")?.let { groups ->
            for (i in 0 until groups.length()) {
                val weaponGroup = groups.optJSONObject(i)
                if (weaponGroup == null) {
                    Global.getLogger(this.javaClass)
                        .error("When loading JSONObject variant with hullSpec$hullId and variant$variantId, failed to load a weaponGroup JSONObject of element '$i' in weaponGroups")
                    continue
                }

                val wgs = WeaponGroupSpec()

                val weapons = weaponGroup.optJSONObject("weapons")
                if (weapons == null) {
                    Global.getLogger(this.javaClass)
                        .error("When loading JSONObject variant with hullSpec$hullId and variant$variantId, failed to find weapons in a weaponGroup")
                    continue
                }
                val slots = weapons.keys()
                while (slots.hasNext()) {
                    val slotId = slots.next().toString()
                    val weaponId = weapons.optString(slotId)
                    if(loadout.hullSpec.isBuiltIn(slotId)) { // If slot is built in, no need to add the weapon the variant, it's already there.
                        wgs.addSlot(slotId)
                    }
                    else if (Global.getSettings().actuallyAllWeaponSpecs.any { it.weaponId == weaponId }) {
                        loadout.addWeapon(slotId, weaponId)
                        wgs.addSlot(slotId)
                    } else {
                        missingElements.weaponIds.add(weaponId)
                    }
                }

                wgs.isAutofireOnByDefault = weaponGroup.optBoolean("autofire", false)

                wgs.type = WeaponGroupType.valueOf(weaponGroup.optString("mode", WeaponGroupType.LINKED.name))

                loadout.addWeaponGroup(wgs)

            }
        }

        //Custom module loading functionality
        json.optJSONObject("moduleVariants")?.let { jsonModules ->
            val moduleSlots = jsonModules.keys()

            while (moduleSlots.hasNext()) {
                val slotId = moduleSlots.next().toString()

                val jsonModuleVariant = jsonModules.optJSONObject(slotId)
                if (jsonModuleVariant == null) {
                    Global.getLogger(this::class.java).warn("Missing or invalid JSON for module slot '$slotId' of variantId '$variantId'")
                    continue
                }

                val (moduleVariant, moduleMissing) = getVariantFromJsonWithMissing(jsonModuleVariant)

                if (moduleMissing.hullIds.size != 0) {
                    Global.getLogger(this::class.java).error("Could not get hullId for module in variant '$variantId' with slotId '$slotId'")
                    continue
                }

                loadout.setModuleVariant(slotId, moduleVariant)
                missingElements.add(moduleMissing)
            }
        }

        //Vanilla module loading functionality, added to maintain vanilla variant loading functionality.
        json.optJSONArray("modules")?.let { modulesArray ->
            for (i in 0 until modulesArray.length()) {
                val module = modulesArray.optJSONObject(i) ?: continue
                val slotId = module.names()?.optString(0) ?: continue

                if(!loadout.getModuleVariant(slotId).isEmptyHullVariant) {
                    Global.getLogger(this.javaClass).warn("Variant '$variantId' has both moduleVariants (custom) and modules (vanilla) in their json file. There should only be one or the other. Skipping vanilla module setup")
                } else {
                    val moduleVariantId = module.optString(slotId, null) ?: continue

                    val variant = Global.getSettings().getVariant(moduleVariantId)
                    if (variant != null) {
                        loadout.setModuleVariant(slotId, variant.clone())
                    } else {
                        Global.getLogger(this.javaClass)
                            .error("Variant '$moduleVariantId' not found for slot '$slotId'")
                    }
                }
            }
        }*/

        return Pair(variant, missingElements)
    }
}