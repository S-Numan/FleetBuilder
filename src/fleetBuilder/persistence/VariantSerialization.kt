package fleetBuilder.persistence

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.loading.WeaponGroupSpec
import com.fs.starfarer.api.loading.WeaponGroupType
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.ModSettings.hullModsToNeverSave
import fleetBuilder.util.MISC
import fleetBuilder.util.MISC.getMissingFromModInfo
import fleetBuilder.util.completelyRemoveMod
import fleetBuilder.util.containsString
import fleetBuilder.util.toBinary
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib
import org.json.JSONArray
import org.json.JSONObject

object VariantSerialization {

    fun getVariantFromJsonWithMissing(json: JSONObject): Pair<ShipVariantAPI, MissingElements> {
        val missingElements = MissingElements()
        getMissingFromModInfo(json, missingElements)

        val hullId = json.optString("hullId")
        if (hullId.isEmpty()) {
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
            Global.getLogger(this.javaClass)
                .warn("When loading JSONObject variant with hullId $hullId, failed to find variantID. Setting a random UID in it's place")
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
            Global.getLogger(this.javaClass)
                .warn("When loading JSONObject variant with hullId $hullId and variantId $variantId, failed to find fluxCapacitors")
            0
        }
        loadout.numFluxCapacitors = fluxCaps

        val fluxVents = if (json.has("fluxVents")) {
            json.optInt("fluxVents", 0)
        } else {
            Global.getLogger(this.javaClass)
                .warn("When loading JSONObject variant with hullId $hullId and variantId $variantId, failed to find fluxVents")
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
                    if (loadout.hullSpec.builtInMods.contains(modId))
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
                    if (loadout.hullSpec.isBuiltIn(slotId)) { // If slot is built in, no need to add the weapon the variant, it's already there.
                        wgs.addSlot(slotId)
                    } else if (Global.getSettings().actuallyAllWeaponSpecs.any { it.weaponId == weaponId }) {
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
                    Global.getLogger(this::class.java)
                        .warn("Missing or invalid JSON for module slot '$slotId' of variantId '$variantId'")
                    continue
                }

                val (moduleVariant, moduleMissing) = getVariantFromJsonWithMissing(jsonModuleVariant)

                if (moduleMissing.hullIds.size != 0) {
                    Global.getLogger(this::class.java)
                        .error("Could not get hullId for module in variant '$variantId' with slotId '$slotId'")
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

                if (!loadout.getModuleVariant(slotId).isEmptyHullVariant) {
                    Global.getLogger(this.javaClass)
                        .warn("Variant '$variantId' has both moduleVariants (custom) and modules (vanilla) in their json file. There should only be one or the other. Skipping vanilla module setup")
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
        }

        return Pair(loadout, missingElements)
    }

    fun getVariantFromJson(json: JSONObject): ShipVariantAPI {
        return getVariantFromJsonWithMissing(json).first
    }


    data class VariantSettings(
        var applySMods: Boolean = true,//If false, SMods will be treated like normal mods.
        var includeDMods: Boolean = true,
        var includeHiddenMods: Boolean = true,
        var includeTags: Boolean = true,
    )

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
                if (weaponId != null) {
                    weaponsJson.put(slotId, weaponId)
                }
            }
            groupJson.put("weapons", weaponsJson)

            weaponGroupsJson.put(groupJson)
        }
        json.put("weaponGroups", weaponGroupsJson)

        hullModsToNeverSave.forEach { modId ->
            variant.completelyRemoveMod(modId)
        }

        if (!settings.includeHiddenMods) {
            val allModIds = buildSet {
                addAll(variant.hullMods)
                addAll(variant.sMods)
                addAll(variant.suppressedMods)
                addAll(variant.permaMods)
            }

            allModIds.forEach { modId ->
                val mod = Global.getSettings().getHullModSpec(modId)
                if (mod.isHiddenEverywhere) {
                    variant.completelyRemoveMod(modId)
                }
            }
        }

        if (settings.applySMods && variant.sModdedBuiltIns.isNotEmpty()) {
            val sModdedbuiltins = JSONArray()
            variant.sModdedBuiltIns.forEach { mod ->
                sModdedbuiltins.put(mod)
            }
            json.put("sModdedbuiltins", sModdedbuiltins)
        }

        val allDmods = VariantLib.getAllDMods()

        val hullMods = JSONArray()
        val sMods = JSONArray()
        val permaMods = JSONArray()

        if (settings.applySMods) {
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
                if (!settings.includeDMods && allDmods.contains(mod))
                    return@forEach
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
                || hullModsToNeverSave.contains(mod)
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


    val sep = ","
    val fieldSep = "%"//Only two ascii characters that cannot be in a variant display name
    val metaSep = "$"//Only two ascii characters that cannot be in a variant display name
    val weaponGroupSep = ">"

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
            Global.getLogger(this.javaClass).warn("When loading JSONObject variant with hullId $hullId, failed to find variantID. Setting a random UID in it's place")
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