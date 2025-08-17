package fleetBuilder.persistence.fleet

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import fleetBuilder.config.ModSettings
import fleetBuilder.persistence.fleet.SecondInCommandSerialization.buildSecondInCommandData
import fleetBuilder.persistence.fleet.SecondInCommandSerialization.extractSecondInCommandData
import fleetBuilder.persistence.fleet.SecondInCommandSerialization.getSecondInCommandDataFromFleet
import fleetBuilder.persistence.fleet.SecondInCommandSerialization.saveSecondInCommandData
import fleetBuilder.persistence.fleet.SecondInCommandSerialization.validateSecondInCommandData
import fleetBuilder.persistence.member.DataMember
import fleetBuilder.persistence.member.DataMember.buildMember
import fleetBuilder.persistence.member.DataMember.filterParsedMemberData
import fleetBuilder.persistence.member.DataMember.getMemberDataFromMember
import fleetBuilder.persistence.member.DataMember.validateAndCleanMemberData
import fleetBuilder.persistence.member.MemberSettings
import fleetBuilder.persistence.person.DataPerson
import fleetBuilder.persistence.person.DataPerson.buildPerson
import fleetBuilder.persistence.person.DataPerson.buildPersonFull
import fleetBuilder.persistence.person.DataPerson.filterParsedPersonData
import fleetBuilder.persistence.person.DataPerson.getPersonDataFromPerson
import fleetBuilder.persistence.person.DataPerson.validateAndCleanPersonData
import fleetBuilder.persistence.person.JSONPerson.savePersonToJson
import fleetBuilder.persistence.variant.DataVariant
import fleetBuilder.persistence.variant.JSONVariant.extractVariantDataFromJson
import fleetBuilder.persistence.variant.JSONVariant.saveVariantToJson
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib
import fleetBuilder.variants.VariantLib.getErrorVariantHullID
import org.json.JSONObject
import java.util.Random
import kotlin.collections.forEach

object DataFleet {
    data class ParsedFleetData(
        val fleetName: String? = null,
        val aggression: Int = -1,
        val factionID: String? = null,
        val commander: DataPerson.ParsedPersonData?, // null if flagship exists, as commander is on flagship
        val members: List<DataMember.ParsedMemberData>,
        val idleOfficers: List<DataPerson.ParsedPersonData>,
        val secondInCommandData: SecondInCommandSerialization.SecondInCommandData?,
    )

    @JvmOverloads
    fun copyFleet(
        fleet: CampaignFleetAPI,
        aiMode: Boolean,
        settings: FleetSettings = FleetSettings()
    ): CampaignFleetAPI {
        val data = getFleetDataFromFleet(fleet, settings)
        return createCampaignFleetFromData(data, aiMode, settings)
    }

    @JvmOverloads
    fun getFleetDataFromFleet(
        fleet: CampaignFleetAPI,
        settings: FleetSettings = FleetSettings(),
        filterParsed: Boolean = true
    ): ParsedFleetData {
        return getFleetDataFromFleet(fleet.fleetData, settings, filterParsed)
    }

    @JvmOverloads
    fun getFleetDataFromFleet(
        fleet: FleetDataAPI,
        settings: FleetSettings = FleetSettings(),
        filterParsed: Boolean = true
    ): ParsedFleetData {
        val campFleet: CampaignFleetAPI? = fleet.fleet

        val hasFlagship = fleet.membersListCopy.any { it.isFlagship }

        val data = ParsedFleetData(
            fleetName = campFleet?.name,
            aggression = campFleet?.faction?.doctrine?.aggression ?: -1,
            factionID = campFleet?.faction?.id,
            commander = if (hasFlagship) null else fleet.commander?.let { if (it.isDefault) null else getPersonDataFromPerson(it, filterParsed = false) },
            members = fleet.membersListCopy.map { getMemberDataFromMember(it, filterParsed = false) },
            idleOfficers = fleet.officersCopy.mapNotNull { officerData ->
                val person = officerData.person
                if (!person.isDefault && person.id != fleet.commander.id && fleet.getMemberWithCaptain(person) == null) {
                    getPersonDataFromPerson(person, filterParsed = false)
                } else null
            },
            secondInCommandData = run {
                if (!Global.getSettings().modManager.isModEnabled("second_in_command") || campFleet == null) return@run null
                getSecondInCommandDataFromFleet(campFleet)
            }
        )

        return if (filterParsed)
            filterParsedFleetData(data, settings)
        else
            data
    }

    @JvmOverloads
    fun filterParsedFleetData(
        data: ParsedFleetData,
        settings: FleetSettings = FleetSettings(),
        missing: MissingElements = MissingElements()
    ): ParsedFleetData {

        var filteredCommander = if (settings.includeCommanderSetFlagship)
            data.commander?.let { filterParsedPersonData(it, settings.memberSettings.personSettings, missing) }
        else null

        val filteredMembers = data.members.mapNotNull { member ->
            val variantData = member.variantData

            // Exclude by variant or hull ID
            if (variantData?.hullId in settings.excludeMembersWithHullID ||
                variantData?.variantId in settings.excludeMembersWithID ||
                variantData?.allHullMods()?.contains(ModSettings.commandShuttleId) == true
            ) return@mapNotNull null

            var processedMember = member

            // Commander handling
            if (member.isFlagship && (!settings.includeCommanderAsOfficer || !settings.memberSettings.includeOfficer)) {
                if (filteredCommander == null &&
                    settings.includeCommanderSetFlagship &&
                    member.personData != null
                ) {
                    filteredCommander = filterParsedPersonData(
                        member.personData,
                        settings.memberSettings.personSettings,
                        missing
                    )
                }
                processedMember = processedMember.copy(personData = null, isFlagship = false)
            }
            // Officer exclusion
            else if (!settings.memberSettings.includeOfficer) {
                processedMember = processedMember.copy(personData = null)
            }
            // Remove flagship if set to be not included
            if (member.isFlagship && !settings.includeCommanderSetFlagship) {
                processedMember = processedMember.copy(isFlagship = false)
            }

            filterParsedMemberData(processedMember, settings.memberSettings, missing)
        }

        val filteredIdleOfficers = if (settings.includeIdleOfficers) {
            data.idleOfficers.map {
                filterParsedPersonData(it, settings.memberSettings.personSettings, missing)
            }
        } else emptyList()

        val filteredAggression = if (settings.includeAggression) {
            data.aggression
        } else -1

        return data.copy(
            members = filteredMembers,
            commander = filteredCommander,
            idleOfficers = filteredIdleOfficers,
            aggression = filteredAggression
        )
    }

    fun validateAndCleanFleetData(
        data: ParsedFleetData,
        settings: FleetSettings = FleetSettings(),
        missing: MissingElements = MissingElements(),
    ): ParsedFleetData {
        val validatedMembers = data.members.mapNotNull { member ->
            val ourMissing = MissingElements()
            val validated = validateAndCleanMemberData(member, ourMissing)
            val variantData = validated.variantData

            // If missing variant data: replace with error variant
            if (variantData == null) {
                missing.add(ourMissing)
                val name = "ERR:NOVAR"
                return@mapNotNull validated.copy(
                    variantData = DataVariant.ParsedVariantData(getErrorVariantHullID(), displayName = name),
                    shipName = name
                )
            }

            // If hull ID does not exist: log missing and maybe replace with error variant
            if (variantData.hullId !in VariantLib.getHullIDSet()) {
                missing.hullIds.add(variantData.hullId)

                if (settings.excludeMembersWithMissingHullSpec) return@mapNotNull null

                missing.add(ourMissing)

                val name = "ERR:NOHUL:${variantData.hullId}"
                return@mapNotNull validated.copy(
                    variantData = DataVariant.ParsedVariantData(getErrorVariantHullID(), displayName = name),
                    shipName = name
                )
            }

            // Error-tagged variant
            if (VariantLib.errorTag in variantData.tags) {
                if (settings.excludeMembersWithMissingHullSpec) return@mapNotNull null
            }

            missing.add(ourMissing)

            // Valid member
            validated
        }

        val validatedCommander = data.commander?.let {
            validateAndCleanPersonData(it, missing)
        }

        val validatedIdleOfficers = data.idleOfficers.map {
            validateAndCleanPersonData(it, missing)
        }

        val validatedFaction =
            if (Global.getSettings().allFactionSpecs.any { it.id == data.factionID }) data.factionID
            else null

        if (data.secondInCommandData != null)
            validateSecondInCommandData(data.secondInCommandData, missing)

        return data.copy(
            members = validatedMembers,
            commander = validatedCommander,
            idleOfficers = validatedIdleOfficers,
            secondInCommandData = data.secondInCommandData,
            factionID = validatedFaction
        )
    }

    fun buildFleet(data: ParsedFleetData, fleet: FleetDataAPI) {
        val campFleet: CampaignFleetAPI? = fleet.fleet
        campFleet?.name = data.fleetName

        if (data.factionID != null && campFleet !== Global.getSector().playerFleet) // Don't change player fleet faction.
            campFleet?.setFaction(data.factionID)

        data.members.forEach { parsed ->
            val member = buildMember(parsed)

            fleet.addFleetMember(member)

            if (parsed.isFlagship) {
                if (!member.captain.isDefault)
                    campFleet?.commander = member.captain

                fleet.membersListCopy.forEach { it.isFlagship = false }
                member.isFlagship = true
            }

            member.captain?.let { officer ->
                if (officer.isAICore) return@let
                if (!officer.isDefault) {
                    fleet.addOfficer(officer)
                } else if (data.aggression > 0 && campFleet !== Global.getSector().playerFleet) { // Don't do this to the playerFleet, just set the player's faction aggression doctrine manually instead.
                    // Apply doctrinal aggression to default officers
                    val personality = when (data.aggression) {
                        1 -> Personalities.CAUTIOUS
                        2 -> Personalities.STEADY
                        3 -> Personalities.AGGRESSIVE
                        4 -> if (Random().nextBoolean()) Personalities.AGGRESSIVE else Personalities.RECKLESS
                        5 -> Personalities.RECKLESS
                        else -> Personalities.STEADY
                    }

                    officer.setPersonality(personality)
                }
            }
        }

        data.commander?.let {
            campFleet?.commander = buildPerson(it)
        }

        data.idleOfficers.forEach {
            fleet.addOfficer(buildPerson(it))
        }

        fleet.syncIfNeeded()

        if (data.secondInCommandData != null && campFleet != null) {
            buildSecondInCommandData(data.secondInCommandData, campFleet)
        }
    }

    @JvmOverloads
    fun buildFleetFull(
        data: ParsedFleetData,
        fleet: FleetDataAPI,
        settings: FleetSettings = FleetSettings(),
        missing: MissingElements = MissingElements()
    ) {
        val ourMissing = MissingElements()

        val validated = validateAndCleanFleetData(data, settings, ourMissing)
        val filtered = filterParsedFleetData(validated, settings, ourMissing)
        missing.add(ourMissing)

        buildFleet(filtered, fleet)
    }

    @JvmOverloads
    fun createCampaignFleetFromData(
        data: ParsedFleetData,
        aiMode: Boolean,
        settings: FleetSettings = FleetSettings(),
        missing: MissingElements = MissingElements()
    ): CampaignFleetAPI {
        val fleet = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, FleetTypes.TASK_FORCE, aiMode)

        buildFleetFull(data, fleet.fleetData, settings, missing)

        return fleet
    }
}