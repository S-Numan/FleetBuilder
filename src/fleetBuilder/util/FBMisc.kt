package fleetBuilder.util

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.fleet.RepairTrackerAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.codex2.CodexDialog
import fleetBuilder.config.ModSettings.randomPastedCosmetics
import fleetBuilder.features.CommanderShuttle.addPlayerShuttle
import fleetBuilder.features.CommanderShuttle.playerShuttleExists
import fleetBuilder.features.CommanderShuttle.removePlayerShuttle
import fleetBuilder.persistence.CargoSerialization.getCargoFromJson
import fleetBuilder.persistence.CargoSerialization.saveCargoToJson
import fleetBuilder.persistence.FleetSerialization
import fleetBuilder.persistence.FleetSerialization.buildFleetFull
import fleetBuilder.persistence.FleetSerialization.extractFleetDataFromJson
import fleetBuilder.persistence.FleetSerialization.getFleetFromJson
import fleetBuilder.persistence.FleetSerialization.saveFleetToJson
import fleetBuilder.persistence.FleetSerialization.validateAndCleanFleetData
import fleetBuilder.persistence.MemberSerialization
import fleetBuilder.persistence.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.PersonSerialization.getPersonFromJsonWithMissing
import fleetBuilder.persistence.PersonSerialization.savePersonToJson
import fleetBuilder.persistence.VariantSerialization
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
import fleetBuilder.ui.PopUpUI.PopUpUI
import fleetBuilder.ui.PopUpUI.PopUpUIDialog
import fleetBuilder.util.ClipboardUtil.setClipboardText
import fleetBuilder.util.DisplayMessage.showError
import fleetBuilder.util.DisplayMessage.showMessage
import fleetBuilder.util.ReflectionMisc.getCodexEntryParam
import fleetBuilder.util.ReflectionMisc.getViewedFleetInFleetPanel
import fleetBuilder.util.ReflectionMisc.updateFleetPanelContents
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.MissingElementsExtended
import fleetBuilder.variants.VariantLib
import org.json.JSONArray
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.optFloat
import org.lwjgl.input.Keyboard
import java.awt.Color
import java.util.*
import kotlin.math.max


object FBMisc {

    fun createDevModeDialog() {
        val dialog = PopUpUIDialog("Dev Options", addCancelButton = false, addConfirmButton = false, addCloseButton = true)
        dialog.addToggle("Toggle Dev Mode", Global.getSettings().isDevMode)

        dialog.onConfirm { fields ->
            Global.getSettings().isDevMode = fields["Toggle Dev Mode"] as Boolean
        }
        initPopUpUI(dialog, 500f, 200f)
    }

    fun replacePlayerFleetWith(
        json: JSONObject, replacePlayer: Boolean = false,
        settings: FleetSerialization.FleetSettings = FleetSerialization.FleetSettings()
    ): MissingElements {
        val fleet = Global.getFactory().createEmptyFleet(Factions.INDEPENDENT, FleetTypes.TASK_FORCE, true)
        val missing = getFleetFromJson(
            json, fleet.fleetData
        )

        missing.add(
            replacePlayerFleetWith(
                fleet,
                if (settings.includeAggression) json.optInt("aggression_doctrine", 2) else -1,
                replacePlayer, settings
            )
        )
        return missing
    }

    fun replacePlayerFleetWith(
        fleet: CampaignFleetAPI, aggression: Int = -1, replacePlayer: Boolean = false,
        settings: FleetSerialization.FleetSettings = FleetSerialization.FleetSettings()
    ): MissingElements {
        val missing = MissingElements()
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
        missing.add(
            getFleetFromJson(
                jsonFleet, playerFleet,
                settings
            )
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

        return missing
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

    fun initPopUpUI(dialog: PopUpUI, width: Float, height: Float) {
        val coreUI = ReflectionMisc.getCoreUI() ?: return

        if (!Global.getSector().isPaused)
            Global.getSector().isPaused = true

        if (Global.getCurrentState() != GameState.TITLE && Global.getCombatEngine() != null && !Global.getCombatEngine().isPaused)
            Global.getCombatEngine().isPaused = true

        val panelAPI = Global.getSettings().createCustom(width, height, dialog)
        dialog.init(
            panelAPI,
            coreUI.position.centerX - panelAPI.position.width / 2,
            coreUI.position.centerY + panelAPI.position.height / 2,
        )


        /*  //Top Left
            dialog.init(
                panelAPI,
                0f,
                coreUI.position.height,
            )
            dialog.isDialog = false
        */
    }

    fun isPopUpUIOpen(): Boolean {
        ReflectionMisc.getCoreUI()?.getChildrenCopy()?.forEach { child ->
            if (child is CustomPanelAPI && child.plugin is PopUpUI) {
                return true
            }
        }
        return false
    }

    fun isMouseHoveringOverComponent(component: UIComponentAPI): Boolean {
        val x = component.position.x
        val y = component.position.y
        val width = component.position.width
        val height = component.position.height

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

    fun reportMissingElementsIfAny(
        missingElements: MissingElements,
        defaultShortMessage: String = "HAD MISSING ELEMENTS: see console for more details"
    ) {
        val fullMessage = missingElements.getMissingElementsString()
        if (fullMessage.isNotBlank()) {
            showError(defaultShortMessage, fullMessage)
        }
    }

    fun addCodexParamEntryToFleet(sector: SectorAPI, param: Any, ctrlCreatesBlueprints: Boolean = true) {
        val shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
        val alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)
        val ctrl = ctrlCreatesBlueprints && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)

        val count = when {
            shift && alt -> 100
            shift || alt -> 10
            else -> 1
        }

        val cargo = Global.getSector().playerFleet.cargo

        var message: String? = null

        var json: JSONObject? = null

        when (param) {
            is CommoditySpecAPI -> {
                cargo.addCommodity(param.id, count.toFloat())
                message = "Added $count '${param.name}' to cargo"
            }

            is SpecialItemSpecAPI -> {
                cargo.addSpecial(SpecialItemData(param.id, null), count.toFloat())
                message = "Added $count '${param.name}' to cargo"
            }

            is WeaponSpecAPI -> {
                if (ctrl) {
                    cargo.addSpecial(SpecialItemData("weapon_bp", param.weaponId), count.toFloat())
                    message = "Added $count '${param.weaponName}' blueprint to your cargo"
                } else {
                    cargo.addWeapons(param.weaponId, count)
                    message = "Added $count '${param.weaponName}' to cargo"
                }
            }

            is FighterWingSpecAPI -> {
                if (ctrl) {
                    cargo.addSpecial(SpecialItemData("fighter_bp", param.id), count.toFloat())
                    message = "Added $count '${param.wingName}' blueprint to your cargo"
                } else {
                    cargo.addFighters(param.id, count)
                    message = "Added $count '${param.wingName}' to cargo"
                }
            }

            is HullModSpecAPI -> {
                if (ctrl) {
                    cargo.addSpecial(SpecialItemData("modspec", param.id), count.toFloat())
                    message = "Added $count '${param.displayName}' blueprint to your cargo"
                } else {
                    Global.getSector().playerFaction.addKnownHullMod(param.id)
                    message = "Added '${param.displayName}' your faction's known hullmods"
                }
            }

            is ShipHullSpecAPI -> {
                if (ctrl) {
                    cargo.addSpecial(SpecialItemData("ship_bp", param.hullId), count.toFloat())
                    message = "Added $count '${param.hullName}' blueprint to your cargo"
                } else {
                    val emptyVariant =
                        Global.getSettings().createEmptyVariant(param.hullId, param)
                    json = saveVariantToJson(emptyVariant)
                }
            }

            is FleetMemberAPI -> {
                if (ctrl) {
                    cargo.addSpecial(SpecialItemData("ship_bp", param.hullId), count.toFloat())
                    message = "Added $count '${param.hullSpec.hullName}' blueprint to your cargo"
                } else {
                    json = saveMemberToJson(param)
                }
            }
        }

        if (json != null) {
            repeat(count) {
                fleetPaste(sector, json)
            }
        }

        if (!message.isNullOrEmpty())
            showMessage(message)
    }

    fun codexEntryToClipboard(codex: CodexDialog) {
        val param = getCodexEntryParam(codex)
        if (param == null) return

        when (param) {
            is ShipHullSpecAPI -> {
                val emptyVariant = Global.getSettings().createEmptyVariant(param.hullId, param)
                val json = saveVariantToJson(emptyVariant)
                setClipboardText(json.toString(4))
                showMessage("Copied codex variant to clipboard")
            }

            is FleetMemberAPI -> {
                val json = saveMemberToJson(param)
                setClipboardText(json.toString(4))
                showMessage("Copied codex member to clipboard")
            }
        }
    }

    fun campaignPaste(
        sector: SectorAPI,
        json: JSONObject
    ): Boolean {
        if (!json.has("members")) return false

        val missing = MissingElements()
        getMissingFromModInfo(json, missing)

        val extractedFleet = extractFleetDataFromJson(json)
        val subMissing = MissingElements()
        val parsedFleet = validateAndCleanFleetData(extractedFleet, MissingElements(), settings = FleetSerialization.FleetSettings())

        if (parsedFleet.members.isEmpty()) {
            reportMissingElementsIfAny(subMissing, "Fleet was empty when pasting")
            return false
        }

        val dialog = PopUpUIDialog("Spawn Fleet in Campaign", addConfirmButton = true, addCancelButton = true)
        dialog.confirmButtonName = "Spawn Fleet"

        val memberCount = parsedFleet.members.size
        val officerCount = parsedFleet.members.count { it.personData != null }
        dialog.addParagraph(
            "Pasted fleet contains $memberCount member${if (memberCount != 1) "s" else ""}" +
                    if (officerCount > 0) " and $officerCount officer${if (officerCount != 1) "s" else ""}" else ""
        )

        val missingHullCount = parsedFleet.members.count { it.variantData == null || it.variantData.tags.contains(VariantLib.errorTag) }
        if (missingHullCount > 0)
            dialog.addParagraph("Fleet contains $missingHullCount hull${if (missingHullCount != 1) "s" else ""} from missing mods")



        dialog.addPadding(8f)

        dialog.addToggle("Set Aggression Doctrine", default = true)
        dialog.addToggle("Fight To The Last", default = true)
        dialog.addToggle("Include Officers", default = true)
        dialog.addToggle("Include Commander as Commander", default = true)
        dialog.addToggle("Include Commander as Officer", default = true)
        dialog.addToggle("Exclude Ships From Missing Mods", default = true)

        dialog.onConfirm { fields ->

            val settings = FleetSerialization.FleetSettings()
            settings.includeAggression = fields["Set Aggression Doctrine"] as Boolean
            settings.memberSettings.includeOfficer = fields["Include Officers"] as Boolean
            settings.includeCommanderSetFlagship = fields["Include Commander as Commander"] as Boolean
            settings.includeCommanderAsOfficer = fields["Include Commander as Officer"] as Boolean
            settings.excludeMembersWithMissingHullSpec = fields["Exclude Ships From Missing Mods"] as Boolean

            val fleet = Global.getFactory().createEmptyFleet(Factions.PIRATES, FleetTypes.TASK_FORCE, true)

            missing.add(buildFleetFull(extractedFleet, fleet.fleetData, settings))

            reportMissingElementsIfAny(missing)

            sector.playerFleet.containingLocation.spawnFleet(sector.playerFleet, 0f, 0f, fleet)
            Global.getSector().campaignUI.showInteractionDialog(fleet)
            if (fields["Fight To The Last"] as Boolean)
                fleet.memoryWithoutUpdate[MemFlags.FLEET_FIGHT_TO_THE_LAST] = true

            showMessage("Fleet from clipboard added to campaign")
        }

        initPopUpUI(dialog, 500f, 324f)

        return true
    }

    fun getAnyFromJson(json: JSONObject): Pair<Any?, MissingElements> {
        return when {
            json.has("skills") -> {
                // Officer
                val (person, missing) = getPersonFromJsonWithMissing(json)
                return Pair(person, missing)
            }

            json.has("variant") || json.has("officer") -> {
                // Fleet member
                return MemberSerialization.getMemberFromJsonWithMissing(json)
            }

            json.has("hullId") -> {
                // Variant
                return VariantSerialization.getVariantFromJsonWithMissing(json)
            }

            json.has("members") -> {
                // Fleet
                val fleet = Global.getFactory().createEmptyFleet(Factions.INDEPENDENT, FleetTypes.TASK_FORCE, true)
                val missing = getFleetFromJson(json, fleet.fleetData)
                return Pair(fleet, missing)
            }

            else -> {
                Pair(null, MissingElements())
            }
        }
    }

    fun fleetPaste(
        sector: SectorAPI,
        json: JSONObject
    ) {
        val playerFleet = sector.playerFleet.fleetData

        var uiShowsSubmarketFleet = false

        val fleetToAddTo = getViewedFleetInFleetPanel() ?: playerFleet
        if (fleetToAddTo !== playerFleet)
            uiShowsSubmarketFleet = true

        val missing = MissingElements()

        when {
            json.has("skills") -> {
                // Officer
                val (person, subMissing) = getPersonFromJsonWithMissing(json)
                missing.add(subMissing)

                if (randomPastedCosmetics) {
                    randomizePersonCosmetics(person, playerFleet.fleet.faction)
                }
                playerFleet.addOfficer(person)
                showMessage("Added officer to fleet")
            }

            json.has("hullId") -> {
                // Variant
                val (variant, subMissing) = VariantSerialization.getVariantFromJsonWithMissing(json)
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

            json.has("variant") || json.has("officer") -> {
                // Fleet member
                val (member, subMissing) = MemberSerialization.getMemberFromJsonWithMissing(json)
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

            json.has("members") -> {
                // Fleet

                val extractedFleet = extractFleetDataFromJson(json)
                val subMissing = MissingElements()
                val parsedFleet = validateAndCleanFleetData(extractedFleet, subMissing, settings = FleetSerialization.FleetSettings())

                if (parsedFleet.members.isEmpty()) {
                    reportMissingElementsIfAny(subMissing, "Fleet was empty when pasting")
                    return
                }

                val dialog = PopUpUIDialog("Paste Fleet into Player Fleet", addCloseButton = true)

                val memberCount = parsedFleet.members.size
                val officerCount = parsedFleet.members.count { it.personData != null }
                dialog.addParagraph(
                    "Pasted fleet contains $memberCount member${if (memberCount != 1) "s" else ""}" +
                            if (officerCount > 0) " and $officerCount officer${if (officerCount != 1) "s" else ""}" else ""
                )

                val missingHullCount = parsedFleet.members.count { it.variantData == null || it.variantData.tags.contains(VariantLib.errorTag) }
                if (missingHullCount > 0)
                    dialog.addParagraph("Fleet contains $missingHullCount hull${if (missingHullCount != 1) "s" else ""} from missing mods")

                dialog.addPadding(8f)

                dialog.addButton("Append to Player Fleet") { fields ->

                    val fleet = Global.getFactory().createEmptyFleet(Factions.INDEPENDENT, FleetTypes.TASK_FORCE, false)
                    val addMissing = buildFleetFull(
                        extractedFleet, fleet.fleetData,
                        settings = FleetSerialization.FleetSettings().apply {
                            excludeMembersWithMissingHullSpec = fields["Exclude Ships From Missing Mods"] as Boolean
                            memberSettings.includeOfficer = fields["Include Officers"] as Boolean
                            includeCommanderAsOfficer = fields["Include Commander as Officer"] as Boolean
                        }
                    )

                    fleet.fleetData.membersListCopy.forEach { member ->
                        playerFleet.addFleetMember(member)

                        val captain = member.captain
                        if (!captain.isDefault && !captain.isAICore) {
                            playerFleet.addOfficer(captain)
                        }
                    }

                    reportMissingElementsIfAny(addMissing)

                    if (fields["Fulfill cargo, fuel, crew, and repair for fleet"] as Boolean)
                        fulfillPlayerFleet()
                }
                dialog.addPadding(24f)

                dialog.addButton("Replace Player Fleet") { fields ->
                    val settings = FleetSerialization.FleetSettings()
                    settings.memberSettings.includeOfficer = fields["Include Officers"] as Boolean
                    settings.excludeMembersWithMissingHullSpec = fields["Exclude Ships From Missing Mods"] as Boolean
                    settings.includeCommanderAsOfficer = fields["Include Commander as Officer"] as Boolean
                    settings.includeAggression = fields["Set Aggression Doctrine"] as Boolean

                    val replaceMissing = replacePlayerFleetWith(
                        json,
                        (fields["Replace Player with Commander"] as Boolean && settings.includeCommanderAsOfficer),
                        settings
                    )

                    reportMissingElementsIfAny(replaceMissing)

                    if (fields["Fulfill cargo, fuel, crew, and repair for fleet"] as Boolean)
                        fulfillPlayerFleet()
                }
                dialog.addToggle("Set Aggression Doctrine", default = true)
                dialog.addToggle("Replace Player with Commander", default = false)

                dialog.addPadding(48f)
                dialog.addParagraph("Additional Settings:")
                dialog.addToggle("Include Officers", default = true)
                dialog.addToggle("Include Commander as Officer", default = true)
                dialog.addToggle("Exclude Ships From Missing Mods", default = false)
                dialog.addToggle("Fulfill cargo, fuel, crew, and repair for fleet", default = true)


                initPopUpUI(dialog, 500f, 380f)
            }

            else -> {
                showMessage("No valid data found in clipboard", Color.YELLOW)
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

    //TODO, an option that enables grabbing things from your markets. Items in storage, ships in storage, items in industries. Then putting it all in an abandoned station somewhere in the new sector on load.
    fun createPlayerSaveJson(
        handleCargo: Boolean = true,
        handleRelations: Boolean = true,
        handleKnownBlueprints: Boolean = true,
        handlePlayer: Boolean = true,
        handleFleet: Boolean = true,
        handleCredits: Boolean = true,
        handleKnownHullmods: Boolean = true,
        handleOfficers: Boolean = true
    ): JSONObject {
        val json = JSONObject()
        val sector = Global.getSector()

        if (handleCargo) {
            val cargoJson = saveCargoToJson(sector.playerFleet.cargo.stacksCopy)
            json.put("cargo", cargoJson)
        }

        if (handleRelations) {
            val relationsJson = JSONObject()
            for (faction in sector.allFactions) {
                relationsJson.put(faction.id, faction.relToPlayer.rel)
            }
            json.put("relations", relationsJson)
        }

        if (handleKnownBlueprints) {
            val playerFaction = sector.playerFaction

            json.put("shipBlueprints", JSONArray(playerFaction.knownShips))
            json.put("fighterBlueprints", JSONArray(playerFaction.knownFighters))
            json.put("weaponBlueprints", JSONArray(playerFaction.knownWeapons))
        }

        if (handlePlayer) {
            val playerJson = savePersonToJson(sector.playerPerson)
            playerJson.put("storyPoints", sector.playerStats.storyPoints)
            json.put("player", playerJson)
        }

        if (handleFleet) {
            val fleetJson = saveFleetToJson(
                sector.playerFleet,
                FleetSerialization.FleetSettings().apply {
                    includeCommanderSetFlagship = false
                    includeCommanderAsOfficer = false
                    memberSettings.includeOfficer = handleOfficers
                    includeIdleOfficers = handleOfficers
                }

            )
            json.put("fleet", fleetJson)
        }

        if (handleCredits) {
            val credits = sector.playerFleet.cargo.credits
            json.put("credits", credits.get())
        }

        if (handleKnownHullmods) {
            val hullMods = sector.characterData.hullMods
            json.put("knownHullMods", JSONArray(hullMods))
        }

        return json
    }

    data class CompiledSaved(
        var fleet: CampaignFleetAPI? = null,
        var aggressionDoctrine: Int = -1,
        var player: PersonAPI? = null,
        var cargo: CargoAPI? = null,
        var relations: Map<String, Float>? = null,
        var shipBlueprints: List<String> = listOf(),
        var fighterBlueprints: List<String> = listOf(),
        var weaponBlueprints: List<String> = listOf(),
        var hullMods: List<String>? = null
    )

    fun compilePlayerSaveJson(
        json: JSONObject
    ): Pair<CompiledSaved, MissingElementsExtended> {
        val missing = MissingElementsExtended()
        val compiled = CompiledSaved()

        val sector = Global.getSector() ?: return compiled to missing

        val cargo = Global.getFactory().createCargo(true)

        if (json.has("cargo")) {
            try {
                missing.add(getCargoFromJson(json.getJSONArray("cargo"), cargo))

                compiled.cargo = cargo
            } catch (e: Exception) {
                showError("Failed to load cargo", e)
            }
        }

        if (json.has("credits")) {
            try {
                cargo.credits.add(json.optDouble("credits", 0.0).toFloat())
            } catch (e: Exception) {
                showError("Failed to load credits", e)
            }
        }

        if (json.has("relations")) {
            try {
                val relationsMap: MutableMap<String, Float> = mutableMapOf()

                val relations = json.getJSONObject("relations")
                for (factionItem in sector.allFactions) {
                    if (relations.has(factionItem.id)) {
                        relationsMap[factionItem.id] = relations.optFloat(factionItem.id, 0f)
                    }
                }
                compiled.relations = relationsMap
            } catch (e: Exception) {
                showError("Failed to load relations", e)
            }
        }

        if (json.has("player")) {
            try {
                val playerJson = json.getJSONObject("player")
                val (loadedPlayer, personMissing) = getPersonFromJsonWithMissing(playerJson)
                missing.add(personMissing)

                compiled.player = loadedPlayer
            } catch (e: Exception) {
                showError("Failed to load player", e)
            }
        }

        if (json.has("fleet")) {
            try {
                val fleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, FleetTypes.TASK_FORCE, false)
                val fleetJson = json.getJSONObject("fleet")
                val fleetMissing = getFleetFromJson(
                    fleetJson,
                    fleet,
                    FleetSerialization.FleetSettings().apply {
                        includeCommanderSetFlagship = false
                        includeCommanderAsOfficer = false
                        includeAggression = false
                    })
                missing.add(fleetMissing)
                compiled.aggressionDoctrine = fleetJson.optInt("aggression_doctrine", 2)

                compiled.fleet = fleet
            } catch (e: Exception) {
                showError("Failed to load fleet", e)
            }
        }

        if (json.has("knownHullMods")) {
            val hullModsJson = json.optJSONArray("knownHullMods") ?: JSONArray()
            val hullMods = mutableListOf<String>()
            for (i in 0 until hullModsJson.length()) {
                val modId = hullModsJson.optString(i, null) ?: continue
                val spec = runCatching { Global.getSettings().getHullModSpec(modId) }.getOrNull()
                if (spec == null) {
                    missing.hullModIdsKnown.add(modId)
                    continue
                }
                if (!spec.isAlwaysUnlocked && !spec.isHidden && !spec.isHiddenEverywhere) {
                    hullMods.add(modId)
                }
            }

            compiled.hullMods = hullMods
        }

        if (json.has("shipBlueprints") ||
            json.has("fighterBlueprints") ||
            json.has("weaponBlueprints")
        ) {
            val shipBlueprintsJson = json.optJSONArray("shipBlueprints") ?: JSONArray()
            val shipBlueprints = mutableListOf<String>()
            val fighterBlueprintsJson = json.optJSONArray("fighterBlueprints") ?: JSONArray()
            val fighterBlueprints = mutableListOf<String>()
            val weaponBlueprintsJson = json.optJSONArray("weaponBlueprints") ?: JSONArray()
            val weaponBlueprints = mutableListOf<String>()

            for (i in 0 until shipBlueprintsJson.length()) {
                val id = shipBlueprintsJson.optString(i, null) ?: continue
                val spec = runCatching { Global.getSettings().getHullSpec(id) }.getOrNull()
                if (spec != null) {
                    shipBlueprints.add(id)
                } else {
                    missing.blueprintHullIds.add(id)
                }
            }

            for (i in 0 until fighterBlueprintsJson.length()) {
                val id = fighterBlueprintsJson.optString(i, null) ?: continue
                val spec = runCatching { Global.getSettings().getFighterWingSpec(id) }.getOrNull()
                if (spec != null) {
                    fighterBlueprints.add(id)
                } else {
                    missing.blueprintWingIds.add(id)
                }
            }

            for (i in 0 until weaponBlueprintsJson.length()) {
                val id = weaponBlueprintsJson.optString(i, null) ?: continue
                val spec = runCatching { Global.getSettings().getWeaponSpec(id) }.getOrNull()
                if (spec != null) {
                    weaponBlueprints.add(id)
                } else {
                    missing.blueprintWeaponIds.add(id)
                }
            }

            compiled.shipBlueprints = shipBlueprints
            compiled.fighterBlueprints = fighterBlueprints
            compiled.weaponBlueprints = weaponBlueprints
        }

        return compiled to missing
    }

    fun loadPlayerCompiledSave(
        compiled: CompiledSaved,
        handleCargo: Boolean = true,
        handleRelations: Boolean = true,
        handleKnownBlueprints: Boolean = true,
        handlePlayer: Boolean = true,
        handleFleet: Boolean = true,
        handleCredits: Boolean = true,
        handleKnownHullmods: Boolean = true,
        handleOfficers: Boolean = true
    ) {
        val sector = Global.getSector()

        val cargo = sector.playerFleet.cargo

        if (handleRelations && compiled.relations != null) {
            for ((factionId, relation) in compiled.relations) {
                val faction = sector.allFactions.find { it.id == factionId } ?: continue
                faction.relToPlayer.rel = relation
            }
        }

        if (handlePlayer && compiled.player != null) {
            val currentPlayer = sector.playerPerson

            currentPlayer.stats = compiled.player!!.stats
            currentPlayer.name = compiled.player!!.name
            currentPlayer.portraitSprite = compiled.player!!.portraitSprite
        }

        if (handleCargo && compiled.cargo != null) {
            cargo.addAll(compiled.cargo)
        }

        if (handleCredits && compiled.cargo != null) {
            cargo.credits.add(compiled.cargo!!.credits.get())
        }

        if (handleFleet && compiled.fleet != null) {
            replacePlayerFleetWith(
                compiled.fleet!!, replacePlayer = false, aggression = compiled.aggressionDoctrine,
                settings = FleetSerialization.FleetSettings().apply {
                    memberSettings.includeOfficer = handleOfficers
                    includeIdleOfficers = handleOfficers
                    includeCommanderSetFlagship = false
                    includeCommanderAsOfficer = false
                    includeAggression = false
                })
        }

        if (handleKnownHullmods && compiled.hullMods != null) {
            val sector = Global.getSector()
            for (modId in compiled.hullMods!!) {
                sector.playerFaction.addKnownHullMod(modId)
                sector.characterData.addHullMod(modId)
            }
        }

        if (handleKnownBlueprints) {
            for (id in compiled.shipBlueprints) {
                sector.playerFaction.addKnownShip(id, true)
            }
            for (id in compiled.fighterBlueprints) {
                sector.playerFaction.addKnownFighter(id, true)
            }
            for (id in compiled.weaponBlueprints) {
                sector.playerFaction.addKnownWeapon(id, true)
            }
        }
    }

    fun loadPlayerSaveJson(
        json: JSONObject,
        handleCargo: Boolean = true,
        handleRelations: Boolean = true,
        handleKnownBlueprints: Boolean = true,
        handlePlayer: Boolean = true,
        handleFleet: Boolean = true,
        handleCredits: Boolean = true,
        handleKnownHullmods: Boolean = true,
        handleOfficers: Boolean = true
    ): MissingElements {
        val (compiled, missing) = compilePlayerSaveJson(json)

        loadPlayerCompiledSave(
            compiled,
            handleCargo,
            handleRelations,
            handleKnownBlueprints,
            handlePlayer,
            handleFleet,
            handleCredits,
            handleKnownHullmods,
            handleOfficers
        )

        if (handleCargo) {

        }

        return missing
    }


    fun getMissingFromModInfo(json: JSONObject, missingElements: MissingElements) {
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
                if (!hasMod) {
                    missingElements.gameMods.add(Triple(modSpecId, modSpecName, modSpecVersion))
                }
            }
        }
    }
}