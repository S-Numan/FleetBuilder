package fleetBuilder.core.makeSaveRemovable

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin
import com.fs.starfarer.api.impl.campaign.ids.Items
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial
import com.fs.starfarer.campaign.CampaignEngine
import com.fs.util.container.repo.ObjectRepository
import fleetBuilder.util.FBMisc.replaceVariantWithVariant
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.api.FactionUtils
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.completelyRemoveMod
import fleetBuilder.util.getModules
import fleetBuilder.util.safeGet

// Jank code alert!

internal object RemoveFromSave {

    fun removeModThings(
        modsToRemove: List<ModSpecAPI>,
        removeHullmods: Boolean = true,
        removeWeapons: Boolean = true,
        removeHullspecs: Boolean = true,
        removeSkills: Boolean = true,
        removeIndustries: Boolean = true,
        removeWings: Boolean = true,
        removeCargoItems: Boolean = true,
        removeMarkets: Boolean = true,
        removeAllFactionOwnedEntities: Boolean = true,
        removeListeners: Boolean = true,
        removeFleets: Boolean = true,
    ) {
        val modsToRemoveStuffFrom = modsToRemove.filter { it.modPluginClassName != null }
        val sector = Global.getSector()
        val settings = Global.getSettings()

        val modIds = modsToRemove.map { it.id }.toSet()

        val hullmods = settings.allHullModSpecs
            .filter { it.sourceMod != null && it.sourceMod.id in modIds }
            .map { it.id }

        val weapons = settings.actuallyAllWeaponSpecs
            .filter { it.sourceMod != null && it.sourceMod.id in modIds }
            .map { it.weaponId }

        val hullspecs = settings.allShipHullSpecs
            .filter { it.sourceMod != null && it.sourceMod.id in modIds }
            .map { it.hullId }

        val wings = settings.allFighterWingSpecs
            .filter { it.sourceMod != null && it.sourceMod.id in modIds }
            .map { it.id }

        val industries = settings.allIndustrySpecs
            .filter { it.sourceMod != null && it.sourceMod.id in modIds }
            .map { it.id }

        val skills = LookupUtils.getAllSkillSpecs()
            .filter { it.sourceMod != null && it.sourceMod.id in modIds }
            .map { it.id }

        val cargoItems = settings.allCommoditySpecs
            .filter { it.sourceMod != null && it.sourceMod.id in modIds }
            .map { it.id } + settings.allSpecialItemSpecs
            .filter { it.sourceMod != null && it.sourceMod.id in modIds }
            .map { it.id }


        if (modsToRemoveStuffFrom.isNotEmpty() && removeListeners) {
            val listenerManager = sector.listenerManager
            val listenerManagerObjectRepository = (listenerManager.safeGet("listeners") as? ObjectRepository)
            val listenersManagerListeners = (listenerManagerObjectRepository?.safeGet("lists") as? Map<Class<*>, *>)?.keys?.toList()

            val sectorScripts = sector.scripts.map { it.javaClass }
            val sectorListeners = sector.allListeners

            listenersManagerListeners?.forEach { listenerClass ->
                if (listenerClass.name.startsWith("com")) return@forEach
                val listenerJarLocation = listenerClass.protectionDomain.codeSource?.location.toString().substringAfter("mods/")
                modsToRemoveStuffFrom.forEach { mod ->
                    if (mod.dirName + "/" + mod.jars.getOrNull(0) == listenerJarLocation) {
                        //Console.showMessage("listenerManager: Mod: ${mod.dirName}    Listener: ${listenerClass.name}")
                        while (listenerManager.hasListenerOfClass(listenerClass))
                            listenerManager.removeListenerOfClass(listenerClass)
                    }
                }
            }

            sectorScripts?.forEach { listenerClass ->
                if (listenerClass.name.startsWith("com")) return@forEach
                val listenerJarLocation = listenerClass.protectionDomain.codeSource?.location.toString().substringAfter("mods/")
                modsToRemoveStuffFrom.forEach { mod ->
                    if (mod.dirName + "/" + mod.jars.getOrNull(0) == listenerJarLocation) {
                        //Console.showMessage("sectorScripts: Mod: ${mod.dirName}    Listener: ${listenerClass.name}")
                        while (sector.hasScript(listenerClass))
                            listenerManager.removeListenerOfClass(listenerClass)
                    }
                }
            }

            sectorListeners?.forEach { listenerClass ->
                if (listenerClass.javaClass.name.startsWith("com")) return@forEach
                val listenerJarLocation = listenerClass.javaClass.protectionDomain.codeSource?.location.toString().substringAfter("mods/")
                modsToRemoveStuffFrom.forEach { mod ->
                    if (mod.dirName + "/" + mod.jars.getOrNull(0) == listenerJarLocation) {
                        //Console.showMessage("sectorListeners: Mod: ${mod.dirName}    Listener: ${listenerClass.javaClass.name}")
                        sector.removeListener(listenerClass)
                        sector.removeListener(listenerClass)
                    }
                }
            }

            val locations = Global.getSector().allLocations
            val fleets = locations.flatMap { it.fleets }
            fleets.forEach { fleet ->
                fleet.eventListeners.toList().forEach { listenerClass ->
                    if (listenerClass.javaClass.name.startsWith("com")) return@forEach
                    val listenerJarLocation = listenerClass.javaClass.protectionDomain.codeSource?.location.toString().substringAfter("mods/")
                    modsToRemoveStuffFrom.forEach { mod ->
                        if (mod.dirName + "/" + mod.jars.getOrNull(0) == listenerJarLocation) {
                            //Console.showMessage("fleetListeners: Mod: ${mod.dirName}    Listener: ${listenerClass.javaClass.name}")
                            fleet.removeEventListener(listenerClass)
                        }
                    }
                }
            }
        }

        val targetModIds = modsToRemoveStuffFrom.map { it.id }.toSet()
        val removedTargets = mutableSetOf<SectorEntityToken>()
        val locations = Global.getSector().allLocations

        getEntitiesWithThings().forEach { entity ->
            if (removeHullmods)
                hullmods.forEach { hullmod ->
                    entity.removeHullmod(hullmod)
                }
            if (removeWings)
                wings.forEach { wing ->
                    entity.removeWings(wing)
                }
            if (removeIndustries)
                industries.forEach { industry ->
                    entity.removeIndustries(industry)
                }
            if (removeSkills)
                skills.forEach { skill ->
                    entity.removeSkills(skill)
                }
            if (removeWeapons)
                weapons.forEach { weapon ->
                    entity.removeWeapon(weapon)
                }
            if (removeHullspecs)
                hullspecs.forEach { hullspec ->
                    entity.removeHullSpec(hullspec)
                }
            if (removeCargoItems)
                cargoItems.forEach { cargoItem ->
                    entity.removeCargoItem(cargoItem)
                }
        }

        var fleets = locations.flatMap { it.fleets }
        fleets.forEach { fleet ->
            if (fleet.membersWithFightersCopy.isEmpty()) {
                val location = fleet.containingLocation
                if (location != null) {
                    location.removeEntity(fleet)
                    removedTargets.add(fleet)
                }
            }
        }

        if (removeMarkets) {
            val markets = locations.flatMap { it.allEntities }.mapNotNull { it.market }

            markets.toList().forEach { market ->
                val sourceMod = FactionUtils.getSourceModFromFaction(market.faction.id) ?: return@forEach
                if (sourceMod.id !in modsToRemoveStuffFrom.map { it.id }) return@forEach

                val entity = market.primaryEntity

                market.connectedEntities.forEach { connected ->
                    connected.market = null
                }
                market.connectedEntities.clear()

                market.submarketsCopy.forEach { sub ->
                    market.removeSubmarket(sub.specId)
                }

                Global.getSector().economy.removeMarket(market)

                if (entity != null) {
                    entity.market = null
                    entity.containingLocation?.removeEntity(entity)
                    removedTargets.add(entity)
                }
            }
        }

        if (removeFleets) {
            val fleets = locations.flatMap { it.fleets }

            fleets.forEach { fleet ->
                val sourceMod = FactionUtils.getSourceModFromFaction(fleet.faction.id) ?: return@forEach
                if (sourceMod.id !in targetModIds) return@forEach

                val location = fleet.containingLocation
                if (location != null) {
                    location.removeEntity(fleet)
                    removedTargets.add(fleet)
                }
            }
        }

        if (removeAllFactionOwnedEntities) {
            val campaignEntity = locations.flatMap { it.allEntities }.mapNotNull { entity ->
                val sourceMod = FactionUtils.getSourceModFromFaction(entity.faction.id)
                if (sourceMod?.id in modsToRemoveStuffFrom.map { it.id }) {
                    entity
                } else
                    null
            }
            campaignEntity.forEach { entity ->
                entity.containingLocation?.removeEntity(entity)
                removedTargets.add(entity)
            }
        }

        fleets = locations.flatMap { it.fleets }
        // Remove assignments involving removed targets from every fleet
        fleets.forEach { fleet ->
            fleet.assignmentsCopy.toList().forEach {
                if (it.target in removedTargets) {
                    fleet.ai.removeAssignment(it)
                }
            }
        }

        //if (removeFactions) {
        /*
        Global.getSector().allFactions.toList().forEach { faction ->
            val sourceMod = FactionUtils.getSourceModFromFaction(faction.id) ?: return@forEach
            if (sourceMod.id !in targetModIds) return@forEach
            Global.getSector().allFactions.remove(faction)
        }*/
        // Does not appear to do anything
        //}
    }

    private fun getEntitiesWithThings(): List<HasThing> {
        val locations = Global.getSector().allLocations

        val submarkets = locations.flatMap { it.allEntities }.mapNotNull { it.market }.flatMap { it.submarketsCopy }
        val markets = locations.flatMap { it.allEntities }.mapNotNull { it.market }

        val fleetMembers = listOf(
            locations.flatMap { it.fleets }.map { it.fleetData }, // Ships in active fleets.
            submarkets.mapNotNull { it.cargo?.mothballedShips },  // Ships in storage.
        ).flatten().flatMap { it.membersListCopy }

        val derelicts = locations.flatMap { it.allEntities }.mapNotNull { if (it.customPlugin is DerelictShipEntityPlugin) it.customPlugin as DerelictShipEntityPlugin else null }

        return listOf(
            Global.getSector().allFactions.map { Faction(it) }, // Factions.
            submarkets.map { Submarket(it) }, // Submarkets.
            markets.map { Market(it) }, // Markets.
            fleetMembers.map { Ship(it) }, // Ships.
            CampaignEngine.getInstance().savedVariantData.variantMap.map { Variant(it.value) }, // Global variants.
            listOf(Cargo(Global.getSector().playerFleet.cargo)),
            derelicts.map { Variant(it.data.ship.variant, it.data.ship) },
        ).flatten()
    }

    interface HasThing {
        fun removeHullmod(value: String)
        fun removeHullSpec(value: String)
        fun removeWeapon(value: String)
        fun removeWings(value: String)
        fun removeSkills(value: String)
        fun removeIndustries(value: String)
        fun removeCargoItem(value: String)
    }

    private fun MutableCollection<String>.removeMatching(value: String) {
        removeAll { it == value }
    }

    private class Variant(val variant: ShipVariantAPI?, val perShipData: ShipRecoverySpecial.PerShipData? = null) :
        HasThing {
        override fun removeHullmod(value: String) {
            variant?.completelyRemoveMod(value, true)
            perShipData?.variant?.completelyRemoveMod(value, true)
        }

        override fun removeHullSpec(value: String) {
            if (variant == null) {
                if (perShipData?.variant == null) {
                    if (Global.getSettings().doesVariantExist(perShipData?.variantId) && Global.getSettings().getVariant(perShipData?.variantId).hullSpec.hullId == value) {
                        perShipData?.variantId = VariantUtils.createErrorVariant("Removed Mod").hullVariantId
                    }
                } else if (perShipData.variant?.hullSpec?.hullId == value) {
                    perShipData.variant = VariantUtils.createErrorVariant("Removed Mod")
                    perShipData.variantId = perShipData.variant?.hullVariantId
                }
                return
            }

            if (variant.hullSpec.hullId == value) {
                replaceVariantWithVariant(variant, VariantUtils.createErrorVariant("Removed Mod"))
                if (perShipData?.variantId != null)
                    perShipData.variantId = variant.hullVariantId
                if (perShipData?.variant != null)
                    perShipData.variant = variant
            }
        }

        override fun removeWeapon(value: String) {
            if (variant == null) return

            fun removeWeapon(variant: ShipVariantAPI) {
                variant.nonBuiltInWeaponSlots.forEach { slot ->
                    if (variant.getWeaponId(slot) == value) {
                        variant.clearSlot(slot)
                    }
                }
            }
            removeWeapon(variant)
            variant.getModules().forEach { removeWeapon(it) }
            if (perShipData?.variant != null) {
                removeWeapon(perShipData.variant)
                perShipData.variant.getModules().forEach { removeWeapon(it) }
            }
        }

        override fun removeWings(value: String) {
            if (variant == null) return

            variant.wings.removeMatching(value)
            variant.getModules().forEach { variant ->
                variant.wings.removeMatching(value)
            }
            if (perShipData?.variant != null) {
                perShipData.variant.wings.removeMatching(value)
                perShipData.variant.getModules().forEach { it.wings.removeMatching(value) }
            }
        }

        override fun removeSkills(value: String) {}
        override fun removeIndustries(value: String) {}
        override fun removeCargoItem(value: String) {}
    }

    private class Ship(val member: FleetMemberAPI) : HasThing {
        override fun removeHullmod(value: String) {
            member.variant.completelyRemoveMod(value, true)
        }

        override fun removeHullSpec(value: String) {
            if (member.variant.hullSpec.hullId == value) {
                //member.setVariant(VariantUtils.createErrorVariant("Removed Mod"), true, true)
                member.fleetData?.removeFleetMember(member) // This might be more effective.
            }
        }

        override fun removeWeapon(value: String) {
            fun removeWeapon(variant: ShipVariantAPI) {
                variant.nonBuiltInWeaponSlots.forEach { slot ->
                    if (variant.getWeaponId(slot) == value) {
                        variant.clearSlot(slot)
                    }
                }
            }
            removeWeapon(member.variant)
            member.variant.getModules().forEach { variant ->
                removeWeapon(variant)
            }
        }

        override fun removeWings(value: String) {
            member.variant.wings.removeMatching(value)
            member.variant.getModules().forEach { variant ->
                variant.wings.removeMatching(value)
            }
        }

        override fun removeSkills(value: String) {
            val captain = member.captain ?: return
            captain.stats.skillsCopy.toList()
                .filter { it.skill.id == value }
                .forEach {
                    captain.stats.setSkillLevel(it.skill.id, 0f)
                    captain.stats.skillsCopy.remove(it)
                }
        }

        override fun removeIndustries(value: String) {}
        override fun removeCargoItem(value: String) {}
    }

    private class Faction(val faction: FactionAPI) : HasThing {
        override fun removeHullmod(value: String) {
            faction.removeKnownHullMod(value)
            faction.removePriorityHullMod(value)
        }

        override fun removeHullSpec(value: String) {
            faction.removeKnownShip(value)
            faction.removePriorityShip(value)
            faction.removeUseWhenImportingShip(value)
        }

        override fun removeWeapon(value: String) {
            faction.removeKnownWeapon(value)
            faction.removePriorityWeapon(value)
        }

        override fun removeWings(value: String) {
            faction.removeKnownFighter(value)
            faction.removePriorityFighter(value)
        }

        override fun removeSkills(value: String) {

        }

        override fun removeIndustries(value: String) {
            faction.removeKnownIndustry(value)
        }

        override fun removeCargoItem(value: String) {
            faction.illegalCommodities.removeMatching(value)
        }
    }

    private class Submarket(val submarket: SubmarketAPI) : HasThing {
        override fun removeHullmod(value: String) {
            val cargo = submarket.cargo

            cargo.stacksCopy.toList().forEach { stack ->
                val special = stack.specialDataIfSpecial ?: return@forEach

                if (stack.specialItemSpecIfSpecial?.id == Items.TAG_MODSPEC &&
                    special.data == value
                ) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }

        override fun removeHullSpec(value: String) {
            val cargo = submarket.cargo

            cargo.stacksCopy.forEach { stack ->
                val special = stack.specialDataIfSpecial ?: return@forEach

                if (stack.specialItemSpecIfSpecial?.id == Items.SHIP_BP &&
                    special.data == value
                ) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }

        override fun removeWeapon(value: String) {
            val cargo = submarket.cargo
            // Remove actual weapons
            val quantity = cargo.getQuantity(CargoAPI.CargoItemType.WEAPONS, value)
            if (quantity > 0) {
                cargo.removeWeapons(value, quantity.toInt())
            }

            // Remove weapon blueprints
            cargo.stacksCopy.forEach { stack ->
                val special = stack.specialDataIfSpecial ?: return@forEach

                if (stack.specialItemSpecIfSpecial?.id == Items.WEAPON_BP &&
                    special.data == value
                ) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }

        override fun removeWings(value: String) {
            val cargo = submarket.cargo

            // Remove fighter LPCs
            val quantity = cargo.getQuantity(CargoAPI.CargoItemType.FIGHTER_CHIP, value)
            if (quantity > 0) {
                cargo.removeFighters(value, quantity.toInt())
            }

            // Remove fighter blueprints
            cargo.stacksCopy.forEach { stack ->
                val special = stack.specialDataIfSpecial ?: return@forEach

                if (stack.specialItemSpecIfSpecial?.id == Items.FIGHTER_BP &&
                    special.data == value
                ) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }

        override fun removeSkills(value: String) {

        }

        override fun removeIndustries(value: String) {
            val cargo = submarket.cargo

            cargo.stacksCopy.forEach { stack ->
                val special = stack.specialDataIfSpecial ?: return@forEach

                if (stack.specialItemSpecIfSpecial?.id == Items.INDUSTRY_BP &&
                    special.data == value
                ) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }

        override fun removeCargoItem(value: String) {
            submarket.cargo.stacksCopy.forEach { stack ->
                if (stack.specialDataIfSpecial?.id == value) {
                    submarket.cargo.removeItems(stack.type, stack.data, stack.size)
                } else if (stack.specialDataIfSpecial?.data == value) {
                    submarket.cargo.removeItems(stack.type, stack.data, stack.size)
                } else if (stack.commodityId == value) {
                    submarket.cargo.removeItems(stack.type, stack.data, stack.size)
                } else if (stack.specialItemSpecIfSpecial?.id == value) {
                    submarket.cargo.removeItems(stack.type, stack.data, stack.size)
                } else if (stack.resourceIfResource?.id == value) {
                    submarket.cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }
    }

    private class Market(val market: MarketAPI) : HasThing {
        override fun removeHullmod(value: String) {

        }

        override fun removeHullSpec(value: String) {

        }

        override fun removeWeapon(value: String) {

        }

        override fun removeWings(value: String) {

        }

        override fun removeSkills(value: String) {
            val commDirectory = market.commDirectory ?: return

            fun removeSkillsFromPerson(person: PersonAPI) {
                val stats = person.stats ?: return

                if (stats.hasSkill(value)) {
                    val skill = stats.skillsCopy.find { it.skill.id == value }
                    stats.setSkillLevel(value, 0f)
                    stats.skillsCopy.remove(skill)
                }
            }

            commDirectory.entriesCopy.forEach { entry ->
                val person = entry.entryData as? PersonAPI ?: return@forEach
                removeSkillsFromPerson(person)
            }

            if (market.admin != null)
                removeSkillsFromPerson(market.admin)

            market.peopleCopy.forEach { person ->
                removeSkillsFromPerson(person)
            }
        }

        override fun removeIndustries(value: String) {
            val industry = market.getIndustry(value)
            if (industry != null) {
                market.removeIndustry(value, null, false)
            }
        }

        override fun removeCargoItem(value: String) {}
    }

    private class Cargo(val cargo: CargoAPI) : HasThing {
        override fun removeHullmod(value: String) {
            cargo.stacksCopy.toList().forEach { stack ->
                val special = stack.specialDataIfSpecial ?: return@forEach

                if (stack.specialItemSpecIfSpecial?.id == Items.TAG_MODSPEC &&
                    special.data == value
                ) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }

        override fun removeHullSpec(value: String) {
            cargo.stacksCopy.forEach { stack ->
                val special = stack.specialDataIfSpecial ?: return@forEach

                if (stack.specialItemSpecIfSpecial?.id == Items.SHIP_BP &&
                    special.data == value
                ) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }

        override fun removeWeapon(value: String) {
            // Remove actual weapons
            val quantity = cargo.getQuantity(CargoAPI.CargoItemType.WEAPONS, value)
            if (quantity > 0) {
                cargo.removeWeapons(value, quantity.toInt())
            }

            // Remove weapon blueprints
            cargo.stacksCopy.forEach { stack ->
                val special = stack.specialDataIfSpecial ?: return@forEach

                if (stack.specialItemSpecIfSpecial?.id == Items.WEAPON_BP &&
                    special.data == value
                ) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }

        override fun removeWings(value: String) {
            // Remove fighter LPCs
            val quantity = cargo.getQuantity(CargoAPI.CargoItemType.FIGHTER_CHIP, value)
            if (quantity > 0) {
                cargo.removeFighters(value, quantity.toInt())
            }

            // Remove fighter blueprints
            cargo.stacksCopy.forEach { stack ->
                val special = stack.specialDataIfSpecial ?: return@forEach

                if (stack.specialItemSpecIfSpecial?.id == Items.FIGHTER_BP &&
                    special.data == value
                ) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }

        override fun removeSkills(value: String) {

        }

        override fun removeIndustries(value: String) {
            cargo.stacksCopy.forEach { stack ->
                val special = stack.specialDataIfSpecial ?: return@forEach

                if (stack.specialItemSpecIfSpecial?.id == Items.INDUSTRY_BP &&
                    special.data == value
                ) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }

        override fun removeCargoItem(value: String) {
            cargo.stacksCopy.forEach { stack ->
                if (stack.specialDataIfSpecial?.id == value) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                } else if (stack.specialDataIfSpecial?.data == value) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                } else if (stack.commodityId == value) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                } else if (stack.specialItemSpecIfSpecial?.id == value) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                } else if (stack.resourceIfResource?.id == value) {
                    cargo.removeItems(stack.type, stack.data, stack.size)
                }
            }
        }

    }
}