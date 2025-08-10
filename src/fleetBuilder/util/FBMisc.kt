package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.fleet.RepairTrackerAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.ModSettings.randomPastedCosmetics
import fleetBuilder.features.CommanderShuttle.addPlayerShuttle
import fleetBuilder.features.CommanderShuttle.playerShuttleExists
import fleetBuilder.features.CommanderShuttle.removePlayerShuttle
import fleetBuilder.persistence.fleet.FleetSerialization
import fleetBuilder.persistence.fleet.FleetSerialization.buildFleetFull
import fleetBuilder.persistence.fleet.FleetSerialization.getFleetFromJson
import fleetBuilder.persistence.fleet.FleetSerialization.saveFleetToJson
import fleetBuilder.persistence.fleet.FleetSerialization.validateAndCleanFleetData
import fleetBuilder.persistence.member.MemberSerialization
import fleetBuilder.persistence.person.PersonSerialization
import fleetBuilder.persistence.variant.VariantSerialization
import fleetBuilder.util.DisplayMessage.showMessage
import fleetBuilder.util.ReflectionMisc.getViewedFleetInFleetPanel
import fleetBuilder.util.ReflectionMisc.updateFleetPanelContents
import fleetBuilder.variants.*
import org.json.JSONObject
import java.awt.Color
import java.util.*
import kotlin.math.max


object FBMisc {

    fun replacePlayerFleetWith(
        data: FleetSerialization.ParsedFleetData, replacePlayer: Boolean = false,
        settings: FleetSerialization.FleetSettings = FleetSerialization.FleetSettings()
    ): MissingElements {
        val fleet = Global.getFactory().createEmptyFleet(Factions.INDEPENDENT, FleetTypes.TASK_FORCE, false)
        val missing = buildFleetFull(data, fleet.fleetData)

        replacePlayerFleetWith(
            fleet,
            if (settings.includeAggression) data.aggression else -1,
            replacePlayer, settings
        )

        return missing
    }

    fun replacePlayerFleetWith(
        fleet: CampaignFleetAPI, aggression: Int = -1, replacePlayer: Boolean = false,
        settings: FleetSerialization.FleetSettings = FleetSerialization.FleetSettings()
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

        //Hack to copy the fleet over
        val jsonFleet = saveFleetToJson(fleet)
        getFleetFromJson(
            jsonFleet, playerFleet,
            settings
        )

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
            val overflow = (playerFleet.cargo.totalPersonnel - playerFleet.cargo.maxPersonnel)

            playerFleet.cargo.removeCrew(overflow.coerceAtMost(playerFleet.fleetData.minCrew).toInt())
        }
        // Repair
        fullFleetRepair(playerFleet)


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

    fun fullFleetRepair(fleet: CampaignFleetAPI) {
        fleet.fleetData.membersListCopy.forEach { member ->
            member.status.repairFully()

            val repairs: RepairTrackerAPI = member.repairTracker
            repairs.cr = max(repairs.cr, repairs.maxCR)
            member.setStatUpdateNeeded(true)
        }
    }

    fun isMouseHoveringOverComponent(component: UIComponentAPI, pad: Float = 0f): Boolean {
        val x = component.position.x - pad
        val y = component.position.y - pad
        val width = component.position.width + pad
        val height = component.position.height + pad

        return isMouseWithinBounds(x, y, width, height)
    }

    fun isMouseWithinBounds(x: Float, y: Float, width: Float, height: Float): Boolean {
        val mouseX = Global.getSettings().mouseX
        val mouseY = Global.getSettings().mouseY

        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height
    }

    fun createFleetFromJson(
        json: JSONObject,
        settings: FleetSerialization.FleetSettings = FleetSerialization.FleetSettings(),
        faction: String = Factions.INDEPENDENT
    ): CampaignFleetAPI {
        val fleet = Global.getFactory().createEmptyFleet(faction, FleetTypes.TASK_FORCE, true)

        val missingElements = getFleetFromJson(
            json,
            fleet,
            settings
        )

        reportMissingElementsIfAny(missingElements)

        return fleet
    }

    fun campaignPaste(
        sector: SectorAPI,
        data: Any,
    ): Boolean {
        if (data !is FleetSerialization.ParsedFleetData) {
            DisplayMessage.showError("Data valid, but it was not fleet data. You can only paste fleet data into the campaign.")
            return false
        }

        val subMissing = MissingElements()
        val validatedData = validateAndCleanFleetData(data, MissingElements(), settings = FleetSerialization.FleetSettings())

        if (validatedData.members.isEmpty()) {
            reportMissingElementsIfAny(subMissing, "Fleet was empty when pasting")
            return false
        }

        Dialogs.spawnFleetInCampaignDialog(sector, data, validatedData)

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
            is PersonSerialization.ParsedPersonData -> {
                // Officer
                val (person, subMissing) = PersonSerialization.buildPersonFull(data)
                missing.add(subMissing)

                if (randomPastedCosmetics) {
                    randomizePersonCosmetics(person, playerFleet.fleet.faction)
                }
                playerFleet.addOfficer(person)
                showMessage("Added officer to fleet")
            }

            is VariantSerialization.ParsedVariantData -> {
                // Variant
                val (variant, subMissing) = VariantSerialization.buildVariantFull(data)
                missing.add(subMissing)

                if (subMissing.hullIds.size > 1) {
                    reportMissingElementsIfAny(subMissing, "Could not find hullId when pasting variant")
                    return
                }

                val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant)

                if (randomPastedCosmetics)
                    randomizeMemberCosmetics(member, fleetToAddTo)

                fleetToAddTo.addFleetMember(member)

                showMessage("Added variant of hull '${variant.hullSpec.hullName}' to ${if (uiShowsSubmarketFleet) "submarket" else "fleet"}", variant.hullSpec.hullName, Misc.getHighlightColor())

                updateFleetPanelContents()
            }

            is MemberSerialization.ParsedMemberData -> {
                // Fleet member
                val (member, subMissing) = MemberSerialization.buildMemberFull(data)
                missing.add(subMissing)

                if (subMissing.hullIds.size > 1) {
                    reportMissingElementsIfAny(subMissing, "Could not find hullId when pasting member")
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

            is FleetSerialization.ParsedFleetData -> {
                // Fleet
                val subMissing = MissingElements()
                val validatedFleet = validateAndCleanFleetData(data, subMissing, settings = FleetSerialization.FleetSettings())

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
                val faction = Global.getSettings().getFactionSpec(Factions.PLAYER)
                val portrait = if (Math.random() < 0.5)
                    faction.malePortraits.pick()
                else
                    faction.femalePortraits.pick()

                officer.portraitSprite = portrait
                officer.name.first = "Unknown"
                officer.name.last = "Officer"
            }
        }
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
