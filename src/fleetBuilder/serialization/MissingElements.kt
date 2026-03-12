package fleetBuilder.serialization

import com.fs.starfarer.api.Global
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.util.FBTxt
import org.apache.log4j.Level
import java.awt.Color

data class GameModInfo(
    val id: String,
    val name: String,
    val version: String
)

open class MissingElements(
) {
    val weaponIds: MutableSet<String> = mutableSetOf()
    val wingIds: MutableSet<String> = mutableSetOf()
    val hullIds: MutableSet<String> = mutableSetOf()
    val hullModIds: MutableSet<String> = mutableSetOf()
    val skillIds: MutableSet<String> = mutableSetOf()
    val gameMods: MutableSet<GameModInfo> = mutableSetOf()//ID, name, version

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

        printIfNotEmpty(FBTxt.txt("missing_weapons"), missingMessages, weaponIds)
        printIfNotEmpty(FBTxt.txt("missing_wings"), missingMessages, wingIds)
        printIfNotEmpty(FBTxt.txt("missing_hullmods"), missingMessages, hullModIds)
        printIfNotEmpty(FBTxt.txt("missing_hulls"), missingMessages, hullIds)

        printIfNotEmpty(FBTxt.txt("missing_skills"), missingMessages, skillIds)

        val filteredMods: Set<GameModInfo> = if (doNotPrintEnabledMods) {
            val modManager = Global.getSettings().modManager
            gameMods
                .filterNot { mod -> modManager.isModEnabled(mod.id) }
                .toSet()
        } else {
            gameMods.toSet()
        }

        printIfNotEmpty(FBTxt.txt("mods_saved_with"), missingMessages, filteredMods) { mod ->
            val (id, name, version) = mod
            FBTxt.txt("mod_entry", name, id, version)
        }

        return missingMessages.joinToString(FBTxt.txt("missing_section_separator"))
    }
}

class MissingElementsExtended : MissingElements() {
    val blueprintWeaponIds: MutableSet<String> = mutableSetOf()
    val blueprintWingIds: MutableSet<String> = mutableSetOf()
    val blueprintHullIds: MutableSet<String> = mutableSetOf()
    val blueprintIndustryIds: MutableSet<String> = mutableSetOf()
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
                blueprintIndustryIds.isNotEmpty() ||
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
            blueprintIndustryIds.addAll(other.blueprintIndustryIds)
            hullModIdsKnown.addAll(other.hullModIdsKnown)
            cargoWeaponIds.addAll(other.cargoWeaponIds)
            cargoWingIds.addAll(other.cargoWingIds)
            itemIds.addAll(other.itemIds)
        }
    }

    override fun getMissingElementsString(doNotPrintEnabledMods: Boolean): String {
        val base = super.getMissingElementsString(doNotPrintEnabledMods)
        val extra = mutableListOf<String>()

        printIfNotEmpty(FBTxt.txt("missing_weapon_blueprints"), extra, blueprintWeaponIds)
        printIfNotEmpty(FBTxt.txt("missing_wing_blueprints"), extra, blueprintWingIds)
        printIfNotEmpty(FBTxt.txt("missing_hull_blueprints"), extra, blueprintHullIds)
        printIfNotEmpty(FBTxt.txt("missing_industry_blueprints"), extra, blueprintIndustryIds)
        printIfNotEmpty(FBTxt.txt("missing_hullmods_known"), extra, hullModIdsKnown)
        printIfNotEmpty(FBTxt.txt("missing_weapon_cargo"), extra, cargoWeaponIds)
        printIfNotEmpty(FBTxt.txt("missing_wing_cargo"), extra, cargoWingIds)
        printIfNotEmpty(FBTxt.txt("missing_items"), extra, itemIds)

        return (extra.filter { it.isNotBlank() } + listOf(base))
            .filter { it.isNotBlank() }
            .joinToString(FBTxt.txt("missing_section_separator"))
    }
}

fun reportMissingElementsIfAny(
    missingElements: MissingElements,
    defaultShortMessage: String = FBTxt.txt("missing_default_short_message")
) {
    val fullMessage = missingElements.getMissingElementsString()
    if (fullMessage.isNotBlank()) {
        DisplayMessage.showMessage(defaultShortMessage, Color.YELLOW)
        DisplayMessage.logMessage(MissingElements::class.java, "\n" + fullMessage, Level.WARN, displayMessage = false)
    }
}