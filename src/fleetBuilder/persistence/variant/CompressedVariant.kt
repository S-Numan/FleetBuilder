package fleetBuilder.persistence.variant

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.loading.WeaponGroupType
import fleetBuilder.persistence.CompressedMisc.fieldSep
import fleetBuilder.persistence.CompressedMisc.metaSep
import fleetBuilder.persistence.CompressedMisc.sep
import fleetBuilder.persistence.CompressedMisc.weaponGroupSep
import fleetBuilder.persistence.variant.DataVariant.buildVariantFull
import fleetBuilder.persistence.variant.DataVariant.getVariantDataFromVariant
import fleetBuilder.persistence.variant.VariantMisc.getSourceModsFromVariant
import fleetBuilder.util.lib.CompressionUtil
import fleetBuilder.util.DisplayMessage.showError
import fleetBuilder.util.toBinary
import fleetBuilder.variants.GameModInfo
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.component3
import kotlin.collections.windowed

object CompressedVariant {
    @JvmOverloads
    fun extractVariantDataFromCompString(
        comp: String,
        missing: MissingElements = MissingElements()
    ): DataVariant.ParsedVariantData? {
        val metaIndexStart = comp.indexOf(metaSep)
        val metaIndexEnd = comp.indexOf(metaSep, metaIndexStart + 1)

        if (metaIndexStart == -1 || metaIndexEnd == -1) {
            //showError("Invalid format: missing meta version section.")
            return null
        }

        val metaVersion = comp.substring(metaIndexStart + 1, metaIndexEnd)
        if (metaVersion.isEmpty())
            return null

        var fullData: String? = null

        if (metaVersion == "v0") { // Compressed

            // Extract the compressed portion after the second metaSep
            val compressedData = comp.substring(metaIndexEnd + 1)

            fullData = try {
                CompressionUtil.decompressString(compressedData)
            } catch (e: Exception) {
                showError("Error decompressing variant data", compressedData, e)
                null
            }
            if (fullData.isNullOrBlank()) return null
        } else if (metaVersion == "V0") { // Non compressed
            fullData = comp.substring(metaIndexEnd + 1)
        } else {
            showError("Invalid meta version: $metaVersion")
            return null
        }

        // From here, your original logic applies — parse fields
        val firstFieldSep = fullData.indexOf(fieldSep)
        if (firstFieldSep == -1) {
            //showError("Invalid format: missing field separator.")
            return null
        }

        val modInfoBulk = fullData.substring(0, firstFieldSep)
        val modInfos = modInfoBulk.split(fieldSep)

        val gameMods: Set<GameModInfo> = modInfos
            .mapNotNull { mod ->
                val parts = mod.split(sep)
                if (parts.size == 3) {
                    val (id, name, ver) = parts
                    GameModInfo(id, name, ver)
                } else null // Skip malformed lines
            }
            .toSet()

        missing.gameMods.addAll(gameMods)

        val allData = fullData.substring(firstFieldSep + 1)

        val segments = allData.split(metaSep)
        val rootSegment = segments[0]
        val moduleSegments = segments.drop(1)

        try {
            return extractModuleFromCompString(rootSegment, moduleSegments)
        } catch (e: Exception) {
            showError("Error parsing variant data", e)
            return null
        }
    }


    private fun extractModuleFromCompString(
        data: String,
        moduleSegments: List<String>
    ): DataVariant.ParsedVariantData {
        val fields = data.split(fieldSep)

        val hullId = fields[0]
        val variantId = fields[1]
        val displayName = fields[2]

        //val variantId = "${hullId}_$displayName"

        val fluxCap = fields[3].toInt()
        val fluxVents = fields[4].toInt()

        val weaponGroupString = fields[5]
        val fittedWings = fields[6].takeIf { it.isNotBlank() }?.split(sep) ?: emptyList()
        val hullMods = fields[7].takeIf { it.isNotBlank() }?.split(sep) ?: emptyList()
        val sMods = fields[8].takeIf { it.isNotBlank() }?.split(sep) ?: emptyList()
        val sModdedBuiltIns = fields[9].takeIf { it.isNotBlank() }?.split(sep) ?: emptyList()
        val permaMods = fields[10].takeIf { it.isNotBlank() }?.split(sep) ?: emptyList()
        val tags = fields.getOrNull(11)?.takeIf { it.isNotBlank() }?.split(sep) ?: emptyList()

        val weaponGroups = if (weaponGroupString.isNotBlank()) {
            weaponGroupString.split(weaponGroupSep).map { group ->
                val parts = group.split(sep)
                val mode = parts[0].toInt()
                val autofire = parts[1] == "1"

                val weapons = parts
                    .drop(2)
                    .windowed(2, 2)
                    .associate { (slot, weapon) -> slot to weapon }
                DataVariant.ParsedWeaponGroup(autofire, if (mode == 0) WeaponGroupType.LINKED else WeaponGroupType.ALTERNATING, weapons)
            }
        } else {
            emptyList()
        }

        val modules = moduleSegments
            .mapNotNull { segment ->
                val parts = segment.split('%', limit = 2)
                if (parts.size == 2) {
                    val (slot, moduleData) = parts
                    slot to extractModuleFromCompString(moduleData, emptyList())
                } else null
            }
            .toMap()


        return DataVariant.ParsedVariantData(
            variantId = variantId,
            hullId = hullId,
            displayName = displayName,
            fluxCapacitors = fluxCap,
            fluxVents = fluxVents,
            tags = tags,
            hullMods = hullMods,
            permaMods = permaMods.toSet(),
            sMods = sMods.toSet(),
            sModdedBuiltIns = sModdedBuiltIns.toSet(),
            wings = fittedWings,
            weaponGroups = weaponGroups,
            moduleVariants = modules,
            isGoalVariant = false
        )
    }

    @JvmOverloads
    fun getVariantFromCompString(
        comp: String,
        settings: VariantSettings = VariantSettings(),
        missing: MissingElements = MissingElements(),
    ): ShipVariantAPI {
        val parsed = extractVariantDataFromCompString(comp, missing)
            ?: run {
                DataVariant.ParsedVariantData("")
            }

        return buildVariantFull(parsed, settings, missing)
    }

    @JvmOverloads
    fun saveVariantToCompString(
        variant: ShipVariantAPI,
        settings: VariantSettings = VariantSettings(),
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true
    ): String {
        return saveVariantToCompString(getVariantDataFromVariant(variant, settings), includePrepend = includePrepend, includeModInfo = includeModInfo)
    }

    @JvmOverloads
    fun saveVariantToCompString(
        data: DataVariant.ParsedVariantData,
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true,
        compressString: Boolean = true,
    ): String {
        val structureVersion =
            if (compressString) "v0" // Variant compressed 0
            else "V0" // Variant uncompressed 0


        val ver = "$metaSep$structureVersion$metaSep"//v for variant. To identify the type of compressed string without having to decompress it first. member would be m, fleet would be f, person would be p, etc.


        var compressedVariant = ""
        compressedVariant += saveModuleVariantToCompString(data)

        data.moduleVariants.forEach { (moduleSlot, module) ->
            val compressedModuleVariant = saveModuleVariantToCompString(module)

            compressedVariant += "$metaSep$moduleSlot$fieldSep$compressedModuleVariant"
        }

        var requiredMods = ""//For the user to see
        var addedModDetails = ""//For the computer to see

        if (includeModInfo) {
            val addedModIds = mutableSetOf<Triple<String, String, String>>()
            getSourceModsFromVariant(addedModIds, data)

            if (addedModIds.isNotEmpty()) {

                requiredMods = "Mods Used: "

                for (mod in addedModIds) {
                    addedModDetails += "${mod.first}$sep${mod.second}$sep${mod.third}$fieldSep"
                    requiredMods += "(${mod.second}) $sep "
                }
                requiredMods = requiredMods.dropLast(3)
                addedModDetails = addedModDetails.dropLast(1)
            }
        }

        compressedVariant = "$addedModDetails$fieldSep$compressedVariant"

        if (compressString)
            compressedVariant = CompressionUtil.compressString(compressedVariant)

        compressedVariant = "$ver$compressedVariant"//Indicate structure version for compatibility with future compressed format changes

        if (includePrepend)
            compressedVariant = "${data.displayName} ${VariantLib.getHullSpec(data.hullId)?.hullName} : $requiredMods" + compressedVariant//Prepend for the user to see. Should be ignored by the computer

        return compressedVariant
    }

    private fun saveModuleVariantToCompString(
        data: DataVariant.ParsedVariantData
    ): String {

        val parts = mutableListOf<String>()

        // Weapon groups (mode;autofire;slot+id,slot+id - ...)
        val weaponGroupStrings = data.weaponGroups.map { group ->
            val mode = group.mode.ordinal // 0 = LINKED, 1 = ALTERNATING
            val autofire = group.autofire.toString().toBinary
            val weapons = group.weapons.mapNotNull { (slotId, weaponId) ->
                "$slotId$sep$weaponId"
            }
            "$mode$sep$autofire$sep${weapons.joinToString(sep)}"
        }
        val weaponGroupString = weaponGroupStrings.joinToString(weaponGroupSep)

        // Join everything
        parts += data.hullId
        parts += data.variantId
        parts += data.displayName
        parts += data.fluxCapacitors.toString()
        parts += data.fluxVents.toString()
        parts += weaponGroupString
        parts += data.wings.joinToString(sep)
        parts += data.hullMods.joinToString(sep)
        parts += data.sMods.joinToString(sep)
        parts += data.sModdedBuiltIns.joinToString(sep)
        parts += data.permaMods.joinToString(sep)
        parts += data.tags.joinToString(sep)

        return parts.joinToString(fieldSep)
    }
}