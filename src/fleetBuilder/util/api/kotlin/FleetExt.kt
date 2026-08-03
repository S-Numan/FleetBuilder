package fleetBuilder.util.api.kotlin

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
