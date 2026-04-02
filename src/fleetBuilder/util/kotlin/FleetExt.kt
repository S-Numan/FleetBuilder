package fleetBuilder.util.kotlin

import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.characters.PersonAPI
import fleetBuilder.util.api.FleetUtils

fun FleetDataAPI.getUnassignedOfficers(): List<PersonAPI> =
    FleetUtils.getUnassignedOfficers(this)

fun FleetDataAPI.getAssignedOfficers(): List<PersonAPI> =
    FleetUtils.getAssignedOfficers(this)

/**
 * Repairs all ships in the fleet and restores their CR to maximum
 *
 * This function is a delegate to [FleetUtils.repairAndRestoreCR].
 */
fun FleetDataAPI.repairAndRestoreCR() =
    FleetUtils.repairAndRestoreCR(this)