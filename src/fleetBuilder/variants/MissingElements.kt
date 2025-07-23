package fleetBuilder.variants

import com.fs.starfarer.api.Global

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

    fun getMissingElementsString(doNotPrintEnabledMods: Boolean = false): String {
        if (!this.hasMissing()) return ""

        val missingMessages = mutableListOf<String>()

        fun <T> printIfNotEmpty(label: String, items: Collection<T>, formatItem: (T) -> String = { it.toString() }) {
            if (items.isNotEmpty()) {
                val header = "$label (${items.size}):"
                val body = items.joinToString("\n") { "  - ${formatItem(it)}" }
                missingMessages.add("$header\n$body")
            }
        }

        printIfNotEmpty("Missing Weapons", weaponIds)
        printIfNotEmpty("Missing Wings", wingIds)
        printIfNotEmpty("Missing Hullmods", hullModIds)
        printIfNotEmpty("Missing Hulls", hullIds)
        printIfNotEmpty("Missing Items", itemIds)
        printIfNotEmpty("Missing Skills", skillIds)

        val filteredMods: Set<Triple<String, String, String>> = if (doNotPrintEnabledMods) {
            val modManager = Global.getSettings().modManager
            gameMods.filterNot { (id, _, _) ->
                modManager.isModEnabled(id)
            }.toSet()
        } else {
            gameMods.toSet()
        }

        printIfNotEmpty("Mods Saved With", filteredMods) { mod ->
            val (id, name, version) = mod
            "$name ($id, v$version)"
        }

        return missingMessages.joinToString("\n\n")
    }
}