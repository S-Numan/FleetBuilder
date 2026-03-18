package fleetBuilder.serialization.member

import com.fs.starfarer.api.fleet.FleetMemberAPI
import fleetBuilder.core.displayMessage.DisplayMessage.showError
import fleetBuilder.serialization.GameModInfo
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.SerializationUtils.fieldSep
import fleetBuilder.serialization.SerializationUtils.memberSep
import fleetBuilder.serialization.SerializationUtils.metaSep
import fleetBuilder.serialization.SerializationUtils.sep
import fleetBuilder.serialization.member.DataMember.buildMemberFull
import fleetBuilder.serialization.member.DataMember.getMemberDataFromMember
import fleetBuilder.serialization.person.CompressedPerson
import fleetBuilder.serialization.variant.CompressedVariant
import fleetBuilder.util.FBTxt
import fleetBuilder.util.LookupUtil
import fleetBuilder.util.api.MemberUtils.getAllSourceModsFromMember
import fleetBuilder.util.lib.CompressionUtil
import fleetBuilder.util.roundToDecimals

object CompressedMember {
    fun isCompressedMember(comp: String): Boolean {
        val metaIndexStart = comp.indexOf(metaSep)
        if (metaIndexStart == -1) return false

        val metaVersion = comp.getOrNull(metaIndexStart + 1)
        return metaVersion?.equals('m', ignoreCase = true) == true
    }

    @JvmOverloads
    fun getMemberFromCompString(
        comp: String,
        settings: MemberSettings = MemberSettings(),
        missing: MissingElements = MissingElements(),
    ): FleetMemberAPI {
        val parsed = extractMemberDataFromCompString(comp, missing) ?: run {
            DataMember.ParsedMemberData()
        }

        return buildMemberFull(parsed, settings, missing)
    }

    @JvmOverloads
    fun extractMemberDataFromCompString(
        comp: String,
        missing: MissingElements = MissingElements()
    ): DataMember.ParsedMemberData? {

        val metaIndexStart = comp.indexOf(metaSep)
        val metaIndexEnd = comp.indexOf(metaSep, metaIndexStart + 1)

        if (metaIndexStart == -1 || metaIndexEnd == -1)
            return null

        val metaVersion = comp.substring(metaIndexStart + 1, metaIndexEnd)

        val fullData = when (metaVersion) {
            "m0" -> {
                val compressedData = comp.substring(metaIndexEnd + 1)
                CompressionUtil.decompressString(compressedData)
            }
            "M0" -> comp.substring(metaIndexEnd + 1)
            else -> return null
        } ?: return null

        if (fullData.isBlank())
            return null

        try {

            /* ---------- MOD INFO ---------- */

            val firstFieldSep = fullData.indexOf(fieldSep)
            if (firstFieldSep == -1)
                return null

            val modInfoBulk = fullData.substring(0, firstFieldSep)
            val parts = modInfoBulk.split(sep)

            val modInfos = parts.chunked(3).map { it.joinToString(sep) }

            val gameMods: Set<GameModInfo> =
                modInfos.mapNotNull { mod ->
                    val p = mod.split(sep)
                    if (p.size == 3) {
                        val (id, name, ver) = p
                        GameModInfo(id, name, ver)
                    } else null
                }.toSet()

            missing.gameMods.addAll(gameMods)

            /* ---------- MEMBER DATA ---------- */

            var cursor = firstFieldSep + 1

            /* ---------- VARIANT ---------- */

            val variantEnd = fullData.indexOf(memberSep, cursor)
            if (variantEnd == -1) return null

            val variantString =
                fullData.substring(cursor, variantEnd).ifBlank { null }

            cursor = variantEnd + memberSep.length

            /* ---------- PERSON ---------- */

            val personEnd = fullData.indexOf(memberSep, cursor)
            if (personEnd == -1) return null

            val personString =
                fullData.substring(cursor, personEnd).ifBlank { null }

            cursor = personEnd + memberSep.length

            /* ---------- REMAINING FIELDS ---------- */

            val remaining = fullData.substring(cursor)
            val fields = remaining.split(fieldSep)

            val shipName = fields.getOrNull(0) ?: ""
            val cr = fields.getOrNull(1)?.toFloatOrNull()
            val hullFraction = fields.getOrNull(2)?.toFloatOrNull()
            val mothballed = fields.getOrNull(3)?.toBoolean() ?: false
            val flagship = fields.getOrNull(4)?.toBoolean() ?: false
            val id = fields.getOrNull(5)?.ifBlank { null }

            val variantData =
                variantString?.let {
                    CompressedVariant.extractVariantDataFromCompString(it, missing)
                }

            val personData =
                personString?.let {
                    CompressedPerson.extractPersonDataFromCompString(it, missing)
                }

            return DataMember.ParsedMemberData(
                variantData = variantData,
                personData = personData,
                shipName = shipName,
                cr = cr,
                hullFraction = hullFraction,
                isMothballed = mothballed,
                isFlagship = flagship,
                id = id
            )

        } catch (e: Exception) {
            showError("Error parsing member data", e)
            return null
        }
    }

    @JvmOverloads
    fun saveMemberToCompString(
        member: FleetMemberAPI,
        settings: MemberSettings = MemberSettings(),
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true,
        compress: Boolean = true
    ): String {
        return saveMemberToCompString(getMemberDataFromMember(member, settings), includePrepend = includePrepend, includeModInfo = includeModInfo, compress = compress)
    }

    @JvmOverloads
    fun saveMemberToCompString(
        data: DataMember.ParsedMemberData,
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true,
        compress: Boolean = true
    ): String {

        val structureVersion =
            if (compress) "m0"
            else "M0"

        val ver = "$metaSep$structureVersion$metaSep"

        /* ---------- VARIANT / PERSON ---------- */

        val variantString =
            data.variantData?.let {
                CompressedVariant.saveVariantToCompString(
                    it,
                    includePrepend = false,
                    includeModInfo = false,
                    compress = false
                )
            } ?: ""

        val personString =
            data.personData?.let {
                CompressedPerson.savePersonToCompString(
                    it,
                    includePrepend = false,
                    includeModInfo = false,
                    compress = false
                )
            } ?: ""

        val parts = mutableListOf<String>()

        parts += data.shipName
        parts += (data.cr?.roundToDecimals(2)?.toString() ?: "")
        parts += (data.hullFraction?.roundToDecimals(2)?.toString() ?: "")
        parts += data.isMothballed.toString()
        parts += data.isFlagship.toString()
        parts += (data.id ?: "")

        var memberString =
            variantString + memberSep +
                    personString + memberSep +
                    parts.joinToString(fieldSep)

        /* ---------- MOD INFO ---------- */

        var addedModDetails = ""
        var requiredMods = ""

        if (includeModInfo && data.variantData != null) {
            val mods = getAllSourceModsFromMember(data)

            if (mods.isNotEmpty()) {
                requiredMods = FBTxt.txt("mods_used_prefix")

                for (mod in mods) {
                    addedModDetails +=
                        "${mod.id}$sep${mod.name}$sep${mod.version}$sep"

                    requiredMods += "(${mod.name}) $sep "
                }

                requiredMods = requiredMods.dropLast(3)
                addedModDetails = addedModDetails.dropLast(1)
            }
        }

        memberString = "$addedModDetails$fieldSep$memberString"

        if (compress)
            memberString = CompressionUtil.compressString(memberString)

        memberString = "$ver$memberString"

        if (includePrepend) {
            val shipName = data.shipName
            val displayName = data.variantData?.displayName ?: "null"
            val hullName = LookupUtil.getHullSpec(data.variantData?.hullId ?: "")?.hullName ?: "null"

            val readable = if (data.personData != null) {
                val personName = data.personData.first + " " + data.personData.last
                FBTxt.txt(
                    "shipReadableWithPerson",
                    shipName,
                    personName,
                    displayName,
                    hullName,
                    requiredMods
                )
            } else {
                FBTxt.txt(
                    "shipReadableNoPerson",
                    shipName,
                    displayName,
                    hullName,
                    requiredMods
                )
            }
            memberString = readable + memberString
        }

        return memberString
    }
}