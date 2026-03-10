package fleetBuilder.serialization

import fleetBuilder.serialization.fleet.JSONFleet.extractFleetDataFromJson
import fleetBuilder.serialization.member.JSONMember.extractMemberDataFromJson
import fleetBuilder.serialization.person.CompressedPerson
import fleetBuilder.serialization.person.CompressedPerson.isCompressedPerson
import fleetBuilder.serialization.person.JSONPerson.extractPersonDataFromJson
import fleetBuilder.serialization.variant.CompressedVariant
import fleetBuilder.serialization.variant.CompressedVariant.isCompressedVariant
import fleetBuilder.serialization.variant.JSONVariant.extractVariantDataFromJson
import fleetBuilder.util.isJSON
import org.json.JSONObject

object SerializationUtils {
    const val sep = ","
    const val fieldSep = "%"//Only two ascii characters that cannot be in a variant display name
    const val metaSep = "$"//Only two ascii characters that cannot be in a variant display name
    const val joinSep = ">"

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

    fun extractDataFromAny(value: Any): Any? {
        if (value is String)
            return extractDataFromString(value)
        else if (value is JSONObject)
            return extractDataFromJSON(value)

        return null
    }

    fun extractDataFromString(text: String): Any? {
        if (text.isEmpty()) return null
        if (text.isJSON()) {
            val json = getJSONFromStringSafe(text)
            return if (json != null)
                extractDataFromJSON(json)
            else
                null
        }

        return when {
            isCompressedVariant(text) ->
                CompressedVariant.extractVariantDataFromCompString(text)
            isCompressedPerson(text) ->
                CompressedPerson.extractPersonDataFromCompString(text)

            else -> null
        }
    }

    fun extractDataFromJSON(json: JSONObject): Any? {
        if (json.length() == 0) return null

        return when {
            json.has("skills") || json.has("first") -> {
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

            else -> null
        }
    }
}