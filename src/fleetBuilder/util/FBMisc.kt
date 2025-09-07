package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.fleet.RepairTrackerAPI
import com.fs.starfarer.api.impl.campaign.HullModItemManager
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.plugins.AutofitPlugin
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.ModSettings.randomPastedCosmetics
import fleetBuilder.features.CommanderShuttle.addPlayerShuttle
import fleetBuilder.features.CommanderShuttle.playerShuttleExists
import fleetBuilder.features.CommanderShuttle.removePlayerShuttle
import fleetBuilder.persistence.fleet.DataFleet
import fleetBuilder.persistence.fleet.DataFleet.buildFleetFull
import fleetBuilder.persistence.fleet.DataFleet.createCampaignFleetFromData
import fleetBuilder.persistence.fleet.DataFleet.getFleetDataFromFleet
import fleetBuilder.persistence.fleet.DataFleet.validateAndCleanFleetData
import fleetBuilder.persistence.fleet.FleetSettings
import fleetBuilder.persistence.member.DataMember
import fleetBuilder.persistence.member.DataMember.buildMemberFull
import fleetBuilder.persistence.person.DataPerson
import fleetBuilder.persistence.person.DataPerson.buildPersonFull
import fleetBuilder.persistence.variant.DataVariant
import fleetBuilder.persistence.variant.DataVariant.buildVariantFull
import fleetBuilder.util.DisplayMessage.showMessage
import fleetBuilder.util.ReflectionMisc.getViewedFleetInFleetPanel
import fleetBuilder.util.ReflectionMisc.updateFleetPanelContents
import fleetBuilder.variants.GameModInfo
import fleetBuilder.variants.LoadoutManager.doesLoadoutExist
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.reportMissingElementsIfAny
import org.json.JSONObject
import org.magiclib.kotlin.getBuildInBonusXP
import org.magiclib.kotlin.getOPCost
import java.awt.Color
import java.util.*
import kotlin.math.max
import kotlin.math.min


object FBMisc {

    fun handleRefitCopy(isShiftDown: Boolean): Boolean {
        val baseVariant = ReflectionMisc.getCurrentVariantInRefitTab() ?: return false

        ClipboardMisc.saveVariantToClipboard(baseVariant, isShiftDown)

        return true
    }

    fun handleRefitPaste(): Boolean {
        var data = ClipboardMisc.extractDataFromClipboard() ?: return false

        if (data is DataMember.ParsedMemberData && data.variantData != null) {
            data = data.variantData
        }
        if (data !is DataVariant.ParsedVariantData) {
            DisplayMessage.showMessage("Data in clipboard was valid, but not a variant", Color.YELLOW)
            return true
        }

        val missing = MissingElements()
        val variant = buildVariantFull(data, missing = missing)

        if (missing.hullIds.isNotEmpty()) {
            DisplayMessage.showMessage(
                "Failed to import loadout. Could not find hullId ${missing.hullIds.first()}",
                Color.YELLOW
            )
            return true
        }


        val loadoutExists = doesLoadoutExist(variant)


        if (!loadoutExists) {
            Dialogs.createImportLoadoutDialog(variant, missing)
        } else {
            DisplayMessage.showMessage(
                "Loadout already exists, cannot import loadout with hull: ${variant.hullSpec.hullId}",
                variant.hullSpec.hullId,
                Misc.getHighlightColor()
            )
        }

        return true
    }

    fun sModHandlerTemp(
        ship: ShipAPI,
        baseVariant: ShipVariantAPI,
        loadout: ShipVariantAPI
    ): Pair<List<String>, Float> {
        val coreUI = ReflectionMisc.getCoreUI() as? CoreUIAPI ?: return emptyList<String>() to 0f

        val playerSPLeft = Global.getSector().playerStats.storyPoints

        var maxSMods = getMaxSMods(ship.mutableStats)
        val currentSMods = (baseVariant.sMods + baseVariant.sModdedBuiltIns).toSet()
        val newSMods = (loadout.sMods + loadout.sModdedBuiltIns).toSet()
        var sModsToApply = newSMods.filter { it !in currentSMods }

        // Filter out SMods that are sModdedBuiltIns in the loadout, but not a built-in hullmod in the baseVariant. See Mad Rockpiper MIDAS from Roider Union for why this is done. Also, sometimes variant skins have built in hullmods that can be sModded.
        sModsToApply = sModsToApply.filterNot { it in loadout.sModdedBuiltIns && !baseVariant.hullSpec.builtInMods.contains(it) }
        if (currentSMods.count { it !in baseVariant.hullSpec.builtInMods } + sModsToApply.count { it !in loadout.hullSpec.builtInMods } > maxSMods) {
            DisplayMessage.showMessage("Cannot apply SMods. Not enough build in slots left", Color.YELLOW)
            return emptyList<String>() to 0f
        }
        maxSMods = min(maxSMods, playerSPLeft)

        if (sModsToApply.size > maxSMods) {
            DisplayMessage.showMessage("Cannot apply SMods. Not enough Story Points", Color.YELLOW)
            return emptyList<String>() to 0f
        }

        var canApplySMods: List<String> = sModsToApply.filter { Global.getSector().playerFaction.knowsHullMod(it) }

        if (sModsToApply.size != canApplySMods.size) {
            DisplayMessage.showMessage("Cannot apply some SMods as you do not know how to add them as hullmods.", Color.YELLOW)
            return emptyList<String>() to 0f
        }

        /*val requiredItems = sModsToApply.mapNotNull { Global.getSettings().getHullModSpec(it).effect.requiredItem }
        val numAvailable = requiredItems.sumOf { HullModItemManager.getInstance().getNumAvailableMinusUnconfirmed(it, ship.fleetMember, baseVariant, Global.getSector().currentlyOpenMarket) }
        if (numAvailable < sModsToApply.size) {
            DisplayMessage.showMessage("Cannot apply some SMods. Lacking required items ...", Color.YELLOW)
            return emptyList<String>() to 0f
        }

        sModsToApply.forEach { modID ->
            val modSpec = Global.getSettings().getHullModSpec(modID)
            if (modSpec.effect.requiredItem == null) return@forEach
            modSpec.effect.requiredItem
            val numAvailable = HullModItemManager.getInstance().getNumAvailableMinusUnconfirmed(modSpec.effect.requiredItem, ship.fleetMember, baseVariant, Global.getSector().currentlyOpenMarket)
        }*/

        canApplySMods = sModsToApply.filter { HullModItemManager.getInstance().isRequiredItemAvailable(it, ship.fleetMember, baseVariant, Global.getSector().currentlyOpenMarket) }
        if (sModsToApply.size != canApplySMods.size) {
            DisplayMessage.showMessage("Cannot apply some SMods. Lacking required items ...", Color.YELLOW)
            return emptyList<String>() to 0f
        }

        canApplySMods = sModsToApply.filter { Global.getSettings().getHullModSpec(it).effect.canBeAddedOrRemovedNow(ship, Global.getSector().currentlyOpenMarket, coreUI.tradeMode) }

        if (sModsToApply.size != canApplySMods.size) {
            DisplayMessage.showMessage("Cannot apply some SMods in the current context. Try legally docking at a market.", Color.YELLOW)
            return emptyList<String>() to 0f
        }

        sModsToApply.forEach { modID ->
            if (baseVariant.hullSpec.getOrdnancePoints(null) < Global.getSettings().getHullModSpec(modID).getOPCost(baseVariant.hullSize)) {
                DisplayMessage.showMessage("Cannot apply some SMods. Hull does not have enough Ordnance Points", Color.YELLOW)
                return emptyList<String>() to 0f
            }
        }

        var bonusXpToGrant = 0f
        sModsToApply.forEach { modID ->
            bonusXpToGrant += getHullModBonusXP(baseVariant, modID)
        }

        return sModsToApply to bonusXpToGrant
    }

    fun getHullModBonusXP(
        variant: ShipVariantAPI,
        modID: String,
    ): Float {
        val defaultBonusXP = Global.getSector().playerStats.bonusXPForSpendingStoryPointBeforeSpendingIt.toFloat()
        if (variant.hullSpec.builtInMods.contains(modID)) {
            return defaultBonusXP
        } else {
            val sMod = Global.getSettings().getHullModSpec(modID)
            return defaultBonusXP * sMod.getBuildInBonusXP(variant.hullSize)
        }
    }

    fun getMaxSMods(fleetMember: FleetMemberAPI): Int {
        return getMaxSMods(fleetMember.stats)
    }

    fun getMaxSMods(stats: MutableShipStatsAPI): Int {
        return stats.dynamic
            .getMod(Stats.MAX_PERMANENT_HULLMODS_MOD)
            .computeEffective(Misc.MAX_PERMA_MODS.toFloat()).toInt()
        //stats.dynamic.getStat(Stats.MAX_PERMANENT_HULLMODS_MOD).modifiedInt
    }

    fun spendStoryPoint(points: Int, buildInBonus: Float) {
        Global.getSector().playerStats.spendStoryPoints(
            points,
            true,
            null,
            true,
            (buildInBonus / Global.getSector().playerStats.bonusXPForSpendingStoryPointBeforeSpendingIt.toFloat()) / points,
            "Used $points story point" + if (points > 1) "s" else ""
        );
        Global.getSoundPlayer().playUISound("ui_char_spent_story_point_technology", 1f, 1f);
    }

    fun replacePlayerFleetWith(
        data: DataFleet.ParsedFleetData, replacePlayer: Boolean = false,
        settings: FleetSettings = FleetSettings()
    ): MissingElements {
        val missing = MissingElements()
        val fleet = createCampaignFleetFromData(data, true, missing = missing)

        replacePlayerFleetWith(
            fleet,
            if (settings.includeAggression) data.aggression else -1,
            replacePlayer, settings
        )

        return missing
    }

    fun replacePlayerFleetWith(
        fleet: CampaignFleetAPI, aggression: Int = -1, replacePlayer: Boolean = false,
        settings: FleetSettings = FleetSettings()
    ) {
        val playerFleet = Global.getSector().playerFleet

        // Clear current fleet members and officers
        for (member in playerFleet.fleetData.membersListCopy)
            playerFleet.fleetData.removeFleetMember(member)
        for (officer in playerFleet.fleetData.officersCopy)
            playerFleet.fleetData.removeOfficer(officer.person)

        addPlayerShuttle()

        settings.includeCommanderSetFlagship = false//The player is always commanding the flagship. Thus if this isn't false, the player will displace the officer of that ship with themselves.
        settings.includeAggression = false//We do this manually for the player faction

        //Copy the fleet over
        val dataFleet = getFleetDataFromFleet(fleet, settings)
        buildFleetFull(dataFleet, playerFleet.fleetData, settings)

        if (replacePlayer) {
            val playerPerson = Global.getSector().playerPerson
            val newCommander = fleet.commander
            copyOfficerData(newCommander, playerPerson)
            Global.getSector().characterData.setName(newCommander.name.fullName, newCommander.gender)

            // Look for a fleet member commanded by an officer matching the new commander
            val matchingMember = playerFleet.membersWithFightersCopy.find { member ->
                val officer = member.captain
                officer != null && !officer.isPlayer && officer.nameString == newCommander.nameString &&
                        officer.stats.level == newCommander.stats.level &&
                        officer.stats.skillsCopy.map { it.skill.id to it.level }.toSet() ==
                        newCommander.stats.skillsCopy.map { it.skill.id to it.level }.toSet()
            }

            // If found, remove the officer and assign the player as captain
            if (matchingMember != null) {
                removePlayerShuttle()

                playerFleet.fleetData.removeOfficer(matchingMember.captain)
                matchingMember.captain = playerPerson
                //Console.showMessage("Replaced duplicate officer with player captain: ${playerPerson.nameString}")
            }
        }

        if (playerShuttleExists()) {
            //Need to move the shuttle to the last member in the fleet, but I don't care enough to do this properly.
            removePlayerShuttle()
            addPlayerShuttle()
        }

        if (aggression != -1) {
            Global.getSector().playerFaction.doctrine.aggression = aggression
        }
    }

    fun fulfillPlayerFleet() {
        val playerFleet = Global.getSector().playerFleet

        // Crew
        val neededCrew = playerFleet.cargo.maxPersonnel - playerFleet.cargo.crew
        playerFleet.cargo.addCrew(neededCrew.toInt())

        // Supplies
        val maxCargoFraction = 0.5f

        val maxSupplies = playerFleet.cargo.maxCapacity - playerFleet.cargo.supplies
        val suppliesToAdd = getFractionHoldableSupplies(playerFleet, maxCargoFraction).coerceAtMost(maxSupplies.toInt())
        playerFleet.cargo.addSupplies(suppliesToAdd.toFloat())

        // Fuel
        val fuelToAdd = playerFleet.cargo.freeFuelSpace
        playerFleet.cargo.addFuel(fuelToAdd.toFloat())

        // Remove excess supplies
        if (playerFleet.cargo.supplies > playerFleet.cargo.maxCapacity * maxCargoFraction) {
            val overflow = playerFleet.cargo.supplies - playerFleet.cargo.maxCapacity * maxCargoFraction

            playerFleet.cargo.removeSupplies(overflow)
        }

        // Remove excess fuel
        if (playerFleet.cargo.fuel > playerFleet.cargo.maxFuel) {
            playerFleet.cargo.removeFuel(playerFleet.cargo.fuel - playerFleet.cargo.maxFuel)
        }

        // Remove excess crew
        if (playerFleet.cargo.totalPersonnel > playerFleet.cargo.maxPersonnel) {
            val removable = playerFleet.cargo.crew - playerFleet.fleetData.minCrew
            val overflow = playerFleet.cargo.totalPersonnel - playerFleet.cargo.maxPersonnel

            if (removable > 0) {
                playerFleet.cargo.removeCrew(minOf(removable, overflow).toInt())
            }
        }
        // Repair
        fullFleetRepair(playerFleet.fleetData)


        updateFleetPanelContents()
    }

    fun copyOfficerData(from: PersonAPI, to: PersonAPI) {
        //to.id = from.id
        to.name = from.name
        to.portraitSprite = from.portraitSprite

        val fromStats = from.stats
        val toStats = to.stats
        if (fromStats != null && toStats != null) {
            toStats.level = fromStats.level

            toStats.xp = fromStats.xp
            toStats.bonusXp = fromStats.bonusXp
            toStats.points = fromStats.points

            toStats.skillsCopy.forEach { skill ->
                toStats.setSkillLevel(skill.skill.id, 0f)
            }

            fromStats.skillsCopy.forEach { skill ->
                toStats.setSkillLevel(skill.skill.id, skill.level)
            }
        }
    }

    fun getFractionHoldableSupplies(fleet: CampaignFleetAPI, maxCargoFraction: Float = 1f): Int {

        // Calculate number of supplies needed to reach maxCargoFraction
        // Cap quantity to 100% remaining cargo space, don't go below 0 either
        val cargo: CargoAPI = fleet.cargo
        var total = Math.min(
            cargo.getSpaceLeft(),
            Math.max(
                0f,
                ((cargo.getMaxCapacity() * maxCargoFraction) - cargo.getSupplies())
            )
        ).toInt()

        // Adjust for cargo space supplies take up (if modded, only use 1 in vanilla)
        val spacePerSupply = Global.getSector().economy
            .getCommoditySpec("supplies").cargoSpace
        if (spacePerSupply > 0)
            total = (total / spacePerSupply).toInt()

        if (total < 0)
            total = 0

        return total
    }

    fun fullFleetRepair(fleet: FleetDataAPI) {
        fleet.membersListCopy.forEach { member ->
            member.status.repairFully()

            val repairs: RepairTrackerAPI = member.repairTracker
            repairs.cr = max(repairs.cr, repairs.maxCR)
            member.setStatUpdateNeeded(true)
        }
    }

    fun isMouseHoveringOverComponent(component: UIComponentAPI, pad: Float = 0f): Boolean {
        val x = component.position.x - pad
        val y = component.position.y - pad
        val width = component.position.width + pad * 2
        val height = component.position.height + pad * 2

        return isMouseWithinBounds(x, y, width, height)
    }

    fun isMouseWithinBounds(x: Float, y: Float, width: Float, height: Float): Boolean {
        val mouseX = Global.getSettings().mouseX
        val mouseY = Global.getSettings().mouseY

        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height
    }

    fun campaignPaste(
        sector: SectorAPI,
        data: Any,
        ui: CampaignUIAPI
    ): Boolean {
        if (data !is DataFleet.ParsedFleetData) {
            DisplayMessage.showMessage("Data valid, but not fleet data. You can only paste fleet data into the campaign.", Color.YELLOW)
            return false
        }

        val missing = MissingElements()
        val validatedData = validateAndCleanFleetData(data, settings = FleetSettings(), missing = missing)

        if (validatedData.members.isEmpty()) {
            reportMissingElementsIfAny(missing, "Fleet was empty when pasting")
            return false
        }

        Dialogs.spawnFleetInCampaignDialog(sector, data, validatedData, ui)

        return true
    }

    fun fleetPaste(
        sector: SectorAPI,
        data: Any
    ) {
        val playerFleet = sector.playerFleet.fleetData

        var uiShowsSubmarketFleet = false

        val fleetToAddTo = getViewedFleetInFleetPanel() ?: playerFleet
        if (fleetToAddTo !== playerFleet)
            uiShowsSubmarketFleet = true

        val missing = MissingElements()

        when (data) {
            is DataPerson.ParsedPersonData -> {
                // Officer
                val person = buildPersonFull(data, missing = missing)

                if (randomPastedCosmetics) {
                    randomizePersonCosmetics(person, playerFleet.fleet.faction)
                }
                playerFleet.addOfficer(person)
                showMessage("Added officer to fleet")
            }

            is DataVariant.ParsedVariantData -> {
                // Variant
                val variant = buildVariantFull(data, missing = missing)

                if (missing.hullIds.size > 1) {
                    reportMissingElementsIfAny(missing, "Could not find hullId when pasting variant")
                    return
                }

                val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant)

                if (randomPastedCosmetics)
                    randomizeMemberCosmetics(member, fleetToAddTo)

                fleetToAddTo.addFleetMember(member)

                showMessage("Added variant of hull '${variant.hullSpec.hullName}' to ${if (uiShowsSubmarketFleet) "submarket" else "fleet"}", variant.hullSpec.hullName, Misc.getHighlightColor())

                updateFleetPanelContents()
            }

            is DataMember.ParsedMemberData -> {
                // Fleet member
                val member = buildMemberFull(data, missing = missing)

                if (missing.hullIds.size > 1) {
                    reportMissingElementsIfAny(missing, "Could not find hullId when pasting member")
                    return
                }

                if (randomPastedCosmetics)
                    randomizeMemberCosmetics(member, fleetToAddTo)

                fleetToAddTo.addFleetMember(member)
                if (!member.captain.isDefault && !member.captain.isAICore && !uiShowsSubmarketFleet)
                    fleetToAddTo.addOfficer(member.captain)

                val shipName = member.hullSpec.hullName
                val message = buildString {
                    append("Added '${shipName}' to ${if (uiShowsSubmarketFleet) "submarket" else "fleet"}")
                    if (!member.captain.isDefault) append(", with an officer")
                }

                showMessage(message, shipName, Misc.getHighlightColor())

                updateFleetPanelContents()
            }

            is DataFleet.ParsedFleetData -> {
                // Fleet
                val subMissing = MissingElements()
                val validatedFleet = validateAndCleanFleetData(data, missing = subMissing)

                if (validatedFleet.members.isEmpty()) {
                    reportMissingElementsIfAny(subMissing, "Fleet was empty when pasting")
                    return
                }

                Dialogs.pasteFleetIntoPlayerFleetDialog(data, validatedFleet)
            }

            else -> {
                DisplayMessage.showMessage("Data valid, but was not fleet, member, variant, or officer data", Color.YELLOW)
            }
        }

        reportMissingElementsIfAny(missing)
    }

    fun randomizeMemberCosmetics(
        member: FleetMemberAPI,
        fleet: FleetDataAPI
    ) {
        member.shipName = fleet.pickShipName(member, Random())
        randomizePersonCosmetics(member.captain, fleet.fleet?.faction)
    }

    fun randomizePersonCosmetics(
        officer: PersonAPI,
        faction: FactionAPI?
    ) {
        if (!officer.isDefault && !officer.isAICore) {
            val randomPerson = faction?.createRandomPerson()
            if (randomPerson != null) {
                officer.name = randomPerson.name
                officer.portraitSprite = randomPerson.portraitSprite
            } else {
                officer.name.gender = FullName.Gender.ANY
                officer.portraitSprite = getRandomPortrait(officer.name.gender, faction = faction?.id)
                officer.name.first = "Unknown"
            }
        }
    }

    fun getRandomPortrait(gender: FullName.Gender = FullName.Gender.ANY, faction: String? = null): String {
        val faction = Global.getSettings().getFactionSpec(faction ?: Factions.PLAYER)
        return if (gender == FullName.Gender.MALE)
            faction.malePortraits.pick()
        else if (gender == FullName.Gender.FEMALE)
            faction.femalePortraits.pick()
        else
            if (Random().nextBoolean()) faction.malePortraits.pick() else faction.femalePortraits.pick()
    }

    fun getModInfosFromJson(json: JSONObject, onlyMissing: Boolean = false): MutableSet<GameModInfo> {
        val gameMods = mutableSetOf<GameModInfo>()
        json.optJSONArray("mod_info")?.let {
            repeat(it.length()) { i ->
                val modSpecJson = it.optJSONObject(i)
                val modSpecId = modSpecJson.optString("mod_id")
                val modSpecName = modSpecJson.optString("mod_name")
                val modSpecVersion = modSpecJson.optString("mod_version")

                var hasMod = false
                for (modSpecAPI in Global.getSettings().modManager.enabledModsCopy) {
                    if (modSpecAPI.id == modSpecId) {
                        hasMod = true
                        break
                    }
                }

                if (onlyMissing && hasMod) return@repeat

                gameMods.add(GameModInfo(modSpecId, modSpecName, modSpecVersion))
            }
        }
        return gameMods
    }
}
