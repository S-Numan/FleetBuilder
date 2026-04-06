package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.characters.PersonAPI
import fleetBuilder.util.api.FleetUtils

// Removed to avoid confusion as this does not fully copy the fleet, only certain parts.
/*
/**
 * Creates an exact copy of this fleet.
 *
 * Note that memKey reference types will copy the reference, not the object itself.
 */
fun CampaignFleetAPI.clone(aiMode: Boolean = true, filterParsed: Boolean = false): CampaignFleetAPI {
    return DataFleet.cloneFleet(this, aiMode = aiMode, filterParsed = filterParsed)
}

/**
 * Creates a copy of this fleet with the specified settings.
 *
 * Will apply a filter pass based on the settings, enabling this to be serializable. This will remove non value types memKeys if present.
 */
fun CampaignFleetAPI.clone(aiMode: Boolean = true, settings: FleetSettings): CampaignFleetAPI {
    return DataFleet.cloneFleet(this, aiMode = aiMode, settings = settings)
}
*/



/**
 * Returns a list of all officers in the fleet that are not assigned to any ship.
 *
 * This function is a delegate to [FleetUtils.getUnassignedOfficers].
 */
fun FleetDataAPI.getUnassignedOfficers(): List<PersonAPI> =
    FleetUtils.getUnassignedOfficers(this)

/**
 * Returns a list of all officers in the fleet that are assigned to a ship.
 *
 * This function is a delegate to [FleetUtils.getAssignedOfficers].
 */
fun FleetDataAPI.getAssignedOfficers(): List<PersonAPI> =
    FleetUtils.getAssignedOfficers(this)

/**
 * Repairs all ships in the fleet and restores their CR to maximum
 *
 * This function is a delegate to [FleetUtils.repairAndRestoreCR].
 */
fun FleetDataAPI.repairAndRestoreCR() =
    FleetUtils.repairAndRestoreCR(this)