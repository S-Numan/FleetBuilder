package fleetBuilder.serialization.fleet.mods.secondInCommand

import fleetBuilder.core.displayMessage.DisplayMessage.showError
import fleetBuilder.serialization.MissingContent
import fleetBuilder.serialization.SerializationUtils.fieldSep
import fleetBuilder.serialization.SerializationUtils.metaSep
import fleetBuilder.serialization.SerializationUtils.sep
import fleetBuilder.serialization.person.CompressedPerson
import fleetBuilder.util.lib.CompressionUtil
import fleetBuilder.util.api.kotlin.roundToDecimals

object CompressedSecondInCommand {

    fun isCompressedSecondInCommand(comp: String): Boolean {
        val metaIndexStart = comp.indexOf(metaSep)
        if (metaIndexStart == -1) return false

        val metaVersion = comp.getOrNull(metaIndexStart + 1)
        return metaVersion?.equals('s', ignoreCase = true) == true
    }

    @JvmOverloads
    fun extractSecondInCommandFromCompString(
        comp: String,
        missing: MissingContent = MissingContent()
    ): DataSecondInCommand.SecondInCommandData? {

        val metaStart = comp.indexOf(metaSep)
        val metaEnd = comp.indexOf(metaSep, metaStart + 1)

        if (metaStart == -1 || metaEnd == -1)
            return null

        val version = comp.substring(metaStart + 1, metaEnd)

        val fullData = when (version) {
            "s0" -> CompressionUtil.base64Inflate(comp.substring(metaEnd + 1))
            "S0" -> comp.substring(metaEnd + 1)
            else -> return null
        } ?: return null

        if (fullData.isBlank())
            return DataSecondInCommand.SecondInCommandData(mutableListOf())

        val officers = mutableListOf<DataSecondInCommand.SecondInCommandOfficerData>()

        val officerStrings = fullData.split("$$").filter { it.isNotBlank() }

        for (officerStr in officerStrings) {
            try {
                val personString = officerStr.substringBefore(sep + metaSep + fieldSep)
                if (personString.isEmpty())
                    continue
                val theRest = officerStr.substringAfter(sep + metaSep + fieldSep)
                if (theRest.isEmpty())
                    continue

                val fields = theRest.split(fieldSep)

                val level = fields.getOrNull(0)?.toIntOrNull() ?: 1
                val aptitudeId = fields.getOrNull(1) ?: continue
                val skillPoints = fields.getOrNull(2)?.toIntOrNull() ?: 0
                val experiencePoints = fields.getOrNull(3)?.toFloatOrNull() ?: 0f

                val skillIDs =
                    fields.getOrNull(4)
                        ?.takeIf { it.isNotBlank() }
                        ?.split(sep)
                        ?: emptyList()

                val assignedSlot =
                    fields.getOrNull(5)?.toIntOrNull()

                val person =
                    CompressedPerson.extractPersonDataFromCompString(
                        personString,
                        missing
                    ) ?: continue

                officers += DataSecondInCommand.SecondInCommandOfficerData(
                    person = person,
                    aptitudeId = aptitudeId,
                    skillPoints = skillPoints,
                    experiencePoints = experiencePoints,
                    activeSkillIDs = skillIDs,
                    assignedSlot = assignedSlot,
                    level = level
                )

            } catch (e: Exception) {
                showError("Error parsing second in command officer", e)
            }
        }

        return DataSecondInCommand.SecondInCommandData(officers)
    }

    @JvmOverloads
    fun saveSecondInCommandToCompString(
        data: DataSecondInCommand.SecondInCommandData,
        includePrepend: Boolean = false,
        compress: Boolean = true
    ): String {

        val structureVersion =
            if (compress) "s0"
            else "S0"

        val ver = "$metaSep$structureVersion$metaSep"

        val officersBlock = buildString {
            data.officers.forEach { officer ->
                val personString =
                    CompressedPerson.savePersonToCompString(
                        officer.person,
                        includePrepend = false,
                        includeModInfo = false,
                        compress = false
                    ) + sep + metaSep

                val parts = mutableListOf<String>()

                parts += personString
                parts += officer.level.toString()
                parts += officer.aptitudeId
                parts += officer.skillPoints.toString()
                parts += officer.experiencePoints.roundToDecimals(2).toString()
                parts += officer.activeSkillIDs.joinToString(sep)
                parts += (officer.assignedSlot?.toString() ?: "")

                append(parts.joinToString(fieldSep))
                append("$$")
            }
        }

        var result = officersBlock

        if (compress)
            result = CompressionUtil.base64Deflate(result)

        result = "$ver$result"

        if (includePrepend) {
            val readable = "Second In Command : ${data.officers.size} officers"
            result = readable + result
        }

        return result
    }
}