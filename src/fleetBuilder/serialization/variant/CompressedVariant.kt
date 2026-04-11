package fleetBuilder.serialization.variant

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.loading.WeaponGroupType
import fleetBuilder.core.displayMessage.DisplayMessage.showError
import fleetBuilder.serialization.GameModInfo
import fleetBuilder.serialization.MissingContent
import fleetBuilder.serialization.SerializationUtils.fieldSep
import fleetBuilder.serialization.SerializationUtils.joinSep
import fleetBuilder.serialization.SerializationUtils.metaSep
import fleetBuilder.serialization.SerializationUtils.sep
import fleetBuilder.serialization.variant.DataVariant.buildVariantFull
import fleetBuilder.serialization.variant.DataVariant.getVariantDataFromVariant
import fleetBuilder.core.FBTxt
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.lib.CompressionUtil
import fleetBuilder.util.api.kotlin.toBinary

object CompressedVariant {
    fun isCompressedVariant(comp: String): Boolean {
        val metaIndexStart = comp.indexOf(metaSep)
        if (metaIndexStart == -1) return false

        val metaVersion = comp.getOrNull(metaIndexStart + 1)
        return metaVersion?.equals('v', ignoreCase = true) == true
    }

    @JvmOverloads
    fun getVariantFromCompString(
        comp: String,
        settings: VariantSettings = VariantSettings(),
        missing: MissingContent = MissingContent(),
    ): ShipVariantAPI {
        val parsed = extractVariantDataFromCompString(comp, missing) ?: run {
            DataVariant.ParsedVariantData("")
        }

        return buildVariantFull(parsed, settings, missing)
    }

    @JvmOverloads
    fun extractVariantDataFromCompString(
        comp: String,
        missing: MissingContent = MissingContent()
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

            fullData = CompressionUtil.base64Inflate(compressedData)
            if (fullData.isNullOrBlank()) {
                showError("Error decompressing variant data", "Error decompressing variant data\n$compressedData")
                return null
            }
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

        // Split by separator first
        val parts = modInfoBulk.split(sep)

        // Group every 3 parts together and join back with separator
        val modInfos = parts.chunked(3).map { it.joinToString(sep) }

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
            weaponGroupString.split(joinSep).map { group ->
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
    fun saveVariantToCompString(
        variant: ShipVariantAPI,
        settings: VariantSettings = VariantSettings(),
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true,
        compress: Boolean = true
    ): String {
        return saveVariantToCompString(getVariantDataFromVariant(variant, settings), includePrepend = includePrepend, includeModInfo = includeModInfo, compress = compress)
    }

    @JvmOverloads
    fun saveVariantToCompString(
        data: DataVariant.ParsedVariantData,
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true,
        compress: Boolean = true,
    ): String {
        val structureVersion =
            if (compress) "v0" // Variant compressed 0
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
            val addedMods = VariantUtils.getAllSourceModsFromVariant(data)

            if (addedMods.isNotEmpty()) {

                requiredMods = FBTxt.txt("mods_used_prefix")

                for (mod in addedMods) {
                    addedModDetails += "${mod.id}$sep${mod.name}$sep${mod.version}$sep"
                    requiredMods += "(${mod.name}) $sep "
                }
                requiredMods = requiredMods.dropLast(3)
                addedModDetails = addedModDetails.dropLast(1)
            }
        }

        compressedVariant = "$addedModDetails$fieldSep$compressedVariant"

        if (compress)
            compressedVariant = CompressionUtil.base64Deflate(compressedVariant)

        compressedVariant = "$ver$compressedVariant"//Indicate structure version for compatibility with future compressed format changes

        if (includePrepend)
            compressedVariant = "${data.displayName} ${LookupUtils.getHullSpec(data.hullId)?.hullName} : $requiredMods\n" + compressedVariant//Prepend for the user to see. Should be ignored by the computer

        return compressedVariant
    }

    private fun saveModuleVariantToCompString(
        data: DataVariant.ParsedVariantData
    ): String {
        val parts = mutableListOf<String>()

        // Weapon groups (mode;autofire;slot+id,slot+id - ...)
        val weaponGroupStrings = data.weaponGroups.map { group ->
            val mode = group.mode.ordinal // 0 = LINKED, 1 = ALTERNATING
            val autofire = group.autofire.toString().toBinary ?: 0
            val weapons = group.weapons.mapNotNull { (slotId, weaponId) ->
                "$slotId$sep$weaponId"
            }
            "$mode$sep$autofire$sep${weapons.joinToString(sep)}"
        }
        val weaponGroupString = weaponGroupStrings.joinToString(joinSep)

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