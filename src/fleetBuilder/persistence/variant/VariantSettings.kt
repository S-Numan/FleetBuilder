package fleetBuilder.persistence.variant

/**
 * Holds settings for saving and loading ShipVariantAPI.
 * @param applySMods If false, SMods will be converted to normal hullmods.
 * @param includeDMods If false, DMods will be excluded from the variant's hullmods.
 * @param includeHiddenMods If false, hidden mods will be excluded from the variant's hullmods.
 * @param includeTags If false, the variant's tags will not be included.
 * @param excludeWeaponsWithID A set of weapon IDs to exclude from the variant's weapons.
 * @param excludeWingsWithID A set of wing IDs to exclude from the variant's wings.
 * @param excludeHullModsWithID A set of hullmod IDs to exclude from the variant's hullmods.
 */
data class VariantSettings(
    var applySMods: Boolean = true,
    var includeDMods: Boolean = true,
    var includeHiddenMods: Boolean = true,
    var includeTags: Boolean = true,
    var excludeWeaponsWithID: MutableSet<String> = mutableSetOf(),
    var excludeWingsWithID: MutableSet<String> = mutableSetOf(),
    var excludeHullModsWithID: MutableSet<String> = mutableSetOf(),
    var excludeTagsWithID: MutableSet<String> = mutableSetOf()
)