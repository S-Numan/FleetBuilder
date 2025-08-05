package fleetBuilder.persistence.member

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import fleetBuilder.persistence.member.MemberSerialization.getMemberFromJson
import fleetBuilder.persistence.member.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.person.PersonSerialization
import fleetBuilder.persistence.variant.VariantSerialization
import fleetBuilder.util.FBMisc
import fleetBuilder.variants.GameModInfo
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.optFloat

object MemberSerialization {

    /**
     * Settings for [saveMemberToJson] and [getMemberFromJson].
     *
     * @param includeOfficer Whether to include the officer when saving the member to JSON.
     * @param includeCR Whether to include the CR when saving the member to JSON.
     * @param personSettings The settings for [PersonSerialization] used when saving the officer.
     * @param variantSettings The settings for [VariantSerialization] used when saving the variant.
     */
    data class MemberSettings(
        var includeOfficer: Boolean = true,
        var includeCR: Boolean = true,
        var personSettings: PersonSerialization.PersonSettings = PersonSerialization.PersonSettings(),
        var variantSettings: VariantSerialization.VariantSettings = VariantSerialization.VariantSettings()
    )

    data class ParsedMemberData(
        val variantData: VariantSerialization.ParsedVariantData?,
        val personData: PersonSerialization.ParsedPersonData?,
        val shipName: String,
        val cr: Float,
        val isMothballed: Boolean,
        val isFlagship: Boolean = false,
        val gameMods: Set<GameModInfo>,
    )

    fun extractMemberDataFromJson(json: JSONObject): ParsedMemberData {
        var variantJson = json.optJSONObject("variant")


        val variantData = if (variantJson != null)
            VariantSerialization.extractVariantDataFromJson(variantJson)
        else
            null

        val officerJson = json.optJSONObject("officer")
        val personData = if (officerJson != null)
            PersonSerialization.extractPersonDataFromJson(officerJson)
        else
            null

        val gameMods = FBMisc.getModInfosFromJson(json)

        return ParsedMemberData(
            variantData = variantData,
            personData = personData,
            shipName = json.optString("name", ""),
            cr = json.optFloat("cr", 0.7f),
            isMothballed = json.optBoolean("ismothballed"),
            gameMods = gameMods
        )
    }

    fun filterParsedMemberData(data: ParsedMemberData, settings: MemberSettings): ParsedMemberData {
        val personData = if (settings.includeOfficer) data.personData else null
        val variantData = if (data.variantData != null) VariantSerialization.filterParsedVariantData(data.variantData, settings.variantSettings) else null

        val cr = if (settings.includeCR) data.cr else 0.7f

        return data.copy(
            personData = personData,
            variantData = variantData,
            cr = cr
        )
    }

    fun validateAndCleanMemberData(data: ParsedMemberData, missing: MissingElements): ParsedMemberData {
        val personData = if (data.personData != null) PersonSerialization.validateAndCleanPersonData(data.personData, missing) else null

        val variantData = if (data.variantData != null) {
            VariantSerialization.validateAndCleanVariantData(data.variantData, missing)
        } else {
            missing.hullIds.add("")
            null
        }

        return data.copy(
            personData = personData,
            variantData = variantData,
            cr = data.cr.coerceIn(0f, 1f)
        )
    }

    fun buildMember(data: ParsedMemberData): FleetMemberAPI {
        val variant = if (data.variantData != null)
            VariantSerialization.buildVariant(data.variantData)
        else
            VariantLib.createErrorVariant()

        val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant)

        // Set name and CR
        member.shipName = data.shipName
        member.repairTracker.cr = data.cr
        member.repairTracker.isMothballed = data.isMothballed

        // Officer (optional)
        if (data.personData != null)
            member.captain = PersonSerialization.buildPerson(data.personData)

        return member
    }

    fun buildMemberFull(
        extracted: ParsedMemberData,
        settings: MemberSettings = MemberSettings()
    ): Pair<FleetMemberAPI, MissingElements> {
        val missing = MissingElements()

        val filtered = filterParsedMemberData(extracted, settings)
        val cleaned = validateAndCleanMemberData(filtered, missing)
        val member = buildMember(cleaned)

        return member to missing
    }

    @JvmOverloads
    fun getMemberFromJsonWithMissing(
        json: JSONObject,
        settings: MemberSettings = MemberSettings()
    ): Pair<FleetMemberAPI, MissingElements> {
        val missing = MissingElements()

        val parsed = extractMemberDataFromJson(json)
        missing.gameMods.addAll(parsed.gameMods)

        val (member, newMissing) = buildMemberFull(parsed, settings)
        missing.add(newMissing)

        return member to missing
    }

    @JvmOverloads
    fun getMemberFromJson(
        json: JSONObject,
        settings: MemberSettings = MemberSettings()
    ): FleetMemberAPI {
        return getMemberFromJsonWithMissing(json, settings).first
    }

    fun setMemberValuesFromJson(json: JSONObject, member: FleetMemberAPI) {
        val cr = json.optFloat("cr", 0.7f)
        val shipName = json.optString("name", "")
        member.repairTracker.cr = cr.coerceIn(0f, 1f) // Ensure CR is within [0, 1]
        if (shipName.isNotEmpty()) {
            member.shipName = shipName
        }
        if (json.optBoolean("ismothballed"))
            member.repairTracker.isMothballed = true
    }

    @JvmOverloads
    fun saveMemberToJson(
        member: FleetMemberAPI,
        settings: MemberSettings = MemberSettings(),
        includeModInfo: Boolean = true,
    ): JSONObject {
        val memberJson = JSONObject()
        val variantJson = VariantSerialization.saveVariantToJson(member.variant, settings.variantSettings, includeModInfo = false)
        memberJson.put("variant", variantJson)
        //memberJson.put("id", member.id)
        if (settings.includeCR) {
            memberJson.put("cr", member.repairTracker.cr)
        }

        memberJson.put("name", member.shipName)
        if (member.isMothballed)
            memberJson.put("ismothballed", true)


        if (settings.includeOfficer) {
            if (member.captain != null && !member.captain.isDefault) {
                val officerJson = PersonSerialization.savePersonToJson(member.captain, settings.personSettings)
                memberJson.put("officer", officerJson)
            }
        }

        if (includeModInfo)
            VariantSerialization.addVariantSourceModsToJson(member.variant, memberJson, settings.variantSettings)

        return memberJson
    }
}