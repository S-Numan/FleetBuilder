package fleetBuilder.integration.save

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.campaign.CampaignEngine
import fleetBuilder.config.ModSettings.modID

//Original code is from AITweaks "MakeAITweaksRemovable", the author being Genrir. Credit to them.

object MakeSaveRemovable {

    private data class Thing(val name: String, val key: String)

    private lateinit var hullmods: List<Thing>

    fun beforeGameSave() {
        clearMemoryKeys()
        getEntitiesWithThings().forEach { processBeforeSave(it) }
    }

    fun afterGameSave() {
        getEntitiesWithThings().forEach { processAfterSave(it) }
        clearMemoryKeys()
    }

    fun onGameLoad() {
        val mutableHullMods: MutableList<Thing> = mutableListOf()

        Global.getSettings().allHullModSpecs.forEach { mod ->
            if (mod.sourceMod != null && mod.sourceMod.id == modID) {//Hullmod is from this mod?
                mutableHullMods.add(Thing(mod.id, "$${mod.id}"))//Add it for removal
            }
        }
        hullmods = mutableHullMods


        afterGameSave()
    }

    private fun processBeforeSave(entity: HasThing) {
        val memory = Global.getSector().memoryWithoutUpdate
        hullmods.forEach { hullmod ->
            if (entity.hullMods().contains(hullmod.name)) {
                entity.hullMods().remove(hullmod.name)
                val key = makeMemoryKey(hullmod.key, entity.key())
                memory.set(key, null)
            }
        }

    }

    private fun processAfterSave(entity: HasThing) {
        val memory = Global.getSector().memoryWithoutUpdate
        hullmods.forEach { hullmod ->
            val key = makeMemoryKey(hullmod.key, entity.key())
            if (memory.contains(key)) {
                entity.hullMods().add(hullmod.name)
            }
        }
    }

    private fun clearMemoryKeys() {
        val memory = Global.getSector().memoryWithoutUpdate
        memory.keys.filter { hullmods.any { hullmod -> it.startsWith(hullmod.key) } }.forEach {
            memory.unset(it)
        }
    }

    private fun getEntitiesWithThings(): List<HasThing> {
        val locations = Global.getSector().allLocations

        val submarkets = locations.flatMap { it.allEntities }.mapNotNull { it.market }.flatMap { it.submarketsCopy }

        val fleetMembers = listOf(
            locations.flatMap { it.fleets }.map { it.fleetData }, // Ships in active fleets.
            submarkets.mapNotNull { it.cargo?.mothballedShips },  // Ships in storage.
        ).flatten().flatMap { it.membersListCopy }

        return listOf(
            Global.getSector().allFactions.map { Faction(it) }, // Factions.
            submarkets.map { Submarket(it) }, // Submarkets.
            fleetMembers.map { Ship(it) }, // Ships.
            CampaignEngine.getInstance().savedVariantData.variantMap.map { Variant(it.key, it.value) }, // Global variants.
            fleetMembers.flatMap { ship ->
                ship.moduleVariants().map { Variant("${it.key} ${ship.id}", it.value) }
            }, // Ship modules.
        ).flatten()
    }

    private fun FleetMemberAPI.moduleVariants(): Map<String, ShipVariantAPI> {
        return this.variant.stationModules.mapValues { this.variant.getModuleVariant(it.key) }
    }

    private fun makeMemoryKey(hullmodKey: String, entityKey: String): String {
        return "$hullmodKey $entityKey".replace(Regex("\\s+"), "_")
    }

    private interface HasThing {
        fun key(): String
        fun hullMods(): MutableCollection<String>
    }

    private class Variant(val key: String, val variant: ShipVariantAPI) : HasThing {
        override fun key() = "variant $key"
        override fun hullMods(): MutableCollection<String> = variant.hullMods
    }

    private class Ship(val member: FleetMemberAPI) : HasThing {
        override fun key() = "ship ${member.id}"
        override fun hullMods(): MutableCollection<String> = member.variant.hullMods
    }

    private class Faction(val faction: FactionAPI) : HasThing {
        override fun key() = "faction ${faction.id}"
        override fun hullMods(): MutableCollection<String> = faction.knownHullMods
    }

    private class Submarket(val submarket: SubmarketAPI) : HasThing {
        override fun key() = "submarket ${submarket.market.primaryEntity.id} ${submarket.nameOneLine}"
        override fun hullMods(): MutableCollection<String> = submarket.faction.knownHullMods
    }
}
