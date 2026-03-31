package fleetBuilder.serialization

import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.serialization.fleet.CompressedFleet
import fleetBuilder.serialization.fleet.CompressedFleet.isCompressedFleet
import fleetBuilder.serialization.fleet.JSONFleet.extractFleetDataFromJson
import fleetBuilder.serialization.member.CompressedMember
import fleetBuilder.serialization.member.CompressedMember.isCompressedMember
import fleetBuilder.serialization.member.JSONMember.extractMemberDataFromJson
import fleetBuilder.serialization.person.CompressedPerson
import fleetBuilder.serialization.person.CompressedPerson.isCompressedPerson
import fleetBuilder.serialization.person.JSONPerson.extractPersonDataFromJson
import fleetBuilder.serialization.variant.CompressedVariant
import fleetBuilder.serialization.variant.CompressedVariant.isCompressedVariant
import fleetBuilder.serialization.variant.JSONVariant.extractVariantDataFromJson
import fleetBuilder.util.FBTxt
import fleetBuilder.util.isJSON
import org.json.JSONObject
import java.awt.Color

object SerializationUtils {
    const val sep = ","
    const val fieldSep = "%"//Only two ascii characters that cannot be in a variant display name
    const val metaSep = "$"//Only two ascii characters that cannot be in a variant display name
    const val joinSep = ">"
    const val memberSep = "$$><$$"
    const val fleetSep1 = "$$>>$$"
    const val fleetSep2 = "$$<<$$"
    const val memKeySep = "$$sep$"
    const val memKeyJoinSep = "$$joinSep$"

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

    @JvmOverloads
    fun extractDataFromAny(value: Any, missing: MissingContent = MissingContent()): Any? {
        if (value is String)
            return extractDataFromString(value, missing)
        else if (value is JSONObject)
            return extractDataFromJSON(value, missing)

        return null
    }

    @JvmOverloads
    fun extractDataFromString(text: String, missing: MissingContent = MissingContent()): Any? {
        if (text.isEmpty()) return null
        if (text.isJSON()) {
            val json = getJSONFromStringSafe(text)
            return if (json != null)
                extractDataFromJSON(json, missing)
            else
                null
        }

        return when {
            isCompressedVariant(text) -> {
                val comp = CompressedVariant.extractVariantDataFromCompString(text, missing)
                if (comp == null)
                    DisplayMessage.showMessage(FBTxt.txt("failed_extract_variant"), Color.YELLOW)
                comp
            }
            isCompressedPerson(text) -> {
                val comp = CompressedPerson.extractPersonDataFromCompString(text, missing)
                if (comp == null)
                    DisplayMessage.showMessage(FBTxt.txt("failed_extract_person"), Color.YELLOW)
                comp
            }
            isCompressedMember(text) -> {
                val comp = CompressedMember.extractMemberDataFromCompString(text, missing)
                if (comp == null)
                    DisplayMessage.showMessage(FBTxt.txt("failed_extract_member"), Color.YELLOW)
                comp
            }
            isCompressedFleet(text) -> {
                val comp = CompressedFleet.extractFleetDataFromCompString(text, missing)
                if (comp == null)
                    DisplayMessage.showMessage(FBTxt.txt("failed_extract_fleet"), Color.YELLOW)
                comp
            }
            else -> null
        }
    }

    @JvmOverloads
    fun extractDataFromJSON(json: JSONObject, missing: MissingContent = MissingContent()): Any? {
        if (json.length() == 0) return null

        return when {
            json.has("skills") || json.has("first") -> {
                // Officer
                extractPersonDataFromJson(json, missing)
            }

            json.has("variant") || json.has("officer") -> {
                // Fleet member
                extractMemberDataFromJson(json, missing)
            }

            json.has("hullId") -> {
                // Variant
                extractVariantDataFromJson(json, missing)
            }

            json.has("members") -> {
                // Fleet
                extractFleetDataFromJson(json, missing)
            }

            else -> null
        }
    }
}