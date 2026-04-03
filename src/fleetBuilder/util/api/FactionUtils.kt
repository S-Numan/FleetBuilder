package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import fleetBuilder.core.FBSettings
import fleetBuilder.core.FBMisc

object FactionUtils {

    /**
     * Get the source mod of a given faction.
     *
     * WARNING: This method is not very accurate. Do not rely on it.
     * This assumes the faction file name is the same as the faction id.
     **/
    fun getSourceModFromFaction(factionId: String): ModSpecAPI? {
        if (factionToModMap == null)
            factionToModMap = buildFactionToModMap()
        return factionToModMap?.get(factionId)
    }

    private var factionToModMap: Map<String, ModSpecAPI?>? = null
    private fun buildFactionToModMap(): Map<String, ModSpecAPI?> {
        val result = mutableMapOf<String, ModSpecAPI?>()

        val settings = Global.getSettings()
        val modManager = settings.modManager

        val path = "data/world/factions/factions.csv"
        val csv = settings.getMergedSpreadsheetDataForMod("faction", path, FBSettings.getModID())
        val csvList = FBMisc.jsonArrayToList(csv)

        val factions = Global.getSector().allFactions

        for (row in csvList) {
            val map = row as? Map<*, *> ?: continue

            val factionPath = map["faction"]?.toString() ?: continue
            val sourcePath = map["fs_rowSource"]?.toString() ?: continue

            // Extract faction id from path
            val factionIdFromPath = factionPath
                .substringAfterLast("/")
                .substringBefore(".")

            val faction = factions.find { it.id == factionIdFromPath } ?: continue

            // Find matching mod by path
            val modSpec = modManager.enabledModsCopy.firstOrNull { mod ->
                val modPath = mod.path
                sourcePath.startsWith(modPath)
            }

            result[faction.id] = modSpec
        }

        return result
    }
}