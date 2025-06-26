package fleetBuilder.util

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.codex2.CodexDetailPanel
import com.fs.starfarer.codex2.CodexDialog
import com.fs.starfarer.title.TitleScreenState
import com.fs.state.AppDriver
import fleetBuilder.util.listeners.ShipOfficerChangeEvents
import fleetBuilder.config.ModSettings.commandShuttleId
import fleetBuilder.config.ModSettings.randomPastedCosmetics
import fleetBuilder.hullMods.CommanderShuttleListener
import fleetBuilder.persistence.CargoSerialization.getCargoFromJson
import fleetBuilder.persistence.CargoSerialization.saveCargoToJson
import fleetBuilder.persistence.FleetSerialization.getFleetFromJson
import fleetBuilder.persistence.FleetSerialization.saveFleetToJson
import fleetBuilder.persistence.MemberSerialization.getMemberFromJsonWithMissing
import fleetBuilder.persistence.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.OfficerSerialization.getOfficerFromJson
import fleetBuilder.persistence.OfficerSerialization.saveOfficerToJson
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
import fleetBuilder.variants.MissingElements
import org.apache.log4j.Level
import org.json.JSONArray
import org.json.JSONObject
import org.lazywizard.console.Console
import org.lazywizard.lazylib.ext.json.optFloat
import org.lwjgl.input.Keyboard
import starficz.ReflectionUtils.get
import starficz.ReflectionUtils.getMethodsMatching
import starficz.ReflectionUtils.invoke
import java.awt.Color
import java.util.*

object MISC {
    //Short is displayed to the user, full is put in the log/console.
    fun showError(short: String, full: String, e: Exception? = null) {

        showMessage(short, Color.RED)

        if (Global.getSettings().modManager.isModEnabled("lw_console")) {
            if (e != null) {
                Console.showException(full, e)
            } else {
                Console.showMessage(full, Level.ERROR)
            }

            //CampaignEngine.getInstance().campaignUI.messageList.messages.forEach { message -> }//TODO, remove console message in bottom left messages.
            //Global.getSector().campaignUI.messageDisplay.removeMessage(short)
        } else {
            Global.getLogger(this.javaClass).error(full)
        }

        Global.getSoundPlayer().playUISound("ui_selection_cleared", 1f, 1f)
    }

    fun showError(short: String, e: Exception? = null) {
        showError(short, short, e)
    }

    fun showMessage(short: String, color: Color?, highlight: String, highlightColor: Color = Misc.getHighlightColor()) {
        var defaultColor = color

        val gameState = Global.getCurrentState()
        if (gameState == GameState.CAMPAIGN) {
            if (defaultColor == null)
                defaultColor = Misc.getTooltipTitleAndLightHighlightColor()

            val ui = Global.getSector().campaignUI
            ui.messageDisplay.addMessage(short, defaultColor, highlight, highlightColor)
        } else if (gameState == GameState.COMBAT) {
            if (defaultColor == null)
                defaultColor = Misc.getTextColor()

            val engine = Global.getCombatEngine()
            val ui = engine.combatUI

            val highlightIndex = short.indexOf(highlight)
            if (highlight.isEmpty() || highlightIndex == -1) { // Highlight text not found.
                ui.addMessage(1, defaultColor, short)
            } else {
                val before = short.substring(0, highlightIndex)
                val after = short.substring(highlightIndex + highlight.length)

                ui.addMessage(
                    0,
                    defaultColor, before,
                    highlightColor, highlight,
                    defaultColor, after
                )
            }
        } else if (gameState == GameState.TITLE) {
            //TEMP
            val state = AppDriver.getInstance().currentState
            state.invoke(
                "showMessageDialog",
                "$short\nTemporary dialog for TitleScreen messages, this will be improved later."
            )

            Global.getSoundPlayer().playUISound("ui_selection_cleared", 1f, 1f)
        }
    }

    fun showMessage(short: String, color: Color? = null) {
        showMessage(short, color, "")
    }

    fun showMessage(short: String, highlight: String, highlightColor: Color = Misc.getHighlightColor()) {
        showMessage(short, null, highlight, highlightColor)
    }
    /*
    val stackTrace = Exception().stackTrace
        var stackTraceString = ""
        for(stack in stackTrace) {
            stackTraceString += stack
        }
        Global.getLogger(this.javaClass).error("\n" + stackTraceString)
     */

    fun createFleetFromJson(
        json: JSONObject,
        includeOfficers: Boolean = true,
        includeCommander: Boolean = true,
        includeNoOfficerPersonality: Boolean = true,
        setFlagship: Boolean = true,
        faction: String = Factions.INDEPENDENT
    ): CampaignFleetAPI {
        val fleet = Global.getFactory().createEmptyFleet(faction, FleetTypes.TASK_FORCE, true)

        getFleetFromJsonComplainIfMissing(
            json,
            fleet,
            includeOfficers,
            includeCommander,
            includeNoOfficerPersonality,
            setFlagship
        )

        return fleet
    }

    fun getFleetFromJsonComplainIfMissing(
        json: JSONObject,
        fleet: CampaignFleetAPI,
        includeOfficers: Boolean = true,
        includeCommander: Boolean = true,
        includeNoOfficerPersonality: Boolean = true,
        setFlagship: Boolean = true
    ) {
        val missingElements = getFleetFromJson(
            json,
            fleet,
            includeOfficers = includeOfficers,
            includeCommander = includeCommander,
            includeNoOfficerPersonality = includeNoOfficerPersonality,
            setFlagship = setFlagship
        )

        if (missingElements.hasMissing()) {
            showError("FLEET HAD MISSING ELEMENTS")

            fun printIfNotEmpty(label: String, items: Collection<*>) {
                if (items.isNotEmpty()) {
                    Console.showMessage("$label: ${items.joinToString()}")
                }
            }

            printIfNotEmpty("Required mods", missingElements.gameMods)
            printIfNotEmpty("Missing hulls", missingElements.hullIds)
            printIfNotEmpty("Missing weapons", missingElements.weaponIds)
            printIfNotEmpty("Missing wings", missingElements.wingIds)
            printIfNotEmpty("Missing hullmods", missingElements.hullModIds)
        }
    }

    fun createErrorVariant(displayName: String = ""): ShipVariantAPI {
        var tempVariant: ShipVariantAPI? = null
        try {
            tempVariant = Global.getSettings().getVariant(Global.getSettings().getString("errorShipVariant"))
        } catch (_: Exception) {
        }
        if (tempVariant == null)
            tempVariant = Global.getSettings().getVariant(Global.getSettings().allVariantIds.first())
        if (tempVariant == null) throw Exception("No variants anywhere? How?")

        tempVariant = tempVariant.clone()

        tempVariant.setVariantDisplayName("ERROR:$displayName")

        return tempVariant
    }

    fun getCoreUI(): UIPanelAPI? {
        val state = AppDriver.getInstance().currentState
        if (state is CampaignState) {
            return (state.invoke("getEncounterDialog")?.let { dialog ->
                dialog.invoke("getCoreUI") as? UIPanelAPI
            } ?: state.invoke("getCore") as? UIPanelAPI)
        } else if (state is TitleScreenState) {
            return state.invoke("getScreenPanel") as? UIPanelAPI
        }
        return null
    }

    fun getBorderContainer(): UIPanelAPI? {
        val newCoreUI = getCoreUI() ?: return null

        return newCoreUI.findChildWithMethod("setBorderInsetLeft") as? UIPanelAPI
    }

    fun getRefitTab(): UIPanelAPI? {
        val borderContainer = getBorderContainer() ?: return null

        return borderContainer.findChildWithMethod("goBackToParentIfNeeded") as? UIPanelAPI
    }

    fun getFleetTab(): UIPanelAPI? {
        val borderContainer = getBorderContainer() ?: return null

        return borderContainer.findChildWithMethod("getFleetPanel") as? UIPanelAPI
    }

    fun getCodexDialog(): CodexDialog? {
        var codex: CodexDialog?

        val gameState = Global.getCurrentState()
        val state = AppDriver.getInstance().currentState

        //F2 press, and in some other places
        val codexOverlayPanel = state.invoke("getOverlayPanelForCodex") as? UIPanelAPI?
        codex = codexOverlayPanel?.findChildWithMethod("getCurrentSnapshot") as? CodexDialog?

        if (codex == null) {
            if (gameState == GameState.CAMPAIGN) {

                //Button press in the fleet screen
                val coreUI = getCoreUI()
                codex = coreUI?.findChildWithMethod("getCurrentSnapshot") as? CodexDialog?

            } else if (gameState == GameState.COMBAT || gameState == GameState.TITLE) {
                val state = AppDriver.getInstance().currentState

                //Combat F2 with ship selected
                if (state.getMethodsMatching("getRibbon").isNotEmpty()) {
                    val ribbon = state.invoke("getRibbon") as? UIPanelAPI?
                    val temp = ribbon?.invoke("getParent") as? UIPanelAPI?
                    codex = temp?.findChildWithMethod("getCurrentSnapshot") as? CodexDialog?
                }
            }
        }
        return codex
    }

    fun getCodexEntryParam(codex: CodexDialog): Any? {
        val codexDetailPanel = codex.get(type = CodexDetailPanel::class.java) ?: return null
        val codexEntry = codexDetailPanel.get(name = "plugin") ?: return null

        return codexEntry.invoke("getParam")
    }

    fun addParamEntryToFleet(sector: SectorAPI, param: Any) {
        val shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
        val alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)
        val ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)

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

    fun campaignPaste(
        sector: SectorAPI,
        json: JSONObject
    ): Boolean {
        val fleet = MISC.createFleetFromJson(json, faction = Factions.PIRATES)
        if (fleet.fleetSizeCount == 0) {
            showMessage("Failed to create fleet from clipboard", Color.RED)
            return false
        }

        sector.playerFleet.containingLocation.spawnFleet(sector.playerFleet, 0f, 0f, fleet)
        Global.getSector().campaignUI.showInteractionDialog(fleet)
        fleet.memoryWithoutUpdate[MemFlags.FLEET_FIGHT_TO_THE_LAST] = true
        showMessage("Fleet from clipboard added to campaign")
        return true
    }

    fun fleetPaste(
        sector: SectorAPI,
        json: JSONObject
    ): Boolean {
        return when {
            json.has("skills") -> {
                // Officer
                addOfficerToFleet(json, sector.playerFleet.fleetData, randomPastedCosmetics)
                showMessage("Added officer to fleet")
                true
            }

            json.has("variant") || json.has("officer") -> {
                // Fleet member
                val (member, _) = addMemberToFleet(json, sector.playerFleet.fleetData, randomPastedCosmetics)
                if (member == null) {
                    showMessage("Failed to create member from clipboard", Color.RED)
                    return false
                }

                if (!member.captain.isDefault) {
                    sector.playerFleet.fleetData.addOfficer(member.captain)
                }

                val shipName = member.hullSpec.hullName
                val message = buildString {
                    append("Added '${shipName}' to fleet")
                    if (!member.captain.isDefault) append(", with an officer")
                }

                showMessage(message, shipName, Misc.getHighlightColor())
                true
            }

            json.has("hullId") -> {
                val wrappedJson = JSONObject().put("variant", json)
                val (member, missing) = addMemberToFleet(
                    wrappedJson,
                    sector.playerFleet.fleetData,
                    randomPastedCosmetics
                )
                if (member == null) return false

                val shipName = member.hullSpec.hullName
                showMessage("Added '$shipName' to fleet", shipName, Misc.getHighlightColor())
                true
            }

            else -> {
                showMessage("No valid data found in clipboard", Color.RED)
                false
            }
        }
    }

    fun updateFleetPanelContents() {
        if (Global.getSector().campaignUI.getActualCurrentTab() != CoreUITabId.FLEET) return

        var fleetPanel: UIPanelAPI? = null
        try {
            val fleetTab = getFleetTab()
            if (fleetTab != null)
                fleetPanel = fleetTab.invoke("getFleetPanel") as? UIPanelAPI
        } catch (_: Exception) {
        }
        fleetPanel?.invoke("updateListContents")
    }

    fun onGameLoad(newGame: Boolean) {
        val sector = Global.getSector()

        if (Global.getSector().memoryWithoutUpdate.getBoolean("\$FB_hadCommandShuttle")) {
            addPlayerShuttle()

            sector.addTransientScript(commanderShuttleListener)
            sector.addTransientListener(commanderShuttleListener)
        }

        ShipOfficerChangeEvents.addListener { change ->
            //Remove commandShuttle if was piloted by player and is no longer
            if (change.member.variant.hasHullMod(commandShuttleId)
                && change.previous != null && change.previous.isPlayer
            ) {
                Global.getSector().playerFleet.fleetData.removeFleetMember(change.member)
                updateFleetPanelContents()
            }
        }

        //ShipOfficerChangeEvents.addListener { change ->
        //    showMessage("Officer changed on ${change.member.shipName}: Previously captained by: ${change.previous?.name?.fullName} Currently: ${change.current?.name?.fullName}")
        //}
    }


    fun beforeGameSave() {
        if (playerShuttleExists()) {
            removePlayerShuttle()
            Global.getSector().memoryWithoutUpdate["\$FB_hadCommandShuttle"] = true
        } else {
            Global.getSector().memoryWithoutUpdate["\$FB_hadCommandShuttle"] = false
        }
    }

    fun afterGameSave() {
        if (Global.getSector().memoryWithoutUpdate.getBoolean("\$FB_hadCommandShuttle")) {
            addPlayerShuttle()
        }
    }

    fun reportCurrentLocationChanged(prev: LocationAPI, curr: LocationAPI) {
        commanderShuttleListener.reportCurrentLocationChanged(prev, curr)
    }

    var commanderShuttleListener = CommanderShuttleListener()//Should not be present as a script if the shuttle is not in the player's fleet

    fun removePlayerShuttle() {
        val sector = Global.getSector()

        var hadShuttle = false
        for (member in Global.getSector().playerFleet.fleetData.membersListCopy) {
            if (member.variant.hasHullMod(commandShuttleId)) {
                member.variant.removeMod(commandShuttleId)
                Global.getSector().playerFleet.fleetData.removeFleetMember(member)
                hadShuttle = true
            }
        }
        if (hadShuttle) {
            sector.removeTransientScript(commanderShuttleListener)
            sector.removeListener(commanderShuttleListener)
        }

        updateFleetPanelContents()
    }

    fun addPlayerShuttle() {
        if (playerShuttleExists()) return

        val sector = Global.getSector()

        sector.addTransientScript(commanderShuttleListener)
        sector.addTransientListener(commanderShuttleListener)

        val shuttleMember = Global.getSettings().createFleetMember(FleetMemberType.SHIP, "shuttlepod_Hull")

        shuttleMember.shipName = "Command Shuttle"
        shuttleMember.variant.addMod(commandShuttleId)
        shuttleMember.variant.addTag(Tags.NO_SELL)
        shuttleMember.variant.addTag(Tags.RESTRICTED)
        shuttleMember.variant.addTag(Tags.NO_SIM)
        shuttleMember.variant.addTag(Tags.NO_BATTLE_SALVAGE)
        shuttleMember.variant.addTag(Tags.HULLMOD_NO_DROP_SALVAGE)
        shuttleMember.variant.addTag(Tags.UNRECOVERABLE)
        shuttleMember.variant.addTag(Tags.HIDE_IN_CODEX)
        shuttleMember.variant.addTag(Tags.SHIP_CAN_NOT_SCUTTLE)

        sector.playerFleet.fleetData.addFleetMember(shuttleMember)
        sector.playerFleet.fleetData.setFlagship(shuttleMember)
        shuttleMember.repairTracker.cr = shuttleMember.repairTracker.maxCR

        updateFleetPanelContents()
    }

    fun togglePlayerShuttle() {
        if (playerShuttleExists()) {
            removePlayerShuttle()
        } else {
            addPlayerShuttle()
        }
    }

    fun playerShuttleExists(): Boolean {
        for (member in Global.getSector().playerFleet.fleetData.membersListCopy) {
            if (member.variant.hasHullMod(commandShuttleId)) {
                return true
            }
        }
        return false
    }

    fun addMemberToFleet(
        json: JSONObject,
        fleet: FleetDataAPI,
        randomPastedCosmetics: Boolean = false
    ): Pair<FleetMemberAPI?, MissingElements> {
        val (member, missing) = getMemberFromJsonWithMissing(json)
        if (missing.hullIds.size != 0) {
            return Pair(null, missing)
        }

        if (randomPastedCosmetics) {
            member.shipName = fleet.pickShipName(member, Random())
            if (!member.captain.isDefault && !member.captain.isAICore) {
                val randomPerson = fleet.fleet.faction.createRandomPerson()
                member.captain.name = randomPerson.name
                member.captain.portraitSprite = randomPerson.portraitSprite
            }
        }

        fleet.addFleetMember(member)

        updateFleetPanelContents()

        return Pair(member, missing)
    }

    fun addOfficerToFleet(json: JSONObject, fleet: FleetDataAPI, randomPastedCosmetics: Boolean = false) {
        val officer = getOfficerFromJson(json)

        if (randomPastedCosmetics && !officer.isDefault && !officer.isAICore) {
            val randomPerson = fleet.fleet.faction.createRandomPerson()
            officer.name = randomPerson.name
            officer.portraitSprite = randomPerson.portraitSprite
        }

        fleet.addOfficer(officer) // Note, it doesn't seem possible to add AI cores as officers
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
            val playerJson = saveOfficerToJson(sector.playerPerson)
            playerJson.put("storyPoints", sector.playerStats.storyPoints)
            json.put("player", playerJson)
        }

        if (handleFleet) {
            val fleetJson = saveFleetToJson(
                sector.playerFleet,
                includeCommander = false,
                includeCommanderAsOfficer = false,
                includeOfficers = handleOfficers,
                includeIdleOfficers = handleOfficers
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
                getCargoFromJson(json.getJSONArray("cargo"), cargo)
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
                val loadedPlayer = getOfficerFromJson(playerJson)
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
                        includeOfficers = handleOfficers,
                        includeIdleOfficers = handleOfficers,
                        includeCommander = false
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
            try {
                val hullMods = json.getJSONArray("knownHullMods")
                for (i in 0 until hullMods.length()) {
                    val modId = hullMods.optString(i, null) ?: continue
                    val spec = Global.getSettings().getHullModSpec(modId)
                    if (spec == null) {
                        //missing.hullModIds.add(modId)
                        continue
                    }
                    if (!spec.isAlwaysUnlocked && !spec.isHidden && !spec.isHiddenEverywhere) {
                        sector.characterData.addHullMod(modId)
                    }
                }
            } catch (e: Exception) {
                showError("Failed to load known hullmods", e)
            }
        }

        if (handleKnownBlueprints &&
            json.has("shipBlueprints") &&
            json.has("fighterBlueprints") &&
            json.has("weaponBlueprints")
        ) {
            try {
                val shipBlueprints = json.getJSONArray("shipBlueprints")
                val fighterBlueprints = json.getJSONArray("fighterBlueprints")
                val weaponBlueprints = json.getJSONArray("weaponBlueprints")

                for (i in 0 until shipBlueprints.length()) {
                    val id = shipBlueprints.optString(i, null) ?: continue
                    if (Global.getSettings().getHullSpec(id) != null) {
                        faction.addKnownShip(id, true)
                    }// else {
                    //    missing.hullIds.add(id)
                    //}
                }

                for (i in 0 until fighterBlueprints.length()) {
                    val id = fighterBlueprints.optString(i, null) ?: continue
                    if (Global.getSettings().getFighterWingSpec(id) != null) {
                        faction.addKnownFighter(id, true)
                    }// else {
                    //    missing.wingIds.add(id)
                    //}
                }

                for (i in 0 until weaponBlueprints.length()) {
                    val id = weaponBlueprints.optString(i, null) ?: continue
                    if (Global.getSettings().getWeaponSpec(id) != null) {
                        faction.addKnownWeapon(id, true)
                    }// else {
                    //    missing.weaponIds.add(id)
                    //}
                }
            } catch (e: Exception) {
                showError("Failed to load blueprints", e)
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