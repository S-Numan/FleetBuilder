package fleetBuilder.persistence

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import fleetBuilder.persistence.PersonSerialization.getPersonFromJsonWithMissing
import fleetBuilder.persistence.PersonSerialization.savePersonToJson
import fleetBuilder.persistence.VariantSerialization.addVariantSourceModsToJson
import fleetBuilder.persistence.VariantSerialization.getVariantFromJsonWithMissing
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
import fleetBuilder.util.MISC.createErrorVariant
import fleetBuilder.util.MISC.getMissingFromModInfo
import fleetBuilder.variants.MissingElements
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

    //You may want to add the officer on the fleet member to the fleet, provided there is an officer. Otherwise the logic may not behave as expected
    @JvmOverloads
    fun getMemberFromJsonWithMissing(
        json: JSONObject,
        includeOfficer: Boolean = true
    ): Pair<FleetMemberAPI, MissingElements> {
        // Extract and validate "variant" JSON
        val variantJson = json.optJSONObject("variant")

        val variant: ShipVariantAPI
        var missingElements: MissingElements
        if (variantJson != null) {
            val (deserializedVariant, _missing) = getVariantFromJsonWithMissing(variantJson)
            variant = deserializedVariant
            missingElements = _missing
        } else { //Handle when no variantJson exists. This makes a temp variant to avoid losing the officer.
            variant = createErrorVariant()
            missingElements = MissingElements()
            missingElements.hullIds.add("")
        }

        getMissingFromModInfo(json, missingElements)

        // Create the FleetMemberAPI object
        val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant)

        setMemberValuesFromJson(json, member)

        // Handle officer if included and present
        if (includeOfficer) {
            setMemberOfficerFromJson(json, member, missingElements)
        }

        return Pair(member, missingElements)
    }

    @JvmOverloads
    fun getMemberFromJson(json: JSONObject, includeOfficer: Boolean = true): FleetMemberAPI {
        return getMemberFromJsonWithMissing(json, includeOfficer).first
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

    fun setMemberOfficerFromJson(json: JSONObject, member: FleetMemberAPI, missingElements: MissingElements) {
        val officerJson = json.optJSONObject("officer")
        if (officerJson != null) {
            val (officer, personMissing) = getPersonFromJsonWithMissing(officerJson)
            missingElements.add(personMissing)

            member.captain = officer
        }
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