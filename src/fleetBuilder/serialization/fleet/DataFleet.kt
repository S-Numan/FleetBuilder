package fleetBuilder.serialization.fleet

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import fleetBuilder.core.FBConst
import fleetBuilder.serialization.MissingContent
import fleetBuilder.serialization.fleet.mods.secondInCommand.DataSecondInCommand
import fleetBuilder.serialization.member.DataMember
import fleetBuilder.serialization.member.DataMember.buildMember
import fleetBuilder.serialization.member.DataMember.filterParsedMemberData
import fleetBuilder.serialization.member.DataMember.getMemberDataFromMember
import fleetBuilder.serialization.member.DataMember.validateAndCleanMemberData
import fleetBuilder.serialization.person.DataPerson
import fleetBuilder.serialization.person.DataPerson.buildPerson
import fleetBuilder.serialization.person.DataPerson.filterParsedPersonData
import fleetBuilder.serialization.person.DataPerson.getPersonDataFromPerson
import fleetBuilder.serialization.person.DataPerson.validateAndCleanPersonData
import fleetBuilder.serialization.variant.DataVariant
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.LookupUtils.getErrorVariantHullID
import fleetBuilder.util.api.PersonUtils
import fleetBuilder.util.api.VariantUtils
import java.util.*

object DataFleet {
    data class ParsedFleetData(
        val fleetName: String? = null,
        val aggression: Int = -1,
        val factionID: String? = null,
        val commanderIfNoFlagship: DataPerson.ParsedPersonData? = null, // null if flagship exists, as commander is on flagship
        val members: List<DataMember.ParsedMemberData> = emptyList(),
        val idleOfficers: List<DataPerson.ParsedPersonData> = emptyList(),
        val secondInCommandData: DataSecondInCommand.SecondInCommandData? = null,
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

        //val hasFlagship = fleet.membersListCopy.any { it.isFlagship && !it.variant.hullMods.contains(ModSettings.commandShuttleId) }

        val data = ParsedFleetData(
            fleetName = campFleet?.name,
            aggression = campFleet?.faction?.doctrine?.aggression ?: -1,
            factionID = campFleet?.faction?.id,
            commanderIfNoFlagship = fleet.commander?.let { if (it.isDefault && it.stats.skillsCopy.count { skill -> skill.skill.isAdmiralSkill && skill.level > 0 } == 0) null else getPersonDataFromPerson(it, filterParsed = false) },
            members = fleet.membersListCopy.map { getMemberDataFromMember(it, filterParsed = false) },
            idleOfficers = fleet.officersCopy.mapNotNull { officerData ->
                val person = officerData.person
                if (!person.isDefault && person.id != fleet.commander.id && fleet.getMemberWithCaptain(person) == null) {
                    getPersonDataFromPerson(person, filterParsed = false)
                } else null
            },
            secondInCommandData = run {
                if (!Global.getSettings().modManager.isModEnabled("second_in_command") || campFleet == null) return@run null
                DataSecondInCommand.getSecondInCommandDataFromFleet(campFleet)
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
        missing: MissingContent = MissingContent()
    ): ParsedFleetData {

        var filteredCommander: DataPerson.ParsedPersonData? = null

        val filteredMembers = data.members.mapNotNull { member ->
            val variantData = member.variantData

            // Exclude by
            if (variantData?.hullId in settings.excludeMembersWithHullID ||
                member.id in settings.excludeMembersWithID ||
                variantData?.tags?.contains(FBConst.NO_COPY_TAG) == true
                || variantData?.let { LookupUtils.getHullSpec(it.hullId)?.hasTag(FBConst.NO_COPY_TAG) == true } == true
            ) return@mapNotNull null

            var processedMember = member

            // Commander handling
            if (member.isFlagship && (!settings.includeCommanderAsOfficer || !settings.memberSettings.includeOfficer)) {
                if (settings.includeCommanderSetFlagship
                    && member.personData != null
                    && !member.personData.tags.contains(FBConst.NO_COPY_TAG)
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
            else if (!settings.memberSettings.includeOfficer
                || processedMember.personData?.tags?.contains(FBConst.NO_COPY_TAG) == true
            ) {
                processedMember = processedMember.copy(personData = null)
            }
            // Remove flagship if set to be not included
            if (member.isFlagship && !settings.includeCommanderSetFlagship) {
                processedMember = processedMember.copy(isFlagship = false)
            }

            filterParsedMemberData(processedMember, settings.memberSettings, missing)
        }

        val hasFlagship = (filteredMembers.any { it.isFlagship })
        if (!settings.includeCommanderSetFlagship)
            filteredCommander = null
        else if (!hasFlagship && filteredCommander == null) // No flagship, no commander, supposed to set commander?
            filteredCommander = data.commanderIfNoFlagship?.let { filterParsedPersonData(it, settings.memberSettings.personSettings, missing) } // Set commander

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
            commanderIfNoFlagship = filteredCommander,
            idleOfficers = filteredIdleOfficers,
            aggression = filteredAggression
        )
    }

    fun validateAndCleanFleetData(
        data: ParsedFleetData,
        settings: FleetSettings = FleetSettings(),
        missing: MissingContent = MissingContent(),
        random: Random = Random()
    ): ParsedFleetData {
        val validatedMembers = data.members.mapNotNull { member ->
            val ourMissing = MissingContent()
            val validated = validateAndCleanMemberData(member, ourMissing, random)
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
            if (variantData.hullId !in LookupUtils.getHullIDSet()) {
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
            if (VariantUtils.FB_ERROR_TAG in variantData.tags) {
                if (settings.excludeMembersWithMissingHullSpec) return@mapNotNull null
            }

            missing.add(ourMissing)

            // Valid member
            validated
        }

        val validatedCommander = data.commanderIfNoFlagship?.let {
            validateAndCleanPersonData(it, missing, random)
        }

        val validatedIdleOfficers = data.idleOfficers.map {
            validateAndCleanPersonData(it, missing, random)
        }

        val validatedFaction =
            if (LookupUtils.getAllFactionIDs().contains(data.factionID)) data.factionID
            else null

        if (data.secondInCommandData != null)
            DataSecondInCommand.validateSecondInCommandData(data.secondInCommandData, missing, random)

        return data.copy(
            members = validatedMembers,
            commanderIfNoFlagship = validatedCommander,
            idleOfficers = validatedIdleOfficers,
            secondInCommandData = data.secondInCommandData,
            factionID = validatedFaction
        )
    }

    fun buildFleet(
        data: ParsedFleetData,
        fleet: FleetDataAPI,
        random: Random = Random()
    ) {
        val campFleet: CampaignFleetAPI? = fleet.fleet
        val isPlayerFleet = campFleet != null && campFleet === Global.getSector()?.playerFleet

        if (!isPlayerFleet) {
            campFleet?.name = data.fleetName

            if (data.factionID != null)
                campFleet?.setFaction(data.factionID)

            data.commanderIfNoFlagship?.let {
                campFleet?.commander = buildPerson(it, random)
            }
        }

        data.idleOfficers.forEach {
            fleet.addOfficer(buildPerson(it, random))
        }

        if (data.secondInCommandData != null && campFleet != null) {
            DataSecondInCommand.buildSecondInCommandData(data.secondInCommandData, campFleet, random)
        }

        val addedMembers = mutableMapOf<DataMember.ParsedMemberData, FleetMemberAPI>()
        data.members.forEach { parsed ->
            val member = buildMember(parsed, random)
            fleet.addFleetMember(member)
            addedMembers[parsed] = member

            if (parsed.isFlagship) {
                if (member.captain.isDefault)
                    member.captain.portraitSprite = PersonUtils.getRandomPortrait(factionID = data.factionID, random = random) // The commander of the fleet mustn't be default. May cause issues otherwise.

                campFleet?.commander = member.captain
                member.isFlagship = true

                fleet.membersListCopy.forEach { it.isFlagship = false }
            }

            member.captain?.let { officer ->
                if (officer.isAICore) return@let
                if (!officer.isDefault) {
                    fleet.addOfficer(officer)
                } else if (data.aggression > 0 && !isPlayerFleet) { // Don't do this to the playerFleet, just set the player's faction aggression doctrine manually instead.
                    // Apply doctrinal aggression to default officers
                    val personality = when (data.aggression) {
                        1 -> Personalities.CAUTIOUS
                        2 -> Personalities.STEADY
                        3 -> Personalities.AGGRESSIVE
                        4 -> if (random.nextBoolean()) Personalities.AGGRESSIVE else Personalities.RECKLESS
                        5 -> Personalities.RECKLESS
                        else -> Personalities.STEADY
                    }

                    officer.setPersonality(personality)
                }
            }
        }

        fleet.setSyncNeeded()
        fleet.syncIfNeeded()

        addedMembers.forEach { (parsed, member) ->
            // Re-run cr check if it's null to account for new stats
            if (parsed.cr == null)
                member.repairTracker.cr = member.repairTracker.maxCR
        }
    }

    @JvmOverloads
    fun buildFleetFull(
        data: ParsedFleetData,
        fleet: FleetDataAPI,
        settings: FleetSettings = FleetSettings(),
        missing: MissingContent = MissingContent(),
        random: Random = Random()
    ) {
        val ourMissing = MissingContent()

        val validated = validateAndCleanFleetData(data, settings, ourMissing, random)
        val filtered = filterParsedFleetData(validated, settings, ourMissing)
        missing.add(ourMissing)

        buildFleet(filtered, fleet, random)
    }

    @JvmOverloads
    fun createCampaignFleetFromData(
        data: ParsedFleetData,
        aiMode: Boolean,
        settings: FleetSettings = FleetSettings(),
        missing: MissingContent = MissingContent(),
        random: Random = Random()
    ): CampaignFleetAPI {
        val fleet = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, FleetTypes.TASK_FORCE, aiMode)

        buildFleetFull(data, fleet.fleetData, settings, missing, random)

        return fleet
    }
}