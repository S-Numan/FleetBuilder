package fleetBuilder.variants

import com.fs.starfarer.api.Global

open class MissingElements(
) {
    val weaponIds: MutableSet<String> = mutableSetOf()
    val wingIds: MutableSet<String> = mutableSetOf()
    val hullIds: MutableSet<String> = mutableSetOf()
    val hullModIds: MutableSet<String> = mutableSetOf()
    val skillIds: MutableSet<String> = mutableSetOf()
    val gameMods: MutableSet<Triple<String, String, String>> = mutableSetOf()//ID, name, version

    // Function to check if anything was missing from the variant.
    open fun hasMissing(): Boolean {
        return weaponIds.isNotEmpty() || wingIds.isNotEmpty() || hullModIds.isNotEmpty() || hullIds.isNotEmpty() || skillIds.isNotEmpty()
    }

    open fun add(other: MissingElements) {
        weaponIds.addAll(other.weaponIds)
        wingIds.addAll(other.wingIds)
        hullIds.addAll(other.hullIds)
        hullModIds.addAll(other.hullModIds)
        gameMods.addAll(other.gameMods)
        skillIds.addAll(other.skillIds)
    }

    protected fun <T> printIfNotEmpty(
        label: String,
        missingMessages: MutableList<String>,
        items: Collection<T>,
        formatItem: (T) -> String = { it.toString() }
    ) {
        if (items.isNotEmpty()) {
            val header = "$label (${items.size}):"
            val body = items.joinToString("\n") { "  - ${formatItem(it)}" }
            missingMessages.add("$header\n$body")
        }
    }

    open fun getMissingElementsString(doNotPrintEnabledMods: Boolean = false): String {
        if (!this.hasMissing()) return ""

        val missingMessages = mutableListOf<String>()

        printIfNotEmpty("Missing Weapons", missingMessages, weaponIds)
        printIfNotEmpty("Missing Wings", missingMessages, wingIds)
        printIfNotEmpty("Missing Hullmods", missingMessages, hullModIds)
        printIfNotEmpty("Missing Hulls", missingMessages, hullIds)

        printIfNotEmpty("Missing Skills", missingMessages, skillIds)

        val filteredMods: Set<Triple<String, String, String>> = if (doNotPrintEnabledMods) {
            val modManager = Global.getSettings().modManager
            gameMods.filterNot { (id, _, _) ->
                modManager.isModEnabled(id)
            }.toSet()
        } else {
            gameMods.toSet()
        }

        printIfNotEmpty("Mods Saved With", missingMessages, filteredMods) { mod ->
            val (id, name, version) = mod
            "$name ($id, v$version)"
        }

        return missingMessages.joinToString("\n\n")
    }
}

class MissingElementsExtended : MissingElements() {
    val blueprintWeaponIds: MutableSet<String> = mutableSetOf()
    val blueprintWingIds: MutableSet<String> = mutableSetOf()
    val blueprintHullIds: MutableSet<String> = mutableSetOf()
    val hullModIdsKnown: MutableSet<String> = mutableSetOf()
    val cargoWeaponIds: MutableSet<String> = mutableSetOf()
    val cargoWingIds: MutableSet<String> = mutableSetOf()
    val itemIds: MutableSet<String> = mutableSetOf()

    override fun hasMissing(): Boolean {
        return super.hasMissing() || hasMissingBlueprints()
    }

    private fun hasMissingBlueprints(): Boolean {
        return blueprintWeaponIds.isNotEmpty() ||
                blueprintWingIds.isNotEmpty() ||
                blueprintHullIds.isNotEmpty() ||
                hullModIdsKnown.isNotEmpty() ||
                cargoWeaponIds.isNotEmpty() ||
                cargoWingIds.isNotEmpty() ||
                itemIds.isNotEmpty()
    }

    override fun add(other: MissingElements) {
        super.add(other)
        if (other is MissingElementsExtended) {
            blueprintWeaponIds.addAll(other.blueprintWeaponIds)
            blueprintWingIds.addAll(other.blueprintWingIds)
            blueprintHullIds.addAll(other.blueprintHullIds)
            hullModIdsKnown.addAll(other.hullModIdsKnown)
            cargoWeaponIds.addAll(other.cargoWeaponIds)
            cargoWingIds.addAll(other.cargoWingIds)
            itemIds.addAll(other.itemIds)
        }
    }

    override fun getMissingElementsString(doNotPrintEnabledMods: Boolean): String {
        val base = super.getMissingElementsString(doNotPrintEnabledMods)
        val extra = mutableListOf<String>()

        printIfNotEmpty("Missing Weapon Blueprints", extra, blueprintWeaponIds)
        printIfNotEmpty("Missing Wing Blueprints", extra, blueprintWingIds)
        printIfNotEmpty("Missing Hull Blueprints", extra, blueprintHullIds)
        printIfNotEmpty("Missing Hullmods Known", extra, hullModIdsKnown)
        printIfNotEmpty("Missing Weapon in Cargo", extra, cargoWeaponIds)
        printIfNotEmpty("Missing Wing in Cargo", extra, cargoWingIds)
        printIfNotEmpty("Missing Items", extra, itemIds)

        return (extra.filter { it.isNotBlank() } + listOf(base))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }
}