package fleetBuilder.persistence.member

import fleetBuilder.persistence.person.PersonSettings
import fleetBuilder.persistence.variant.VariantSettings

/**
 * Settings for saving and loading FleetMemberAPI.
 *
 * @param includeOfficer Whether to include the officer when saving the member to JSON.
 * @param includeCR Whether to include the CR when saving the member to JSON.
 * @param personSettings The settings for the person serialization used when saving the member's officer.
 * @param variantSettings The settings for variant serialization used when saving the member's variant.
 */
data class MemberSettings(
    var includeOfficer: Boolean = true,
    var includeCR: Boolean = true,
    var personSettings: PersonSettings = PersonSettings(),
    var variantSettings: VariantSettings = VariantSettings(),
)
