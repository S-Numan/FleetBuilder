package fleetBuilder.serialization.person

/**
 * Holds settings for saving and loading PersonAPI,
 * @param handleXpAndPoints whether to save and load XP and skill points. If false, these will be ignored.
 * @param excludeSkillsWithID a set of skill IDs to exclude when loading skills.
 * @param excludePeopleMemoryKeys whether to exclude memory keys that are not relevant to officers when loading.
 * @param handleRankAndPost whether to save and load rank and post. If false, these will be ignored.
 */
data class PersonSettings(
    var handleXpAndPoints: Boolean = true,
    var excludeSkillsWithID: MutableSet<String> = mutableSetOf(),
    var excludePeopleMemoryKeys: Boolean = true,
    var handleRankAndPost: Boolean = true,
)