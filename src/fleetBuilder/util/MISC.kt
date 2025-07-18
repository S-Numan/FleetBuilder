package fleetBuilder.util

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
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
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.campaign.econ.Submarket
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.campaign.ui.UITable
import com.fs.starfarer.codex2.CodexDetailPanel
import com.fs.starfarer.codex2.CodexDialog
import com.fs.starfarer.combat.CombatState
import com.fs.starfarer.coreui.CaptainPickerDialog
import com.fs.starfarer.title.TitleScreenState
import com.fs.state.AppDriver
import fleetBuilder.config.ModSettings.isConsoleModEnabled
import fleetBuilder.config.ModSettings.randomPastedCosmetics
import fleetBuilder.persistence.CargoSerialization.getCargoFromJson
import fleetBuilder.persistence.CargoSerialization.saveCargoToJson
import fleetBuilder.persistence.FleetSerialization
import fleetBuilder.persistence.FleetSerialization.getFleetFromJson
import fleetBuilder.persistence.FleetSerialization.saveFleetToJson
import fleetBuilder.persistence.MemberSerialization
import fleetBuilder.persistence.MemberSerialization.MemberSettings
import fleetBuilder.persistence.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.PersonSerialization
import fleetBuilder.persistence.PersonSerialization.getPersonFromJsonWithMissing
import fleetBuilder.persistence.PersonSerialization.savePersonToJson
import fleetBuilder.persistence.VariantSerialization
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
import fleetBuilder.util.ClipboardUtil.setClipboardText
import fleetBuilder.variants.MissingElements
import org.apache.log4j.Level
import org.json.JSONArray
import org.json.JSONObject
import org.lazywizard.console.Console
import org.lazywizard.lazylib.ext.json.optFloat
import org.lwjgl.input.Keyboard
import starficz.ReflectionUtils.get
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.getMethodsMatching
import starficz.ReflectionUtils.invoke
import java.awt.Color
import java.util.*

object MISC {
    //Short is displayed to the user, full is put in the log/console.
    fun showError(short: String, full: String, e: Exception? = null) {

        showMessage(short, Color.RED)

        if (isConsoleModEnabled) {
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

            val fullMessage = missingMessages.joinToString(separator = "\n")
            showError(defaultShortMessage, fullMessage)
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

        if (displayName.isNotEmpty())
            tempVariant.setVariantDisplayName("ERR:$displayName")
        else
            tempVariant.setVariantDisplayName("ERROR")

        tempVariant.addTag("ERROR")

        return tempVariant
    }

    fun getCoreUI(): UIPanelAPI? {
        val state = AppDriver.getInstance().currentState
        if (state is CampaignState) {
            return (state.invoke("getEncounterDialog")?.let { dialog ->
                dialog.invoke("getCoreUI") as? UIPanelAPI
            } ?: state.invoke("getCore") as? UIPanelAPI)
        } else if (state is TitleScreenState || state is CombatState) {
            return state.invoke("getScreenPanel") as? UIPanelAPI
        }
        return null
    }

    fun getBorderContainer(): UIPanelAPI? {
        return getCoreUI()?.findChildWithMethod("setBorderInsetLeft") as? UIPanelAPI
    }

    fun getRefitTab(): UIPanelAPI? {
        return getBorderContainer()?.findChildWithMethod("goBackToParentIfNeeded") as? UIPanelAPI
    }

    fun getRefitPanel(): UIPanelAPI? {
        return getRefitTab()?.findChildWithMethod("syncWithCurrentVariant") as? UIPanelAPI
    }

    fun getCurrentVariantInRefitTab(): ShipVariantAPI? {
        val shipDisplay = getRefitPanel()?.invoke("getShipDisplay") as? UIPanelAPI ?: return null
        return shipDisplay.invoke("getCurrentVariant") as? ShipVariantAPI
    }

    fun getFleetTab(): UIPanelAPI? {
        val campaignState = Global.getSector().campaignUI
        if (campaignState?.getActualCurrentTab() != CoreUITabId.FLEET)
            return null
        else
            return getCoreUI()?.invoke("getCurrentTab") as? UIPanelAPI
    }

    fun getFleetPanel(): UIPanelAPI? {
        return getFleetTab()?.findChildWithMethod("getOther") as? UIPanelAPI
    }

    fun getFleetSidePanel(): UIPanelAPI? {
        val children = getFleetTab()?.getChildrenCopy()
        return children?.find { it.getFieldsMatching(type = UITable::class.java).isNotEmpty() } as? UIPanelAPI
    }

    fun getMemberUIHoveredInFleetTabLowerPanel(): UIPanelAPI? {
        val fleetTab = MISC.getFleetTab() ?: return null
        val mouseOverMember = fleetTab.invoke("getMousedOverFleetMember") as? FleetMemberAPI ?: return null

        val fleetPanel = MISC.getFleetPanel() ?: return null
        val list = fleetPanel.invoke("getList") ?: return null
        val items = list.invoke("getItems") as? List<Any?>
            ?: return null//Core UI box that contains everything related to the fleet member, including the ship, officer, cr, etc. There is one for each member in your fleet.

        // Find UI element of which the mouse is hovering over
        items.forEach { item ->
            if (item == null) return@forEach

            //Get all children for this item
            val children = item.invoke("getChildrenCopy") as? List<Any?> ?: return@forEach

            //Find the UI child with a portrait button
            val foundUI = children.firstOrNull { child ->
                child != null && child.getMethodsMatching(name = "getPortraitButton").isNotEmpty()
            } ?: return@forEach

            //Get FleetMember
            val fields = foundUI.getFieldsMatching(type = FleetMember::class.java)
            if (fields.isEmpty()) return@forEach

            //Return if this item's fleet member is not the one we are hovering over
            val member = fields[0].get(foundUI) as? FleetMemberAPI
            if (member?.id != mouseOverMember.id) return@forEach

            //If we've got here, this is the UI item the mouse is hovering over.
            return foundUI as UIPanelAPI?
        }

        return null
    }

    fun getCodexDialog(): CodexDialog? {
        //if (!Global.getSettings().isShowingCodex) // This does NOT work as of 0.98
        //    return null

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

    fun getCaptainPickerDialog(): CaptainPickerDialog? {
        val core = getCoreUI() ?: return null
        val children = core.invoke("getChildrenNonCopy") as? MutableList<*> ?: return null
        return children.firstOrNull { it is CaptainPickerDialog } as? CaptainPickerDialog
    }

    fun addParamEntryToFleet(sector: SectorAPI, param: Any, ctrlCreatesBlueprints: Boolean = true) {
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

    fun getViewedFleetInFleetPanel(
    ): FleetDataAPI? {
        val campaignUI = Global.getSector().campaignUI

        if (campaignUI.getActualCurrentTab() == CoreUITabId.FLEET) {
            val fleetPanel = getFleetPanel() ?: return null

            return fleetPanel.invoke("getFleetData") as? FleetDataAPI
        }
        return null
    }

    fun getSelectedSubmarketInFleetTab(
    ): SubmarketAPI? {
        val campaignUI = Global.getSector().campaignUI

        if (campaignUI.getActualCurrentTab() == CoreUITabId.FLEET && campaignUI.isShowingDialog) {
            val dialog = campaignUI.currentInteractionDialog ?: return null
            dialog.interactionTarget?.market ?: return null

            val fleetTab = getFleetTab() ?: return null

            return fleetTab
                .getFieldsMatching(fieldAssignableTo = Submarket::class.java)
                .getOrNull(0)
                ?.get(fleetTab) as? SubmarketAPI
        }
        return null
    }

    private var postUpdateFleetPanelCallbacks = mutableListOf<() -> Unit>()
    fun addPostUpdateFleetPanelCallback(callback: () -> Unit) {
        if (postUpdateFleetPanelCallbacks.contains(callback)) return

        postUpdateFleetPanelCallbacks.add(callback)
    }

    fun removePostUpdateFleetPanelCallback(callback: () -> Unit) {
        postUpdateFleetPanelCallbacks.remove(callback)
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

        postUpdateFleetPanelCallbacks.forEach { it.invoke() }
    }

    fun codexEntryToClipboard(codex: CodexDialog) {
        val param = MISC.getCodexEntryParam(codex)
        if (param == null) return

        when (param) {
            is ShipHullSpecAPI -> {
                val emptyVariant = Global.getSettings().createEmptyVariant(param.hullId, param)
                val json = saveVariantToJson(emptyVariant)
                setClipboardText(json.toString(4))
                MISC.showMessage("Copied codex variant to clipboard")
            }

            is FleetMemberAPI -> {
                val json = saveMemberToJson(param)
                setClipboardText(json.toString(4))
                MISC.showMessage("Copied codex member to clipboard")
            }
        }
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
                            includeIdleOfficers = includeIdleOfficers
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