package fleetBuilder.persistence.variant

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.loading.WeaponGroupSpec
import com.fs.starfarer.api.loading.WeaponGroupType
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.ModSettings
import fleetBuilder.util.DisplayMessage.showError
import fleetBuilder.util.allDMods
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib

object DataVariant {
    data class ParsedVariantData(
        val hullId: String,
        val variantId: String = "",
        val displayName: String = "",
        val fluxCapacitors: Int = 0,
        val fluxVents: Int = 0,
        val tags: List<String> = emptyList(),
        val hullMods: List<String> = emptyList(),
        val permaMods: Set<String> = emptySet(),
        val sMods: Set<String> = emptySet(),
        val sModdedBuiltIns: Set<String> = emptySet(),
        val wings: List<String> = emptyList(),
        val weaponGroups: List<ParsedWeaponGroup> = emptyList(),
        val moduleVariants: Map<String, ParsedVariantData> = emptyMap(),
        val isGoalVariant: Boolean = false,
    ) {
        fun allHullMods() = hullMods + sMods + sModdedBuiltIns + permaMods
    }

    data class ParsedWeaponGroup(
        val autofire: Boolean = false,
        val mode: WeaponGroupType = WeaponGroupType.LINKED,
        val weapons: Map<String, String> = emptyMap()
    )

    @JvmOverloads
    fun copyVariant(
        variant: ShipVariantAPI,
        settings: VariantSettings = VariantSettings()
    ): ShipVariantAPI {
        val data = getVariantDataFromVariant(variant, settings)
        return buildVariant(data)
    }

    @JvmOverloads
    fun getVariantDataFromVariant(
        inputVariant: ShipVariantAPI,
        settings: VariantSettings = VariantSettings(),
        filterParsed: Boolean = true
    ): ParsedVariantData {
        val variant =
            if (inputVariant.weaponGroups.isEmpty()) { // Sometimes weapon groups on other fleets are empty. Weapons are stored by weapon groups, so make sure to generate some weapon groups before proceeding
                val variant = inputVariant.clone()
                variant.autoGenerateWeaponGroups()
                variant
            } else {
                inputVariant
            }


        /*val allDMods = VariantLib.getAllDMods()
        val allHiddenEverywhereMods = VariantLib.getAllHiddenEverywhereMods()

        // Mods that should be excluded
        val excludedMods = buildSet {
            addAll(ModSettings.getHullModsToNeverSave())
            addAll(settings.excludeHullModsWithID)
            if (!settings.includeDMods) addAll(allDMods)
            if (!settings.includeHiddenMods) addAll(allHiddenEverywhereMods)
        }*/

        // Set of all possible mod IDs on the variant
        val allModIds = buildSet {
            addAll(variant.hullMods)
            addAll(variant.sMods)
            addAll(variant.sModdedBuiltIns)
            addAll(variant.suppressedMods)
            addAll(variant.permaMods)
        }

        val sMods = mutableSetOf<String>()
        val sModdedBuiltIns = mutableSetOf<String>()
        val permaMods = mutableSetOf<String>()
        val hullMods = mutableListOf<String>()

        for (mod in allModIds) {
            //if (mod in excludedMods) continue

            when {
                mod in variant.sModdedBuiltIns && settings.applySMods -> sModdedBuiltIns += mod
                mod in variant.sMods && settings.applySMods -> sMods += mod
                mod in variant.permaMods &&
                        mod !in variant.hullSpec.builtInMods &&
                        mod !in sMods && mod !in sModdedBuiltIns -> permaMods += mod

                mod in variant.hullMods &&
                        mod !in variant.hullSpec.builtInMods &&
                        mod !in sMods && mod !in sModdedBuiltIns && mod !in permaMods -> hullMods += mod
            }
        }

        // Add built-in DMods as default behavior is to exclude built in DMods when loading a variant.
        variant.allDMods()
            .filter { it in variant.hullSpec.builtInMods && it !in hullMods } //&& it !in excludedMods }
            .forEach { hullMods += it }

        val data = ParsedVariantData(
            hullId = variant.hullSpec.hullId,
            variantId = variant.hullVariantId,
            displayName = variant.displayName,
            fluxCapacitors = variant.numFluxCapacitors,
            fluxVents = variant.numFluxVents,
            tags = variant.tags.toList(),
            hullMods = hullMods,
            permaMods = permaMods,
            sMods = sMods,
            sModdedBuiltIns = sModdedBuiltIns,
            wings = variant.nonBuiltInWings,
            weaponGroups = variant.weaponGroups.map { group ->
                ParsedWeaponGroup(
                    autofire = group.isAutofireOnByDefault,
                    mode = group.type,
                    weapons = group.slots
                        .mapNotNull { slotId ->
                            val weaponId = variant.getWeaponId(slotId)
                            if (weaponId != null) {
                                slotId to weaponId
                            } else {
                                null
                            }
                        }.toMap()
                )
            },
            moduleVariants = variant.moduleSlots
                .mapNotNull { slotId ->
                    val module = variant.getModuleVariant(slotId) ?: return@mapNotNull null
                    val parsedModule = getVariantDataFromVariant(module, settings)
                    slotId to parsedModule
                }.toMap(),
            isGoalVariant = variant.isGoalVariant,
        )

        if (filterParsed)
            return filterParsedVariantData(data, settings)
        else
            return data
    }

    @JvmOverloads
    fun filterParsedVariantData(
        data: ParsedVariantData,
        settings: VariantSettings = VariantSettings(),
        missing: MissingElements = MissingElements()
    ): ParsedVariantData {

        fun shouldKeepMod(modId: String): Boolean {
            if (ModSettings.getHullModsToNeverSave().contains(modId)) return false
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

        fun shouldKeepTag(tagId: String): Boolean {
            return tagId !in settings.excludeTagsWithID
        }

        // Remove entries from missing that were filtered out, as they aren't missing if they weren't supposed to be included in the first place.
        missing.hullModIds.retainAll { shouldKeepMod(it) }
        missing.weaponIds.retainAll { shouldKeepWeapon(it) }
        missing.wingIds.retainAll { shouldKeepWing(it) }

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
            val filteredSlots = group.weapons.filterValues(::shouldKeepWeapon)
            group.copy(weapons = filteredSlots)
        }

        val filteredTags = if (settings.includeTags) {
            data.tags.filter(::shouldKeepTag)
        } else emptySet()

        return data.copy(
            hullMods = filteredHullMods,
            permaMods = filteredPermaMods.toSet(),
            sMods = filteredSMods.toSet(),
            sModdedBuiltIns = filteredSModdedBuiltIns.toSet(),
            wings = filteredWings,
            weaponGroups = filteredWeaponGroups,
            tags = filteredTags.toList()
        )
    }

    @JvmOverloads
    fun validateAndCleanVariantData(
        data: ParsedVariantData,
        missing: MissingElements = MissingElements(),
    ): ParsedVariantData {
        val settingsAPI = Global.getSettings()

        // --- Hull ID ---
        val validHullId = data.hullId.takeIf { hullId ->
            hullId.isNotBlank() && settingsAPI.allShipHullSpecs.any { it.hullId == hullId }
        } ?: settingsAPI.getVariant(settingsAPI.getString("errorShipVariant")).hullSpec.hullId.also {
            missing.hullIds.add(data.hullId)
        }

        // --- Variant ID ---
        val fixedVariantId = data.variantId.ifBlank {
            "MissingVariantID_" + Misc.genUID()
        }

        // --- Display Name ---
        val fixedDisplayName = data.displayName.ifBlank {
            "No Name"
        }

        // --- HullMods ---
        val allHullMods = settingsAPI.allHullModSpecs.map { it.id }.toSet()

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
        val allWingIds = settingsAPI.allFighterWingSpecs.map { it.id }.toSet()
        val cleanWings = data.wings.mapIndexed { _, wingId ->
            if (wingId !in allWingIds && wingId.isNotBlank()) {
                missing.wingIds.add(wingId)
                ""
            } else wingId
        }

        // --- Weapon Groups ---
        val allWeapons = settingsAPI.actuallyAllWeaponSpecs.map { it.weaponId }.toSet()
        val cleanWeaponGroups = data.weaponGroups.map { wg ->
            val cleanedSlots = wg.weapons.filter { (_, weaponId) ->
                val valid = weaponId in allWeapons
                if (!valid) missing.weaponIds.add(weaponId)
                valid
            }
            wg.copy(weapons = cleanedSlots)
        }

        // --- Module Variants ---
        val cleanedModuleVariants = mutableMapOf<String, ParsedVariantData>()
        data.moduleVariants.forEach { (slotId, moduleData) ->
            val cleanedModule = validateAndCleanVariantData(moduleData, missing)
            cleanedModuleVariants[slotId] = cleanedModule
        }

        val cleanedData = data.copy(
            hullId = validHullId,
            variantId = fixedVariantId,
            displayName = fixedDisplayName,
            hullMods = cleanHullMods.toList(),
            permaMods = cleanPermaMods.toSet(),
            sMods = cleanSMods.toSet(),
            sModdedBuiltIns = cleanSModdedBuiltIns.toSet(),
            wings = cleanWings,
            weaponGroups = cleanWeaponGroups,
            moduleVariants = cleanedModuleVariants
        )

        return cleanedData
    }

    fun buildVariant(
        data: ParsedVariantData
    ): ShipVariantAPI {
        val hullSpec = Global.getSettings().getHullSpec(data.hullId)
        val loadout = Global.getSettings().createEmptyVariant(hullSpec.hullId, hullSpec)

        //Remove default DMods
        if (ModSettings.removeDefaultDMods) {
            loadout.allDMods().forEach {
                loadout.hullMods.remove(it)
            }
        }

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

        val wingOffset = hullSpec.builtInWings.size
        data.wings.forEachIndexed { i, wingId ->
            if (wingId.isNotEmpty()) {
                loadout.setWingId(wingOffset + i, wingId)
            }
        }

        data.weaponGroups.forEach { wgData ->
            val wg = WeaponGroupSpec()

            wgData.weapons.forEach { (slotId, weaponId) ->
                if (hullSpec.isBuiltIn(slotId)) {
                    wg.addSlot(slotId)
                } else {
                    loadout.addWeapon(slotId, weaponId)
                    wg.addSlot(slotId)
                }
            }

            wg.isAutofireOnByDefault = wgData.autofire
            wg.type = wgData.mode
            loadout.addWeaponGroup(wg)
        }

        data.moduleVariants.forEach { (slotId, moduleData) ->
            val variant = buildVariant(moduleData)

            loadout.setModuleVariant(slotId, variant)

            try { // Can't check for what module slots the HullSpec can support, so we have to do this instead
                loadout.moduleSlots == null
            } catch (_: Exception) {
                showError("${loadout.hullSpec.hullId} Does not contain module slot $slotId")
                return VariantLib.createErrorVariant("BAD_MODULE_SLOT:{$slotId}")
            }
        }


        return loadout
    }

    @JvmOverloads
    fun buildVariantFull(
        data: ParsedVariantData,
        settings: VariantSettings = VariantSettings(),
        missing: MissingElements = MissingElements()
    ): ShipVariantAPI {
        val ourMissing = MissingElements()

        val cleanedData = validateAndCleanVariantData(data, ourMissing)
        val filteredData = filterParsedVariantData(cleanedData, settings, ourMissing)

        val variant = buildVariant(filteredData)

        missing.add(ourMissing)

        return variant
    }
}