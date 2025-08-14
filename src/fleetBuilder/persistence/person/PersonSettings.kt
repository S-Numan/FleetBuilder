package fleetBuilder.persistence.person

/**
 * Holds settings for saving and loading PersonAPI,
 * @param handleXpAndPoints whether to save and load XP and skill points from the JSON. If false, these will be ignored.
 * @param excludeSkillsWithID a set of skill IDs to exclude when loading skills from the JSON. If a skill is in
 * this set, it will not be loaded.
 */
data class PersonSettings(
    var handleXpAndPoints: Boolean = true,
    var excludeSkillsWithID: MutableSet<String> = mutableSetOf(),
)