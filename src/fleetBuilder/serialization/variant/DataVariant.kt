package fleetBuilder.serialization.variant

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.loading.VariantSource
import com.fs.starfarer.api.loading.WeaponGroupSpec
import com.fs.starfarer.api.loading.WeaponGroupType
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.FBConst
import fleetBuilder.core.FBSettings
import fleetBuilder.core.displayMessage.DisplayMessage.showError
import fleetBuilder.serialization.MissingContent
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.api.kotlin.allDMods
import fleetBuilder.util.api.kotlin.createHullVariant
import fleetBuilder.util.api.kotlin.getCompatibleDLessHullId
import fleetBuilder.util.api.kotlin.getModules

object DataVariant {

    // Hullmods sets do not contain duplicates. A hullmod will only be in the single set that is relevant to it. E.G, an SMod will only be in the sMods set and nowhere else.
    data class ParsedVariantData(
        val hullId: String,
        val variantId: String = "",
        val displayName: String = "",
        val fluxCapacitors: Int = -1,
        val fluxVents: Int = -1,
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
        filterParsed: Boolean = false
    ): ShipVariantAPI {
        val data = getVariantDataFromVariant(variant, filterParsed = filterParsed)
        return buildVariant(data)
    }

    fun copyVariant(
        variant: ShipVariantAPI,
        settings: VariantSettings
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

        // Set of all hullmods IDs on the variant
        val allModIds = buildSet {
            addAll(variant.hullMods)
            addAll(variant.sMods)
            addAll(variant.sModdedBuiltIns)
            addAll(variant.permaMods)
        }

        val sMods = mutableSetOf<String>()
        val sModdedBuiltIns = mutableSetOf<String>()
        val permaMods = mutableSetOf<String>()
        val hullMods = mutableListOf<String>()

        for (mod in allModIds) {
            when {
                mod in variant.sModdedBuiltIns && (mod in variant.hullSpec.builtInMods || mod in variant.permaMods) -> sModdedBuiltIns += mod
                mod in variant.sMods -> sMods += mod
                mod in variant.permaMods &&
                        mod !in variant.hullSpec.builtInMods &&
                        mod !in sMods && mod !in sModdedBuiltIns -> permaMods += mod

                mod in variant.hullMods &&
                        mod !in variant.hullSpec.builtInMods &&
                        mod !in sMods && mod !in sModdedBuiltIns && mod !in permaMods -> hullMods += mod
            }
        }

        // Add built-in DMods as default behavior is to exclude these.
        variant.allDMods()
            .filter { it in variant.hullSpec.builtInMods && it !in hullMods } //&& it !in excludedMods }
            .forEach { hullMods += it }

        val data = ParsedVariantData(
            hullId = variant.hullSpec.getCompatibleDLessHullId(true), //DMods are already included, get the D less ID for simplicity.
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
            moduleVariants = variant.getModules().map { (slot, module) ->
                slot to getVariantDataFromVariant(module, settings)
            }.toMap(),
            isGoalVariant = variant.isGoalVariant,
        )

        return if (filterParsed)
            filterParsedVariantData(data, settings)
        else
            data
    }

    @JvmOverloads
    fun filterParsedVariantData(
        data: ParsedVariantData,
        settings: VariantSettings = VariantSettings(),
        missing: MissingContent = MissingContent()
    ): ParsedVariantData {

        fun shouldKeepMod(modId: String): Boolean {
            if (FBSettings.getHullModsToNeverSave().contains(modId)) return false
            if (modId in settings.excludeHullModsWithID) return false
            if (!settings.includeDMods && LookupUtils.getAllDMods().contains(modId)) return false
            if (!settings.includeHiddenMods && LookupUtils.getAllHiddenEverywhereMods().contains(modId)) return false
            if (LookupUtils.getHullModSpec(modId)?.hasTag(FBConst.NO_COPY_TAG) == true) return false
            return true
        }

        fun shouldKeepWeapon(weaponId: String): Boolean {
            if (weaponId in settings.excludeWeaponsWithID) return false
            if (LookupUtils.getWeaponSpec(weaponId)?.hasTag(FBConst.NO_COPY_TAG) == true) return false
            return true
        }

        fun shouldKeepWing(wingId: String): Boolean {
            if (wingId in settings.excludeWingsWithID) return false
            if (LookupUtils.getFighterWingSpec(wingId)?.hasTag(FBConst.NO_COPY_TAG) == true) return false
            return true
        }

        fun shouldKeepTag(tagId: String): Boolean {
            return tagId !in settings.excludeTagsWithID && !tagId.startsWith("#")
        }

        // Remove entries from missing that were filtered out, as they aren't missing if they weren't supposed to be included in the first place.
        if (!settings.includeHullMods) missing.hullModIds.clear() else
            missing.hullModIds.retainAll { shouldKeepMod(it) }

        if (!settings.includeWeapons) missing.weaponIds.clear() else
            missing.weaponIds.retainAll { shouldKeepWeapon(it) }

        if (!settings.includeWings) missing.wingIds.clear() else
            missing.wingIds.retainAll { shouldKeepWing(it) }

        // Filter hull mods
        val filteredHullMods = if (!settings.includeHullMods) mutableListOf() else
            data.hullMods.filter(::shouldKeepMod).toMutableList()
        val filteredPermaMods = if (!settings.includeHullMods) emptyList() else
            data.permaMods.filter(::shouldKeepMod)
        val filteredSMods = if (!settings.includeHullMods) emptyList() else
            if (settings.applySMods) {
                data.sMods.filter(::shouldKeepMod)
            } else {
                filteredHullMods.addAll(data.sMods.filter { it !in filteredHullMods })
                emptyList()
            }

        val filteredSModdedBuiltIns = if (!settings.includeHullMods) emptyList() else
            if (settings.applySMods) {
                data.sModdedBuiltIns.filter(::shouldKeepMod)
            } else emptyList()

        val filteredWings = if (!settings.includeWings) emptyList() else
            data.wings.filter(::shouldKeepWing)

        val filteredWeaponGroups = if (!settings.includeWeapons) emptyList() else
            data.weaponGroups.map { group ->
                val filteredSlots = group.weapons.filterValues(::shouldKeepWeapon)
                group.copy(weapons = filteredSlots)
            }

        val filteredTags = if (!settings.includeTags) emptySet() else
            data.tags.filter(::shouldKeepTag)

        return data.copy(
            hullMods = filteredHullMods,
            permaMods = filteredPermaMods.toSet(),
            sMods = filteredSMods.toSet(),
            sModdedBuiltIns = filteredSModdedBuiltIns.toSet(),
            wings = filteredWings,
            weaponGroups = filteredWeaponGroups,
            tags = filteredTags.toList(),
            fluxCapacitors = if (!settings.includeFlux) -1 else
                data.fluxCapacitors,
            fluxVents = if (!settings.includeFlux) -1 else
                data.fluxVents
        )
    }

    @JvmOverloads
    fun validateAndCleanVariantData(
        data: ParsedVariantData,
        missing: MissingContent = MissingContent(),
    ): ParsedVariantData {
        // --- Hull ID ---
        val validHullId = if (data.hullId in LookupUtils.getHullIDSet()) {
            data.hullId
        } else {
            missing.hullIds.add(data.hullId)
            null
        }

        // --- Variant ID ---
        val fixedVariantId = data.variantId.ifBlank {
            "MissingVariantID_" + Misc.genUID()
        }

        // --- Display Name ---
        val fixedDisplayName = data.displayName.ifBlank {
            ""
        }

        // --- HullMods ---
        val allHullMods = LookupUtils.getHullModIDSet()

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
        val allWingIds = LookupUtils.getFighterWingIDSet()
        val cleanWings = data.wings.mapIndexed { _, wingId ->
            if (wingId !in allWingIds && wingId.isNotBlank()) {
                missing.wingIds.add(wingId)
                ""
            } else wingId
        }

        // --- Weapon Groups ---
        val allWeapons = LookupUtils.getActuallyAllWeaponSpecIDSet()
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
            hullId = validHullId ?: LookupUtils.getErrorVariantHullID(),
            variantId = fixedVariantId,
            displayName = fixedDisplayName,
            hullMods = cleanHullMods.toList(),
            permaMods = cleanPermaMods.toSet(),
            sMods = cleanSMods.toSet(),
            sModdedBuiltIns = cleanSModdedBuiltIns.toSet(),
            wings = cleanWings,
            weaponGroups = cleanWeaponGroups,
            moduleVariants = cleanedModuleVariants,
            tags = if (validHullId == null) (data.tags + FBConst.FB_ERROR_TAG) else data.tags
        )

        return cleanedData
    }

    fun buildVariant(
        data: ParsedVariantData
    ): ShipVariantAPI {
        val settings = Global.getSettings()

        val hullSpec = settings.getHullSpec(data.hullId)
        val loadout = settings.createHullVariant(hullSpec)
        loadout.weaponGroups.clear()

        loadout.source = VariantSource.REFIT
        loadout.hullVariantId = data.variantId
        loadout.setVariantDisplayName(data.displayName)
        loadout.isGoalVariant = data.isGoalVariant
        if (data.fluxCapacitors > -1)
            loadout.numFluxCapacitors = data.fluxCapacitors
        if (data.fluxVents > -1)
            loadout.numFluxVents = data.fluxVents

        data.tags.forEach { loadout.addTag(it) }

        if (loadout.hasTag(FBConst.FB_ERROR_TAG))
            return loadout

        //Remove default DMods
        if (FBSettings.removeDefaultDMods) {
            loadout.allDMods().forEach {
                loadout.hullMods.remove(it)
            }
        }

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

            if (!hullSpec.builtInMods.contains(modId)) // If SModded built in, but hullspec doesn't have this mod to build in?
                loadout.addPermaMod(modId, false) // Assume it's a built in perma mod instead. (See Roider Union MIDAS)
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

            if (loadout.stationModules.containsKey(slotId)) {
                loadout.setModuleVariant(slotId, variant)
            } else {
                showError("${loadout.hullSpec.hullId} Does not contain module slot $slotId.")
                return@forEach
            }

            try { // It's hard to check for what module slots the HullSpec can support, so do this just in case to prevent crashes further ahead.
                @SuppressWarnings
                loadout.moduleSlots == null // This isn't meant to be used, it is only meant to check for a crash by accessing loadout.moduleSlots
            } catch (_: Exception) {
                showError("${loadout.hullSpec.hullId} Does not contain module slot $slotId. Removing variant to avoid crash")
                return VariantUtils.createErrorVariant("BAD_MODULE_SLOT:{$slotId}")
            }
        }

        return loadout
    }

    @JvmOverloads
    fun buildVariantFull(
        data: ParsedVariantData,
        settings: VariantSettings = VariantSettings(),
        missing: MissingContent = MissingContent()
    ): ShipVariantAPI {
        val ourMissing = MissingContent()

        val cleanedData = validateAndCleanVariantData(data, ourMissing)
        val filteredData = filterParsedVariantData(cleanedData, settings, ourMissing)

        val variant = buildVariant(filteredData)

        missing.add(ourMissing)

        return variant
    }
}