package fleetBuilder.variants

//This is intended to report what wasn't available when making a variant from a Json. This is not intended to report for a malformed JsonObject
data class MissingElements(
    val weaponIds: MutableSet<String> = mutableSetOf(),
    val wingIds: MutableSet<String> = mutableSetOf(),
    val hullModIds: MutableSet<String> = mutableSetOf(),
    val hullIds: MutableSet<String> = mutableSetOf(),
    val gameMods: MutableSet<Triple<String, String, String>> = mutableSetOf()//ID, name, version
) {
    // Function to check if anything was missing from the variant.
    fun hasMissing(): Boolean {
        return weaponIds.isNotEmpty() || wingIds.isNotEmpty() || hullModIds.isNotEmpty() || hullIds.isNotEmpty()
    }

    fun add(other: MissingElements) {
        weaponIds.addAll(other.weaponIds)
        wingIds.addAll(other.wingIds)
        hullModIds.addAll(other.hullModIds)
        hullIds.addAll(other.hullIds)
        gameMods.addAll(other.gameMods)
    }
}