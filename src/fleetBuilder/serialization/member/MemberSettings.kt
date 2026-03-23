package fleetBuilder.serialization.member

import fleetBuilder.serialization.person.PersonSettings
import fleetBuilder.serialization.variant.VariantSettings

/**
 * Settings for saving and loading FleetMemberAPI.
 *
 * @param includeOfficer Whether to include the officer when saving/loading the member
 * @param includeCR Whether to include the CR when saving/loading the member
 * @param includeHull Whether to include the hull when saving/loading the member
 * @param personSettings The settings for the person serialization used when saving the member's officer.
 * @param variantSettings The settings for variant serialization used when saving the member's variant.
 */
data class MemberSettings(
    var includeOfficer: Boolean = true,
    var includeHull: Boolean = true,
    var includeCR: Boolean = true,
    var personSettings: PersonSettings = PersonSettings(),
    var variantSettings: VariantSettings = VariantSettings(),
)
