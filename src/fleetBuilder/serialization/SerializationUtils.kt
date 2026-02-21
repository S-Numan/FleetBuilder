package fleetBuilder.serialization

import fleetBuilder.serialization.fleet.JSONFleet.extractFleetDataFromJson
import fleetBuilder.serialization.member.JSONMember.extractMemberDataFromJson
import fleetBuilder.serialization.person.JSONPerson.extractPersonDataFromJson
import fleetBuilder.serialization.variant.CompressedVariant.extractVariantDataFromCompString
import fleetBuilder.serialization.variant.JSONVariant.extractVariantDataFromJson
import fleetBuilder.util.startsWithJsonBracket
import org.json.JSONObject

object SerializationUtils {
    const val sep = ","
    const val fieldSep = "%"//Only two ascii characters that cannot be in a variant display name
    const val metaSep = "$"//Only two ascii characters that cannot be in a variant display name
    const val weaponGroupSep = ">"

    fun cleanJsonStringInput(raw: String): String {
        return raw.lines()
            .map { line ->
                var inQuotes = false
                val sb = StringBuilder()

                var i = 0
                while (i < line.length) {
                    val c = line[i]

                    if (c == '"') {
                        // Check for escaped quote
                        val escaped = i > 0 && line[i - 1] == '\\'
                        if (!escaped) inQuotes = !inQuotes
                    }

                    // If we hit a # and we're not in quotes, stop processing this line
                    if (c == '#' && !inQuotes) break

                    sb.append(c)
                    i++
                }

                sb.toString().trimEnd()
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun getJSONFromStringSafe(inputText: String): JSONObject? {
        var text = inputText
        text = cleanJsonStringInput(text)

        if (text.isEmpty()) return null

        return try {
            JSONObject(text)
        } catch (_: Exception) {
            null
        }
    }

    fun extractDataFromString(text: String): Any? {
        if (text.isEmpty()) return null

        if (text.startsWithJsonBracket()) {
            val json = getJSONFromStringSafe(text) ?: return null

            return when {
                json.has("skills") -> {
                    // Officer
                    extractPersonDataFromJson(json)
                }

                json.has("variant") || json.has("officer") -> {
                    // Fleet member
                    extractMemberDataFromJson(json)
                }

                json.has("hullId") -> {
                    // Variant
                    extractVariantDataFromJson(json)
                }

                json.has("members") -> {
                    // Fleet
                    extractFleetDataFromJson(json)
                }

                else -> {
                    null
                }
            }


        } else {
            val data = extractVariantDataFromCompString(text)
            if (data != null) {
                return data
            }
        }

        return null
    }
}