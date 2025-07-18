package fleetBuilder.variants

data class MissingElements(
    val weaponIds: MutableSet<String> = mutableSetOf(),
    val wingIds: MutableSet<String> = mutableSetOf(),
    val hullModIds: MutableSet<String> = mutableSetOf(),
    val hullIds: MutableSet<String> = mutableSetOf(),
    val skillIds: MutableSet<String> = mutableSetOf(),
    val itemIds: MutableSet<String> = mutableSetOf(),
    val gameMods: MutableSet<Triple<String, String, String>> = mutableSetOf()//ID, name, version
) {
    // Function to check if anything was missing from the variant.
    fun hasMissing(): Boolean {
        return weaponIds.isNotEmpty() || wingIds.isNotEmpty() || hullModIds.isNotEmpty() || hullIds.isNotEmpty() || skillIds.isNotEmpty() || itemIds.isNotEmpty()
    }

    fun add(other: MissingElements) {
        weaponIds.addAll(other.weaponIds)
        wingIds.addAll(other.wingIds)
        hullModIds.addAll(other.hullModIds)
        hullIds.addAll(other.hullIds)
        gameMods.addAll(other.gameMods)
        skillIds.addAll(other.skillIds)
        itemIds.addAll(other.itemIds)
    }
}