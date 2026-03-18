package fleetBuilder.serialization.fleet

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetDataAPI
import fleetBuilder.core.displayMessage.DisplayMessage.showError
import fleetBuilder.serialization.GameModInfo
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.SerializationUtils.fieldSep
import fleetBuilder.serialization.SerializationUtils.fleetSep1
import fleetBuilder.serialization.SerializationUtils.fleetSep2
import fleetBuilder.serialization.SerializationUtils.metaSep
import fleetBuilder.serialization.SerializationUtils.sep
import fleetBuilder.serialization.fleet.DataFleet.createCampaignFleetFromData
import fleetBuilder.serialization.fleet.DataFleet.getFleetDataFromFleet
import fleetBuilder.serialization.fleet.mods.secondInCommand.CompressedSecondInCommand
import fleetBuilder.serialization.member.CompressedMember
import fleetBuilder.serialization.member.DataMember
import fleetBuilder.serialization.person.CompressedPerson
import fleetBuilder.serialization.person.DataPerson
import fleetBuilder.util.FBTxt
import fleetBuilder.util.api.FleetUtils.getAllSourceModsFromFleet
import fleetBuilder.util.lib.CompressionUtil

object CompressedFleet {
    fun isCompressedFleet(comp: String): Boolean {
        val metaIndexStart = comp.indexOf(metaSep)
        if (metaIndexStart == -1) return false

        val metaVersion = comp.getOrNull(metaIndexStart + 1)
        return metaVersion?.equals('f', ignoreCase = true) == true
    }

    @JvmOverloads
    fun getFleetFromCompString(
        comp: String,
        aiMode: Boolean,
        settings: FleetSettings = FleetSettings(),
        missing: MissingElements = MissingElements(),
    ): CampaignFleetAPI {
        val parsed = extractFleetDataFromCompString(comp, missing) ?: run {
            DataFleet.ParsedFleetData()
        }

        return createCampaignFleetFromData(parsed, aiMode = aiMode, settings, missing)
    }

    @JvmOverloads
    fun extractFleetDataFromCompString(
        comp: String,
        missing: MissingElements = MissingElements()
    ): DataFleet.ParsedFleetData? {

        val metaIndexStart = comp.indexOf(metaSep)
        val metaIndexEnd = comp.indexOf(metaSep, metaIndexStart + 1)

        if (metaIndexStart == -1 || metaIndexEnd == -1)
            return null

        val metaVersion = comp.substring(metaIndexStart + 1, metaIndexEnd)

        val fullData = when (metaVersion) {
            "f0" -> {
                val compressedData = comp.substring(metaIndexEnd + 1)
                CompressionUtil.decompressString(compressedData)
            }
            "F0" -> comp.substring(metaIndexEnd + 1)
            else -> return null
        } ?: return null

        if (fullData.isBlank())
            return null

        try {

            val blocks = fullData.split(fleetSep2)

            if (blocks.size < 5)
                return null

            /* -------- MOD INFO -------- */

            val modInfoBulk = blocks[0]

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

            /* -------- FLEET META -------- */

            val metaParts = blocks[1].split(fieldSep)

            val fleetName =
                metaParts.getOrNull(0)?.ifBlank { null }

            val aggression =
                metaParts.getOrNull(1)?.toIntOrNull() ?: -1

            val factionID =
                metaParts.getOrNull(2)?.ifBlank { null }

            /* -------- MEMBERS -------- */

            val members = mutableListOf<DataMember.ParsedMemberData>()

            val membersBlock = blocks[2]

            if (membersBlock.isNotBlank()) {

                membersBlock.split(fleetSep1)
                    .filter { it.isNotBlank() }
                    .forEach {

                        val member =
                            CompressedMember.extractMemberDataFromCompString(
                                it,
                                missing
                            )

                        if (member != null)
                            members += member
                    }
            }

            /* -------- COMMANDER -------- */

            val commander =
                blocks[3].takeIf { it.isNotBlank() }?.let {
                    CompressedPerson.extractPersonDataFromCompString(
                        it,
                        missing
                    )
                }

            /* -------- IDLE OFFICERS -------- */

            val idleOfficers = mutableListOf<DataPerson.ParsedPersonData>()

            val officersBlock = blocks[4]

            if (officersBlock.isNotBlank()) {

                officersBlock.split(fleetSep1)
                    .filter { it.isNotBlank() }
                    .forEach {

                        val officer =
                            CompressedPerson.extractPersonDataFromCompString(
                                it,
                                missing
                            )

                        if (officer != null)
                            idleOfficers += officer
                    }
            }

            /* -------- SECOND IN COMMAND -------- */

            val sicData =
                blocks.getOrNull(5)
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        CompressedSecondInCommand.extractSecondInCommandFromCompString(
                            it,
                            missing
                        )
                    }

            return DataFleet.ParsedFleetData(
                fleetName = fleetName,
                aggression = aggression,
                factionID = factionID,
                commanderIfNoFlagship = commander,
                members = members,
                idleOfficers = idleOfficers,
                secondInCommandData = sicData
            )

        } catch (e: Exception) {
            showError("Error parsing fleet data", e)
            return null
        }
    }

    @JvmOverloads
    fun saveFleetToCompString(
        fleet: CampaignFleetAPI,
        settings: FleetSettings = FleetSettings(),
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true,
        compress: Boolean = true
    ): String = saveFleetToCompString(fleet.fleetData, settings = settings, includePrepend = includePrepend, includeModInfo = includeModInfo, compress = compress)

    @JvmOverloads
    fun saveFleetToCompString(
        fleet: FleetDataAPI,
        settings: FleetSettings = FleetSettings(),
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true,
        compress: Boolean = true
    ): String {
        return saveFleetToCompString(getFleetDataFromFleet(fleet, settings), includePrepend = includePrepend, includeModInfo = includeModInfo, compress = compress)
    }

    @JvmOverloads
    fun saveFleetToCompString(
        data: DataFleet.ParsedFleetData,
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true,
        compress: Boolean = true
    ): String {

        val structureVersion =
            if (compress) "f0"
            else "F0"

        val ver = "$metaSep$structureVersion$metaSep"

        /* -------- FLEET META -------- */

        val fleetMeta = listOf(
            data.fleetName ?: "",
            data.aggression.toString(),
            data.factionID ?: ""
        ).joinToString(fieldSep)

        /* -------- MEMBERS -------- */

        val membersBlock =
            data.members.joinToString(fleetSep1) { member ->

                CompressedMember.saveMemberToCompString(
                    member,
                    includePrepend = false,
                    includeModInfo = false,
                    compress = false
                )
            }

        /* -------- COMMANDER -------- */

        val commanderString =
            data.commanderIfNoFlagship?.let {
                CompressedPerson.savePersonToCompString(
                    it,
                    includePrepend = false,
                    includeModInfo = false,
                    compress = false
                )
            } ?: ""

        /* -------- IDLE OFFICERS -------- */

        val officersBlock =
            data.idleOfficers.joinToString(fleetSep1) {

                CompressedPerson.savePersonToCompString(
                    it,
                    includePrepend = false,
                    includeModInfo = false,
                    compress = false
                )
            }

        /* -------- SECOND IN COMMAND -------- */

        val sicString =
            data.secondInCommandData?.let {

                CompressedSecondInCommand.saveSecondInCommandToCompString(
                    it,
                    includePrepend = false,
                    compress = false
                )
            } ?: ""

        /* -------- BUILD BLOCKS -------- */

        val blocks = mutableListOf<String>()

        // placeholder for mod info (added later)
        //blocks += ""

        blocks += fleetMeta
        blocks += membersBlock
        blocks += commanderString
        blocks += officersBlock
        blocks += sicString

        var fleetString = blocks.joinToString(fleetSep2)

        /* -------- MOD INFO -------- */

        var requiredMods = ""
        var addedModDetails = ""

        if (includeModInfo) {

            val mods = getAllSourceModsFromFleet(data)

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

        fleetString =
            "$addedModDetails$fleetSep2$fleetString"

        /* -------- COMPRESSION -------- */

        if (compress)
            fleetString = CompressionUtil.compressString(fleetString)

        fleetString = "$ver$fleetString"

        /* -------- READABLE PREFIX -------- */

        if (includePrepend) {

            val fleetName = data.fleetName ?: "Fleet"
            val readable = FBTxt.txt("fleet_summary", fleetName, data.members.size, requiredMods)

            fleetString = readable + fleetString
        }

        return fleetString
    }
}