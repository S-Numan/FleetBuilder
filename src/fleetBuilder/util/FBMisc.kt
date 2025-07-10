package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.codex2.CodexDialog
import fleetBuilder.config.ModSettings.randomPastedCosmetics
import fleetBuilder.persistence.CargoSerialization.getCargoFromJson
import fleetBuilder.persistence.CargoSerialization.saveCargoToJson
import fleetBuilder.persistence.FleetSerialization
import fleetBuilder.persistence.FleetSerialization.getFleetFromJson
import fleetBuilder.persistence.FleetSerialization.saveFleetToJson
import fleetBuilder.persistence.MemberSerialization
import fleetBuilder.persistence.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.PersonSerialization
import fleetBuilder.persistence.PersonSerialization.getPersonFromJsonWithMissing
import fleetBuilder.persistence.PersonSerialization.savePersonToJson
import fleetBuilder.persistence.VariantSerialization
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
import fleetBuilder.util.ClipboardUtil.setClipboardText
import fleetBuilder.util.DisplayMessage.showError
import fleetBuilder.util.DisplayMessage.showMessage
import fleetBuilder.util.ReflectionMisc.getCodexEntryParam
import fleetBuilder.util.ReflectionMisc.getViewedFleetInFleetPanel
import fleetBuilder.util.ReflectionMisc.updateFleetPanelContents
import fleetBuilder.variants.MissingElements
import org.json.JSONArray
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.optFloat
import org.lwjgl.input.Keyboard
import java.awt.Color
import java.util.*

object FBMisc {

    fun isMouseHoveringOverComponent(component: UIComponentAPI): Boolean {
        val mouseX = Global.getSettings().mouseX
        val mouseY = Global.getSettings().mouseY

        val x = component.position.x
        val y = component.position.y
        val width = component.position.width
        val height = component.position.height

        if (mouseX >= x && mouseX <= x + width &&
            mouseY >= y && mouseY <= y + height
        ) {
            return true
        }

        return false
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

        reportMissingElements(missingElements)

        return fleet
    }

    fun reportMissingElements(
        missingElements: MissingElements,
        defaultShortMessage: String = "HAD MISSING ELEMENTS: see console for more details"
    ) {
        val fullMessage = getMissingElementsString(missingElements)
        if (fullMessage.isNotBlank()) {
            showError(defaultShortMessage, fullMessage)
        }
    }

    fun getMissingElementsString(missingElements: MissingElements): String {
        if (missingElements.hasMissing()) {
            val missingMessages = mutableListOf<String>()

            fun printIfNotEmpty(label: String, items: Collection<*>) {
                if (items.isNotEmpty()) {
                    val message = "$label: ${items.joinToString()}"
                    missingMessages.add(message)
                }
            }

            printIfNotEmpty("Required mods", missingElements.gameMods)
            printIfNotEmpty("Missing hulls", missingElements.hullIds)
            printIfNotEmpty("Missing weapons", missingElements.weaponIds)
            printIfNotEmpty("Missing wings", missingElements.wingIds)
            printIfNotEmpty("Missing hullmods", missingElements.hullModIds)
            printIfNotEmpty("Missing items", missingElements.itemIds)
            printIfNotEmpty("Missing skills", missingElements.skillIds)

            return missingMessages.joinToString(separator = "\n")
        }
        return ""
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
        val fleet = Global.getFactory().createEmptyFleet(Factions.PIRATES, FleetTypes.TASK_FORCE, true)
        val missingElements = getFleetFromJson(json, fleet)

        reportMissingElements(missingElements)
        if (fleet.fleetSizeCount == 0) {
            //showMessage("Failed to create fleet from clipboard", Color.YELLOW)
            return false
        }

        sector.playerFleet.containingLocation.spawnFleet(sector.playerFleet, 0f, 0f, fleet)
        Global.getSector().campaignUI.showInteractionDialog(fleet)
        fleet.memoryWithoutUpdate[MemFlags.FLEET_FIGHT_TO_THE_LAST] = true
        showMessage("Fleet from clipboard added to campaign")
        return true
    }

    fun getAnyFromJson(json: JSONObject): Pair<Any?, MissingElements> {
        return when {
            json.has("skills") -> {
                // Officer
                val (person, missing) = PersonSerialization.getPersonFromJsonWithMissing(json)
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
                val missing = FleetSerialization.getFleetFromJson(json, fleet)
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

        val (element, missing) = getAnyFromJson(json)

        when (element) {
            is PersonAPI -> {
                if (randomPastedCosmetics) {
                    randomizePersonCosmetics(element, playerFleet.fleet.faction)
                }
                playerFleet.addOfficer(element)
                showMessage("Added officer to fleet")
            }

            is ShipVariantAPI -> {
                if (missing.hullIds.size > 1) {
                    reportMissingElements(missing, "Could not find hullId when pasting variant")
                    return
                }

                val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, element)

                if (randomPastedCosmetics)
                    randomizeMemberCosmetics(member, fleetToAddTo)

                fleetToAddTo.addFleetMember(member)

                showMessage("Added variant of hull '${element.hullSpec.hullName}' to ${if (uiShowsSubmarketFleet) "submarket" else "fleet"}", element.hullSpec.hullName, Misc.getHighlightColor())

                updateFleetPanelContents()
            }

            is FleetMemberAPI -> {
                if (missing.hullIds.size > 1) {
                    reportMissingElements(missing, "Could not find hullId when pasting member")
                    return
                }

                if (randomPastedCosmetics)
                    randomizeMemberCosmetics(element, fleetToAddTo)

                fleetToAddTo.addFleetMember(element)
                if (!element.captain.isDefault && !element.captain.isAICore && !uiShowsSubmarketFleet)
                    fleetToAddTo.addOfficer(element.captain)

                val shipName = element.hullSpec.hullName
                val message = buildString {
                    append("Added '${shipName}' to ${if (uiShowsSubmarketFleet) "submarket" else "fleet"}")
                    if (!element.captain.isDefault) append(", with an officer")
                }

                showMessage(message, shipName, Misc.getHighlightColor())

                updateFleetPanelContents()
            }

            is CampaignFleetAPI -> {
                //if () {
                //    reportMissingElements(missing, "Fleet was empty when pasting")
                //    return
                //}

                showMessage("Pasting a fleet into yours is currently Unimplemented. Try the ConsoleCommand \'replacefleet\'", Color.YELLOW)
            }

            else -> {
                showMessage("No valid data found in clipboard", Color.YELLOW)
            }
        }

        reportMissingElements(missing)
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
        val missing = MissingElements()

        val sector = Global.getSector() ?: return missing

        val playerFleet = sector.playerFleet ?: return missing
        val cargo = playerFleet.cargo
        val faction = sector.playerFaction ?: return missing

        if (handleCargo && json.has("cargo")) {
            try {
                missing.add(getCargoFromJson(json.getJSONArray("cargo"), cargo))
            } catch (e: Exception) {
                showError("Failed to load cargo", e)
            }
        }

        if (handleRelations && json.has("relations")) {
            try {
                val relations = json.getJSONObject("relations")
                for (factionItem in sector.allFactions) {
                    if (relations.has(factionItem.id)) {
                        factionItem.relToPlayer.rel = relations.optFloat(factionItem.id, factionItem.relToPlayer.rel)
                    }
                }
            } catch (e: Exception) {
                showError("Failed to load relations", e)
            }
        }

        if (handlePlayer && json.has("player")) {
            try {
                val playerJson = json.getJSONObject("player")
                val (loadedPlayer, personMissing) = getPersonFromJsonWithMissing(playerJson)
                missing.add(personMissing)

                val currentPlayer = sector.playerPerson

                currentPlayer.stats = loadedPlayer.stats
                currentPlayer.stats.storyPoints = playerJson.optInt("storyPoints", currentPlayer.stats.storyPoints)
                currentPlayer.name = loadedPlayer.name
                currentPlayer.portraitSprite = loadedPlayer.portraitSprite
            } catch (e: Exception) {
                showError("Failed to load player", e)
            }
        }

        if (handleFleet && json.has("fleet")) {
            try {
                missing.add(
                    getFleetFromJson(
                        json.getJSONObject("fleet"),
                        playerFleet,
                        FleetSerialization.FleetSettings().apply {
                            memberSettings.includeOfficer = handleOfficers
                            includeIdleOfficers = handleOfficers
                            includeCommanderSetFlagship = false
                        }
                    )
                )
            } catch (e: Exception) {
                showError("Failed to load fleet", e)
            }
        }

        if (handleCredits && json.has("credits")) {
            try {
                cargo.credits.add(json.optDouble("credits", 0.0).toFloat())
            } catch (e: Exception) {
                showError("Failed to load credits", e)
            }
        }

        if (handleKnownHullmods && json.has("knownHullMods")) {
            val hullMods = json.getJSONArray("knownHullMods")
            for (i in 0 until hullMods.length()) {
                val modId = hullMods.optString(i, null) ?: continue
                val spec = runCatching { Global.getSettings().getHullModSpec(modId) }.getOrNull()
                if (spec == null) {
                    missing.hullModIds.add(modId)
                    continue
                }
                if (!spec.isAlwaysUnlocked && !spec.isHidden && !spec.isHiddenEverywhere) {
                    sector.characterData.addHullMod(modId)
                }
            }
        }

        if (handleKnownBlueprints &&
            json.has("shipBlueprints") &&
            json.has("fighterBlueprints") &&
            json.has("weaponBlueprints")
        ) {
            val shipBlueprints = json.getJSONArray("shipBlueprints")
            val fighterBlueprints = json.getJSONArray("fighterBlueprints")
            val weaponBlueprints = json.getJSONArray("weaponBlueprints")

            for (i in 0 until shipBlueprints.length()) {
                val id = shipBlueprints.optString(i, null) ?: continue
                val spec = runCatching { Global.getSettings().getHullSpec(id) }.getOrNull()
                if (spec != null) {
                    faction.addKnownShip(id, true)
                } else {
                    missing.hullIds.add(id)
                }
            }

            for (i in 0 until fighterBlueprints.length()) {
                val id = fighterBlueprints.optString(i, null) ?: continue
                val spec = runCatching { Global.getSettings().getFighterWingSpec(id) }.getOrNull()
                if (spec != null) {
                    faction.addKnownFighter(id, true)
                } else {
                    missing.wingIds.add(id)
                }
            }

            for (i in 0 until weaponBlueprints.length()) {
                val id = weaponBlueprints.optString(i, null) ?: continue
                val spec = runCatching { Global.getSettings().getWeaponSpec(id) }.getOrNull()
                if (spec != null) {
                    faction.addKnownWeapon(id, true)
                } else {
                    missing.weaponIds.add(id)
                }
            }
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