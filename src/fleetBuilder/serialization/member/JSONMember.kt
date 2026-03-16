package fleetBuilder.serialization.member

import com.fs.starfarer.api.fleet.FleetMemberAPI
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.member.DataMember.buildMemberFull
import fleetBuilder.serialization.person.JSONPerson.extractPersonDataFromJson
import fleetBuilder.serialization.person.JSONPerson.savePersonToJson
import fleetBuilder.serialization.variant.JSONVariant.addVariantSourceModsToJson
import fleetBuilder.serialization.variant.JSONVariant.extractVariantDataFromJson
import fleetBuilder.serialization.variant.JSONVariant.saveVariantToJson
import fleetBuilder.util.FBMisc
import fleetBuilder.util.roundToDecimals
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.optFloat

object JSONMember {
    @JvmOverloads
    fun extractMemberDataFromJson(
        json: JSONObject,
        missing: MissingElements = MissingElements()
    ): DataMember.ParsedMemberData {
        val variantJson = json.optJSONObject("variant")

        val variantData = if (variantJson != null)
            extractVariantDataFromJson(variantJson, missing)
        else
            null

        val officerJson = json.optJSONObject("officer")
        val personData = if (officerJson != null)
            extractPersonDataFromJson(officerJson, missing)
        else
            null

        val isFlagship = json.optBoolean("isFlagship", false)

        missing.gameMods.addAll(FBMisc.getModInfosFromJson(json))

        return DataMember.ParsedMemberData(
            variantData = variantData,
            personData = personData,
            shipName = json.optString("name", ""),
            cr = if (json.has("cr")) json.optFloat("cr") else null,
            hullFraction = if (json.has("hullFraction")) json.optFloat("hullFraction") else null,
            isMothballed = json.optBoolean("ismothballed", false),
            isFlagship = isFlagship
        )
    }

    fun setMemberValuesFromJson(json: JSONObject, member: FleetMemberAPI) {
        val cr = json.optFloat("cr", 0.7f)
        val hullFraction = json.optFloat("hullFraction", 1.0f)
        val shipName = json.optString("name", "")
        member.repairTracker.cr = cr.coerceIn(0f, 1f) // Ensure CR is within [0, 1]
        member.status.hullFraction = hullFraction.coerceIn(0f, 1f)
        if (shipName.isNotEmpty()) {
            member.shipName = shipName
        }
        if (json.optBoolean("ismothballed"))
            member.repairTracker.isMothballed = true
    }

    @JvmOverloads
    fun getMemberFromJson(
        json: JSONObject,
        settings: MemberSettings = MemberSettings(),
        missing: MissingElements = MissingElements()
    ): FleetMemberAPI {
        val parsed = extractMemberDataFromJson(json, missing)

        return buildMemberFull(parsed, settings, missing)
    }

    @JvmOverloads
    fun saveMemberToJson(
        member: FleetMemberAPI,
        settings: MemberSettings = MemberSettings(),
        includeModInfo: Boolean = true,
    ): JSONObject {
        return saveMemberToJson(
            DataMember.getMemberDataFromMember(member, settings),
            includeModInfo
        )
    }

    @JvmOverloads
    fun saveMemberToJson(
        data: DataMember.ParsedMemberData,
        includeModInfo: Boolean = true,
    ): JSONObject {
        val memberJson = JSONObject()

        if (data.cr != null)
            memberJson.put("cr", data.cr.roundToDecimals(2))

        if (data.hullFraction != null && data.hullFraction != 1f)
            memberJson.put("hullFraction", data.hullFraction.roundToDecimals(2))

        memberJson.put("name", data.shipName)

        if (data.isMothballed)
            memberJson.put("ismothballed", true)

        if (data.personData != null) {
            val officerJson = savePersonToJson(data.personData)
            memberJson.put("officer", officerJson)
        }

        if (data.variantData != null) {
            memberJson.put("variant", saveVariantToJson(data.variantData, includeModInfo = false))

            if (includeModInfo)
                addVariantSourceModsToJson(data.variantData, memberJson)
        }

        return memberJson
    }
}