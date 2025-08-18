package fleetBuilder.persistence.fleet

import fleetBuilder.persistence.member.MemberSettings

/**
 * Settings for saving and loading CampaignFleetAPI and FleetDataAPI
 * @param includeCommanderSetFlagship whether to set the PersonAPI commander as the fleet's commander.
 *
 * If the commander is assigned as a captain of a ship in the fleet, that ship will be set as the flagship.
 * @param includeCommanderAsOfficer whether to include the PersonAPI commander as an officer of the fleet, provided they are one.
 * @param includeIdleOfficers whether to include officers that are not assigned to a ship in the fleet.
 * @param includeAggression whether to include the aggression of the fleet in the JSON.
 *
 * On save: store the fleet aggression doctrine as "aggression_doctrine" in the JSON
 *
 * On load:
 * Provided the fleet is **not** the player's fleet, every default officer will have their personality assigned to the corresponding aggression based on the input "aggression_doctrine" like in the campaign.
 *
 * If this is the player's fleet, nothing happens. If you want something to happen, please set the aggression doctrine of the player's faction yourself.
 *
 * @param excludeMembersWithMissingHullSpec whether to exclude fleet members with missing hull specs from the fleet.
 * If true, no "error variants" will be added to the fleet.
 * @param excludeMembersWithID a set of fleet member IDs to exclude from the fleet. If a FleetMemberAPI.id is equal to this, it will be excluded.
 * @param excludeMembersWithHullID a set of hull IDs to exclude from the fleet. If a member has a hull with this ID, it will be excluded.
 * @param memberSettings a set of settings for loading the members of the fleet.
 */
data class FleetSettings(
    var includeCommanderSetFlagship: Boolean = true,
    var includeCommanderAsOfficer: Boolean = true,
    var includeIdleOfficers: Boolean = true,
    var includeAggression: Boolean = true,
    var excludeMembersWithMissingHullSpec: Boolean = false,
    var excludeMembersWithID: MutableSet<String> = mutableSetOf(),
    var excludeMembersWithHullID: MutableSet<String> = mutableSetOf(),
    var memberSettings: MemberSettings = MemberSettings(),
)