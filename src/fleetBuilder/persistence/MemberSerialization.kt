package fleetBuilder.persistence

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import fleetBuilder.persistence.MemberSerialization.getMemberFromJson
import fleetBuilder.persistence.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.PersonSerialization.buildPerson
import fleetBuilder.persistence.PersonSerialization.extractPersonDataFromJson
import fleetBuilder.persistence.PersonSerialization.savePersonToJson
import fleetBuilder.persistence.PersonSerialization.validateAndCleanPersonData
import fleetBuilder.persistence.VariantSerialization.addVariantSourceModsToJson
import fleetBuilder.persistence.VariantSerialization.buildVariant
import fleetBuilder.persistence.VariantSerialization.extractVariantDataFromJson
import fleetBuilder.persistence.VariantSerialization.filterParsedVariantData
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
import fleetBuilder.persistence.VariantSerialization.validateAndCleanVariantData
import fleetBuilder.util.FBMisc
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib.createErrorVariant
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
        val isFlagship: Boolean = false
    )

    fun extractMemberDataFromJson(json: JSONObject): ParsedMemberData {
        var variantJson = json.optJSONObject("variant")


        val variantData = if (variantJson != null)
            extractVariantDataFromJson(variantJson)
        else
            null

        val officerJson = json.optJSONObject("officer")
        val personData = if (officerJson != null)
            extractPersonDataFromJson(officerJson)
        else
            null

        return ParsedMemberData(
            variantData = variantData,
            personData = personData,
            shipName = json.optString("name", ""),
            cr = json.optFloat("cr", 0.7f),
            isMothballed = json.optBoolean("ismothballed")
        )
    }

    fun filterParsedMemberData(data: ParsedMemberData, settings: MemberSettings): ParsedMemberData {
        val personData = if (settings.includeOfficer) data.personData else null
        val variantData = if (data.variantData != null) filterParsedVariantData(data.variantData, settings.variantSettings) else null

        val cr = if (settings.includeCR) data.cr else 0.7f

        return data.copy(
            personData = personData,
            variantData = variantData,
            cr = cr
        )
    }

    fun validateAndCleanMemberData(data: ParsedMemberData, missing: MissingElements): ParsedMemberData {
        val personData = if (data.personData != null) validateAndCleanPersonData(data.personData, missing) else null

        val variantData = if (data.variantData != null)
            validateAndCleanVariantData(data.variantData, missing)
        else null

        return data.copy(
            personData = personData,
            variantData = variantData,
            cr = data.cr.coerceIn(0f, 1f)
        )
    }

    fun buildMember(data: ParsedMemberData): Pair<FleetMemberAPI, MissingElements> {
        val missing = MissingElements()

        val variant = if (data.variantData != null)
            buildVariant(data.variantData)
        else {
            missing.hullIds.add("")
            createErrorVariant()
        }

        val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant)

        // Set name and CR
        member.shipName = data.shipName
        member.repairTracker.cr = data.cr
        member.repairTracker.isMothballed = data.isMothballed

        // Officer (optional)
        if (data.personData != null)
            member.captain = buildPerson(data.personData)

        return member to missing
    }

    fun buildMemberFromParsed(
        extracted: ParsedMemberData,
        settings: MemberSettings
    ): Pair<FleetMemberAPI, MissingElements> {
        val filtered = filterParsedMemberData(extracted, settings)
        val cleaned = validateAndCleanMemberData(filtered, MissingElements())
        return buildMember(cleaned)
    }

    @JvmOverloads
    fun getMemberFromJsonWithMissing(
        json: JSONObject,
        settings: MemberSettings = MemberSettings()
    ): Pair<FleetMemberAPI, MissingElements> {
        val missing = MissingElements()
        FBMisc.getMissingFromModInfo(json, missing)

        val parsed = extractMemberDataFromJson(json)

        val (member, newMissing) = buildMemberFromParsed(parsed, settings)
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
        val variantJson = saveVariantToJson(member.variant, settings.variantSettings, includeModInfo = false)
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
                val officerJson = savePersonToJson(member.captain, settings.personSettings)
                memberJson.put("officer", officerJson)
            }
        }

        if (includeModInfo)
            addVariantSourceModsToJson(member.variant, memberJson, settings.variantSettings)

        return memberJson
    }
}