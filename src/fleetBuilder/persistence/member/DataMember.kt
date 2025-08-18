package fleetBuilder.persistence.member

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import fleetBuilder.persistence.person.DataPerson
import fleetBuilder.persistence.person.DataPerson.buildPerson
import fleetBuilder.persistence.person.DataPerson.filterParsedPersonData
import fleetBuilder.persistence.person.DataPerson.getPersonDataFromPerson
import fleetBuilder.persistence.person.DataPerson.validateAndCleanPersonData
import fleetBuilder.persistence.variant.DataVariant
import fleetBuilder.persistence.variant.DataVariant.buildVariant
import fleetBuilder.persistence.variant.DataVariant.filterParsedVariantData
import fleetBuilder.persistence.variant.DataVariant.getVariantDataFromVariant
import fleetBuilder.persistence.variant.DataVariant.validateAndCleanVariantData
import fleetBuilder.util.roundToDecimals
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib

object DataMember {
    data class ParsedMemberData(
        val variantData: DataVariant.ParsedVariantData?,
        val personData: DataPerson.ParsedPersonData?,
        val shipName: String,
        val cr: Float?,
        val isMothballed: Boolean,
        val isFlagship: Boolean,
        val id: String? = null
    )

    @JvmOverloads
    fun copyMember(
        member: FleetMemberAPI,
        settings: MemberSettings = MemberSettings()
    ): FleetMemberAPI {
        val data = getMemberDataFromMember(member, settings)
        return buildMember(data)
    }

    fun getMemberDataFromMember(
        member: FleetMemberAPI,
        settings: MemberSettings = MemberSettings(),
        filterParsed: Boolean = true
    ): ParsedMemberData {
        val data = ParsedMemberData(
            variantData = getVariantDataFromVariant(member.variant, filterParsed = false),
            personData = if (member.captain != null && !member.captain.isDefault && settings.includeOfficer) getPersonDataFromPerson(member.captain, filterParsed = false) else null,
            shipName = member.shipName,
            cr = member.repairTracker.cr,
            isMothballed = member.repairTracker.isMothballed,
            isFlagship = member.isFlagship,
            id = member.id
        )

        if (filterParsed)
            return filterParsedMemberData(data)
        else
            return data
    }

    @JvmOverloads
    fun filterParsedMemberData(
        data: ParsedMemberData,
        settings: MemberSettings = MemberSettings(),
        missing: MissingElements = MissingElements()
    ): ParsedMemberData {
        val personData = if (data.personData != null && settings.includeOfficer) filterParsedPersonData(data.personData, settings.personSettings, missing) else null
        val variantData = if (data.variantData != null) filterParsedVariantData(data.variantData, settings.variantSettings, missing) else null

        val cr = if (settings.includeCR) data.cr else null

        return data.copy(
            personData = personData,
            variantData = variantData,
            cr = cr
        )
    }

    @JvmOverloads
    fun validateAndCleanMemberData(
        data: ParsedMemberData,
        missing: MissingElements = MissingElements()
    ): ParsedMemberData {
        val personData = if (data.personData != null) validateAndCleanPersonData(data.personData, missing) else null

        val variantData = if (data.variantData != null) {
            validateAndCleanVariantData(data.variantData, missing)
        } else {
            missing.hullIds.add("")
            null
        }

        return data.copy(
            personData = personData,
            variantData = variantData,
            cr = data.cr?.coerceIn(0f, 1f)
        )
    }

    fun buildMember(data: ParsedMemberData): FleetMemberAPI {
        // Variant
        val variant = if (data.variantData != null)
            buildVariant(data.variantData)
        else
            VariantLib.createErrorVariant()

        val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant)

        // Set name and CR
        member.shipName = data.shipName
        member.repairTracker.isMothballed = data.isMothballed
        if (data.cr != null)
            member.repairTracker.cr = data.cr
        else
            member.repairTracker.cr = member.repairTracker.maxCR


        // Officer
        if (data.personData != null)
            member.captain = buildPerson(data.personData)

        return member
    }

    fun buildMemberFull(
        data: ParsedMemberData,
        settings: MemberSettings = MemberSettings(),
        missing: MissingElements = MissingElements()
    ): FleetMemberAPI {
        val ourMissing = MissingElements()

        val cleaned = validateAndCleanMemberData(data, ourMissing)
        val filtered = filterParsedMemberData(cleaned, settings, ourMissing)

        missing.add(ourMissing)
        return buildMember(filtered)
    }
}