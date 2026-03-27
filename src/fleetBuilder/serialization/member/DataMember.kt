package fleetBuilder.serialization.member

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.person.DataPerson
import fleetBuilder.serialization.person.DataPerson.buildPerson
import fleetBuilder.serialization.person.DataPerson.filterParsedPersonData
import fleetBuilder.serialization.person.DataPerson.getPersonDataFromPerson
import fleetBuilder.serialization.person.DataPerson.validateAndCleanPersonData
import fleetBuilder.serialization.variant.DataVariant
import fleetBuilder.serialization.variant.DataVariant.buildVariant
import fleetBuilder.serialization.variant.DataVariant.filterParsedVariantData
import fleetBuilder.serialization.variant.DataVariant.getVariantDataFromVariant
import fleetBuilder.serialization.variant.DataVariant.validateAndCleanVariantData
import fleetBuilder.util.api.VariantUtils

object DataMember {
    data class ParsedMemberData(
        val variantData: DataVariant.ParsedVariantData? = null,
        val personData: DataPerson.ParsedPersonData? = null,
        val shipName: String = "",
        val cr: Float? = 0.7f,
        val hullFraction: Float? = 1f,
        val isMothballed: Boolean = false,
        val isFlagship: Boolean = false,
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
            shipName = member.shipName ?: "",
            cr = member.repairTracker.cr,
            hullFraction = member.status.hullFraction,
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

        return data.copy(
            personData = personData,
            variantData = variantData,
            cr = if (settings.includeCR) data.cr else null,
            hullFraction = if (settings.includeHull) data.hullFraction else null,
            id = if (settings.applyID) data.id else null
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
            cr = data.cr?.coerceIn(0f, 1f),
            hullFraction = data.hullFraction?.coerceIn(0f, 1f)
        )
    }

    fun buildMember(data: ParsedMemberData): FleetMemberAPI {
        // Variant
        val variant = if (data.variantData != null)
            buildVariant(data.variantData)
        else
            VariantUtils.createErrorVariant()

        val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant)
        if (data.id != null)
            member.id = data.id

        // Officer
        if (data.personData != null)
            member.captain = buildPerson(data.personData)

        // Set name and CR
        member.shipName = data.shipName
        member.repairTracker.isMothballed = data.isMothballed
        if (data.cr != null)
            member.repairTracker.cr = data.cr
        else
            member.repairTracker.cr = member.repairTracker.maxCR

        if (data.hullFraction != null && data.hullFraction < 1f) {
            member.status.disable()
            member.status.repairDisabledABit()
            member.status.repairFraction(data.hullFraction);
            member.status.hullFraction = data.hullFraction
            /*for (i in 1..<member.status.numStatuses) {
                val rand = Math.random()
                if (rand < 0.5f && rand > data.hullFraction) {
                    member.status.setDetached(i, true)
                } else {
                    member.status.setDetached(i, false)
                    member.status.setHullFraction(i, data.hullFraction)
                }
            }*/
        }

        member.setStatUpdateNeeded(true)

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