package fleetBuilder.persistence.variant

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.loading.WeaponGroupType
import fleetBuilder.config.ModSettings
import fleetBuilder.persistence.variant.DataVariant.buildVariantFull
import fleetBuilder.persistence.variant.DataVariant.getVariantDataFromVariant
import fleetBuilder.persistence.variant.VariantMisc.getSourceModsFromVariant
import fleetBuilder.util.FBMisc
import fleetBuilder.util.allDMods
import fleetBuilder.util.completelyRemoveMod
import fleetBuilder.util.containsString
import fleetBuilder.util.optJSONArrayToStringList
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib
import fleetBuilder.variants.VariantLib.getCoreVariantsForEffectiveHullspec
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.orEmpty

object JSONVariant {
    @JvmOverloads
    fun extractVariantDataFromJson(
        json: JSONObject,
        missing: MissingElements = MissingElements()
    ): DataVariant.ParsedVariantData {
        val variantId = json.optString("variantId", "")
        val hullId = json.optString("hullId", "")
        val tags = json.optJSONArrayToStringList("tags")


        val pickRandomVariant = json.optBoolean("pick_random_variant", false)

        if (pickRandomVariant) {
            fun getParsed(possibleVariants: List<String>): DataVariant.ParsedVariantData {
                val randomVariantID = possibleVariants.random()
                val randomVariantJson = saveVariantToJson(Global.getSettings().getVariant(randomVariantID), includeModInfo = false)
                val randomVariantData = extractVariantDataFromJson(randomVariantJson)
                return randomVariantData.copy(variantId = variantId, tags = randomVariantData.tags + tags)
            }

            val possibleVariants = Global.getSettings().hullIdToVariantListMap[hullId].orEmpty()

            if (possibleVariants.isNotEmpty())
                return getParsed(possibleVariants)

            val hullSpec = Global.getSettings().allShipHullSpecs.find { it.hullId == hullId }
            if (hullSpec != null) {
                val effectivePossibleVariants = getCoreVariantsForEffectiveHullspec(hullSpec).map { it.hullVariantId }
                if (effectivePossibleVariants.isNotEmpty())
                    return getParsed(effectivePossibleVariants)
            }
        }

        val displayName = json.optString("displayName", "")

        val fluxCapacitors = json.optInt("fluxCapacitors", 0)
        val fluxVents = json.optInt("fluxVents", 0)
        val isGoalVariant = json.optBoolean("goalVariant", false)

        val hullMods = json.optJSONArrayToStringList("hullMods")
        val permaMods = json.optJSONArrayToStringList("permaMods")
        val sMods = json.optJSONArrayToStringList("sMods")

        var sModdedBuiltIns = json.optJSONArrayToStringList("sModdedBuiltIns")
        if (sModdedBuiltIns.isEmpty())
            sModdedBuiltIns = json.optJSONArrayToStringList("sModdedbuiltins") // Legacy code due to unfortunate typo

        val wings = json.optJSONArrayToStringList("wings")

        val weaponGroups = mutableListOf<DataVariant.ParsedWeaponGroup>()

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

                weaponGroups.add(DataVariant.ParsedWeaponGroup(isAutofire, type, slotToWeaponId))
            }
        }

        val moduleVariants = mutableMapOf<String, DataVariant.ParsedVariantData>()

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

        missing.gameMods.addAll(FBMisc.getModInfosFromJson(json))

        return DataVariant.ParsedVariantData(
            variantId = variantId, hullId = hullId, displayName = displayName, fluxCapacitors = fluxCapacitors, fluxVents = fluxVents,
            tags = tags.toList(), hullMods = hullMods.toList(), permaMods = permaMods.toSet(), sMods = sMods.toSet(), sModdedBuiltIns = sModdedBuiltIns.toSet(), wings = wings,
            weaponGroups = weaponGroups, moduleVariants = moduleVariants, isGoalVariant = isGoalVariant
        )
    }

    @JvmOverloads
    fun getVariantFromJson(
        json: JSONObject,
        settings: VariantSettings = VariantSettings(),
        missing: MissingElements = MissingElements()
    ): ShipVariantAPI {
        val parsed = extractVariantDataFromJson(json, missing)

        return buildVariantFull(parsed, settings, missing)
    }

    @JvmOverloads
    fun saveAndLoadVariant(variant: ShipVariantAPI, settings: VariantSettings = VariantSettings()): ShipVariantAPI {
        val json = saveVariantToJson(variant, settings)
        return getVariantFromJson(json, settings)
    }

    @JvmOverloads
    fun saveVariantToJson(
        variant: ShipVariantAPI,
        settings: VariantSettings = VariantSettings(),
        includeModInfo: Boolean = true
    ): JSONObject {
        val data = getVariantDataFromVariant(variant, settings)
        return saveVariantToJson(data, settings, includeModInfo)
    }

    @JvmOverloads
    fun saveVariantToJson(
        data: DataVariant.ParsedVariantData,
        settings: VariantSettings = VariantSettings(),
        includeModInfo: Boolean = true
    ): JSONObject {
        val jsonVariant = JSONObject()

        jsonVariant.put("variantId", data.variantId)
        jsonVariant.put("displayName", data.displayName)
        jsonVariant.put("hullId", data.hullId)
        jsonVariant.put("fluxCapacitors", data.fluxCapacitors)
        jsonVariant.put("fluxVents", data.fluxVents)
        jsonVariant.put("hullMods", JSONArray(data.hullMods))
        jsonVariant.put("permaMods", JSONArray(data.permaMods))
        jsonVariant.put("sMods", JSONArray(data.sMods))

        if (data.tags.isNotEmpty())
            jsonVariant.put("tags", JSONArray(data.tags))


        if (data.sModdedBuiltIns.isNotEmpty())
            jsonVariant.put("sModdedBuiltIns", JSONArray(data.sModdedBuiltIns))

        if (data.wings.isNotEmpty())
            jsonVariant.put("wings", JSONArray(data.wings))

        val weaponGroupsJson = JSONArray()
        for (wg in data.weaponGroups) {
            val groupJson = JSONObject()

            // Add autofire and mode
            groupJson.put("autofire", wg.autofire)
            groupJson.put("mode", wg.mode)

            // Add weapons map
            val weaponsJson = JSONObject()
            wg.weapons.forEach { (slotId, weaponId) ->
                weaponsJson.put(slotId, weaponId)
            }

            groupJson.put("weapons", weaponsJson)

            weaponGroupsJson.put(groupJson)
        }
        jsonVariant.put("weaponGroups", weaponGroupsJson)

        val jsonModules = JSONObject()
        data.moduleVariants.forEach { (moduleSlot, moduleData) ->
            val moduleJSON = saveVariantToJson(moduleData, settings, false)
            jsonModules.put(moduleSlot, moduleJSON)
        }
        if (jsonModules.length() != 0)
            jsonVariant.put("moduleVariants", jsonModules)

        if (includeModInfo)
            addVariantSourceModsToJson(data, jsonVariant)

        return jsonVariant
    }

    fun addVariantSourceModsToJson(
        data: DataVariant.ParsedVariantData,
        json: JSONObject
    ) {
        val addedModIds = mutableSetOf<Triple<String, String, String>>()
        getSourceModsFromVariant(addedModIds, data)

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
}