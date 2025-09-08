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
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.FBTxt
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
            DisplayMessage.showMessage(FBTxt.txt("data_valid_but_no_variant"), Color.YELLOW)
            return true
        }

        val missing = MissingElements()
        val variant = buildVariantFull(data, missing = missing)

        if (missing.hullIds.isNotEmpty()) {
            DisplayMessage.showMessage(
                FBTxt.txt("failed_to_import_loadout", missing.hullIds.first()),
                Color.YELLOW
            )
            return true
        }


        val loadoutExists = doesLoadoutExist(variant)

        if (!loadoutExists) {
            Dialogs.createImportLoadoutDialog(variant, missing)
        } else {
            DisplayMessage.showMessage(
                FBTxt.txt("loadout_already_exists", variant.hullSpec.hullId),
                variant.hullSpec.hullName,
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
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_build_in_slots"), Color.YELLOW)
            return emptyList<String>() to 0f
        }
        maxSMods = min(maxSMods, playerSPLeft)

        if (sModsToApply.size > maxSMods) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_story_point"), Color.YELLOW)
            return emptyList<String>() to 0f
        }

        var canApplySMods: List<String> = sModsToApply.filter { Global.getSector().playerFaction.knowsHullMod(it) }

        if (sModsToApply.size != canApplySMods.size) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_knowledge"), Color.YELLOW)
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
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_item"), Color.YELLOW)
            return emptyList<String>() to 0f
        }

        canApplySMods = sModsToApply.filter { Global.getSettings().getHullModSpec(it).effect.canBeAddedOrRemovedNow(ship, Global.getSector().currentlyOpenMarket, coreUI.tradeMode) }

        if (sModsToApply.size != canApplySMods.size) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_dock"), Color.YELLOW)
            return emptyList<String>() to 0f
        }

        sModsToApply.forEach { modID ->
            if (baseVariant.hullSpec.getOrdnancePoints(null) < Global.getSettings().getHullModSpec(modID).getOPCost(baseVariant.hullSize)) {
                DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_op"), Color.YELLOW)
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
        val text =
            if (points > 1)
                FBTxt.txt("used_story_point_plural", points)
            else
                FBTxt.txt("used_story_point", points)

        Global.getSector().playerStats.spendStoryPoints(
            points,
            true,
            null,
            true,
            (buildInBonus / Global.getSector().playerStats.bonusXPForSpendingStoryPointBeforeSpendingIt.toFloat()) / points,
            text
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
        var newData = data
        if (newData !is DataFleet.ParsedFleetData) {
            fun hackTogetherFleet(member: FleetMemberAPI) {
                val fleet = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, FleetTypes.TASK_FORCE, true)
                fleet.fleetData.addFleetMember(member)
                newData = getFleetDataFromFleet(fleet)
            }
            if (newData is DataMember.ParsedMemberData) {
                val member = buildMemberFull(newData as DataMember.ParsedMemberData)
                hackTogetherFleet(member)
            } else if (newData is DataVariant.ParsedVariantData) {
                val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, buildVariantFull(newData as DataVariant.ParsedVariantData))
                hackTogetherFleet(member)
            } else if (newData is DataPerson.ParsedPersonData) {
                DisplayMessage.showMessage(FBTxt.txt("campaign_officer_spawn"), Color.YELLOW)
                return false
            } else {
                DisplayMessage.showMessage(FBTxt.txt("data_valid_but_no_campaign_paste"), Color.YELLOW)
                return false
            }
        }

        val missing = MissingElements()
        val validatedData = validateAndCleanFleetData(newData as DataFleet.ParsedFleetData, settings = FleetSettings(), missing = missing)

        if (validatedData.members.isEmpty()) {
            reportMissingElementsIfAny(missing, FBTxt.txt("fleet_was_empty_when_pasting"))
            return false
        }

        Dialogs.spawnFleetInCampaignDialog(sector, newData as DataFleet.ParsedFleetData, validatedData, ui)

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
                showMessage(FBTxt.txt("added_officer_to_fleet"))
            }

            is DataVariant.ParsedVariantData -> {
                // Variant
                val variant = buildVariantFull(data, missing = missing)

                if (missing.hullIds.size > 1) {
                    reportMissingElementsIfAny(missing, FBTxt.txt("could_not_find_hullid_when_variant", missing.hullIds.first()))
                    return
                }

                val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant)

                if (randomPastedCosmetics)
                    randomizeMemberCosmetics(member, fleetToAddTo)

                fleetToAddTo.addFleetMember(member)

                val shipName = variant.hullSpec.hullName

                val message = if (uiShowsSubmarketFleet)
                    FBTxt.txt("added_ship_to_fleet", shipName)
                else
                    FBTxt.txt("added_ship_to_submarket", shipName)

                showMessage(message, shipName, Misc.getHighlightColor())

                updateFleetPanelContents()
            }

            is DataMember.ParsedMemberData -> {
                // Fleet member
                val member = buildMemberFull(data, missing = missing)

                if (missing.hullIds.size > 1) {
                    reportMissingElementsIfAny(missing, FBTxt.txt("could_not_find_hullid_when_member", missing.hullIds.first()))
                    return
                }

                if (randomPastedCosmetics)
                    randomizeMemberCosmetics(member, fleetToAddTo)

                fleetToAddTo.addFleetMember(member)
                if (!member.captain.isDefault && !member.captain.isAICore && !uiShowsSubmarketFleet)
                    fleetToAddTo.addOfficer(member.captain)

                val shipName = member.hullSpec.hullName
                val message = if (uiShowsSubmarketFleet) {
                    if (member.captain.isDefault) {
                        FBTxt.txt("added_ship_to_fleet", shipName)
                    } else {
                        FBTxt.txt("added_ship_to_fleet_with_officer", shipName)
                    }
                } else {
                    if (member.captain.isDefault) {
                        FBTxt.txt("added_ship_to_submarket", shipName)
                    } else {
                        FBTxt.txt("added_ship_to_submarket_with_officer", shipName)
                    }
                }

                showMessage(message, shipName, Misc.getHighlightColor())

                updateFleetPanelContents()
            }

            is DataFleet.ParsedFleetData -> {
                // Fleet
                val subMissing = MissingElements()
                val validatedFleet = validateAndCleanFleetData(data, missing = subMissing)

                if (validatedFleet.members.isEmpty()) {
                    reportMissingElementsIfAny(subMissing, FBTxt.txt("fleet_was_empty_when_pasting"))
                    return
                }

                Dialogs.pasteFleetIntoPlayerFleetDialog(data, validatedFleet)
            }

            else -> {
                DisplayMessage.showMessage(FBTxt.txt("data_valid_but_not_fleet_member_variant_person"), Color.YELLOW)
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
