package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI
import fleetBuilder.serialization.member.DataMember
import fleetBuilder.serialization.member.MemberSettings

/**
 * Creates an exact copy of this member.
 *
 * Note that memKey reference types will copy the reference, not the object itself.
 */
fun FleetMemberAPI.clone(filterParsed: Boolean = false): FleetMemberAPI {
    return DataMember.cloneMember(this, filterParsed = filterParsed)
}

/**
 * Creates a copy of this member with the specified settings.
 *
 * Will apply a filter pass based on the settings, enabling this to be serializable. This will remove non value types memKeys if present.
 */
fun FleetMemberAPI.clone(settings: MemberSettings): FleetMemberAPI {
    return DataMember.cloneMember(this, settings = settings)
}

/**
 * Returns the ship name without any faction prefixes.
 *
 * Not 100% reliable, as the ship does not itself store its prefix. This checks the for prefixes from every faction. Since the player can change their own faction prefix, it won't work there.
 */
fun FleetMemberAPI.getShipNameWithoutPrefix(): String {
    val fullName = shipName ?: return ""
    val knownPrefixes = buildSet {
        add("ISS") // Default

        // Add from current fleetData (if any)
        fleetData?.fleet?.faction?.shipNamePrefix?.let { if (it.isNotBlank()) add(it) }
        fleetData?.fleet?.faction?.shipNamePrefixOverride?.let { if (it.isNotBlank()) add(it) }

        // Loop through all known factions
        Global.getSector()?.allFactions?.forEach { faction ->
            faction?.shipNamePrefix?.let { if (it.isNotBlank()) add(it) }
            faction?.shipNamePrefixOverride?.let { if (it.isNotBlank()) add(it) }
        }
    }.toSet()

    val parts = fullName.trim().split("\\s+".toRegex())
    return if (parts.size > 1 && knownPrefixes.contains(parts[0])) {
        parts.drop(1).joinToString(" ")
    } else {
        fullName
    }
}