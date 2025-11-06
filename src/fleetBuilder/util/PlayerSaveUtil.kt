package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Abilities
import com.fs.starfarer.campaign.CampaignUIPersistentData
import fleetBuilder.config.FBTxt
import fleetBuilder.persistence.cargo.CargoSerialization.getCargoFromJson
import fleetBuilder.persistence.cargo.CargoSerialization.saveCargoToJson
import fleetBuilder.persistence.fleet.DataFleet.createCampaignFleetFromData
import fleetBuilder.persistence.fleet.FleetSettings
import fleetBuilder.persistence.fleet.JSONFleet.extractFleetDataFromJson
import fleetBuilder.persistence.fleet.JSONFleet.saveFleetToJson
import fleetBuilder.persistence.person.JSONPerson.getPersonFromJson
import fleetBuilder.persistence.person.JSONPerson.savePersonToJson
import fleetBuilder.util.DisplayMessage.showError
import fleetBuilder.util.FBMisc.replacePlayerFleetWith
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.MissingElementsExtended
import org.json.JSONArray
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.optFloat
import starficz.ReflectionUtils.invoke

object PlayerSaveUtil {

    data class AbilityPair(
        var abilityId: String? = null,
        var inHyperAbilityId: String? = null
    )

    //TODO, an option that enables grabbing things from your markets. Items in storage, ships in storage, items in industries. Then putting it all in an abandoned station somewhere in the new sector on load.
    fun createPlayerSaveJson(
        handleCargo: Boolean = true,
        handleRelations: Boolean = true,
        handleKnownBlueprints: Boolean = true,
        handlePlayer: Boolean = true,
        handleFleet: Boolean = true,
        handleCredits: Boolean = true,
        handleKnownHullmods: Boolean = true,
        handleOfficers: Boolean = true,
        handleAbilityBar: Boolean = true,
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
                FleetSettings().apply {
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

        if (handleAbilityBar) {
            val bars: Array<Array<CampaignUIPersistentData.AbilitySlot>> = try {
                @Suppress("UNCHECKED_CAST")
                Global.getSector().uiData.abilitySlotsAPI.invoke("getSlots") as Array<Array<CampaignUIPersistentData.AbilitySlot>>
            } catch (e: Exception) {
                DisplayMessage.showError("Failed to save Ability Bar")
                emptyArray()
            }

            val savedBars = List(bars.size) { List(10) { AbilityPair() } }

            bars.forEachIndexed { barIndex, bar ->
                bar.forEach { slot ->
                    if (slot.slotId >= 10) return@forEach
                    savedBars[barIndex][slot.slotId].apply {
                        if (!slot.abilityId.isNullOrEmpty()) abilityId = slot.abilityId
                        if (!slot.inHyperAbilityId.isNullOrEmpty()) inHyperAbilityId = slot.inHyperAbilityId
                    }
                }
            }


            fun convertSavedBarsToJson(savedBars: List<List<AbilityPair>>): JSONArray {
                val barsJson = JSONArray()

                savedBars.forEachIndexed { barIndex, bar ->
                    val barObject = JSONObject()
                    barObject.put("barIndex", barIndex)

                    val slotsJson = JSONArray()
                    bar.forEachIndexed { slotId, pair ->
                        if (!pair.abilityId.isNullOrEmpty() || !pair.inHyperAbilityId.isNullOrEmpty()) {
                            val slotArray = JSONArray().apply {
                                put(pair.abilityId ?: "")
                                put(pair.inHyperAbilityId ?: "")
                            }

                            val slotObject = JSONObject().apply {
                                put("slotId", slotId)
                                put("values", slotArray)
                            }

                            slotsJson.put(slotObject)
                        }
                    }

                    // Only add the bar if it has non-empty slots
                    if (slotsJson.length() > 0) {
                        barObject.put("slots", slotsJson)
                        barsJson.put(barObject)
                    }
                }

                return barsJson
            }

            json.put("abilityBars", convertSavedBarsToJson(savedBars))
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
        var hullMods: List<String>? = null,
        var abilityBars: List<List<AbilityPair>>? = null,
    ) {
        fun isEmpty(): Boolean {
            return fleet == null &&
                    aggressionDoctrine == -1 &&
                    player == null &&
                    cargo == null &&
                    (relations == null || relations!!.isEmpty()) &&
                    shipBlueprints.isEmpty() &&
                    fighterBlueprints.isEmpty() &&
                    weaponBlueprints.isEmpty() &&
                    (hullMods == null || hullMods!!.isEmpty()) &&
                    abilityBars == null
        }
    }

    fun compilePlayerSaveJson(
        json: JSONObject
    ): Pair<CompiledSaved, MissingElementsExtended> {
        val missing = MissingElementsExtended()
        val compiled = CompiledSaved()

        val sector = Global.getSector() ?: return compiled to missing

        val cargo = Global.getFactory().createCargo(true)

        if (json.has("cargo")) {
            try {
                json.optJSONObject("cargo")?.let { // New
                    missing.add(getCargoFromJson(it, cargo))
                } ?: json.getJSONArray("cargo")?.let { // Legacy
                    missing.add(getCargoFromJson(it, cargo))
                }

            } catch (e: Exception) {
                showError(FBTxt.txt("failed_to_load_cargo"), e)
            }
        }

        if (json.has("credits")) {
            try {
                cargo.credits.add(json.optDouble("credits", 0.0).toFloat())
            } catch (e: Exception) {
                showError(FBTxt.txt("failed_to_load_credits"), e)
            }
        }

        if (cargo.credits.get() > 0 || !cargo.isEmpty)
            compiled.cargo = cargo


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
                showError(FBTxt.txt("failed_to_load_relations"), e)
            }
        }

        if (json.has("player")) {
            try {
                val playerJson = json.getJSONObject("player")
                val loadedPlayer = getPersonFromJson(playerJson, missing = missing)

                compiled.player = loadedPlayer
            } catch (e: Exception) {
                showError(FBTxt.txt("failed_to_load_player"), e)
            }
        }

        if (json.has("fleet")) {
            try {
                val fleetJson = json.getJSONObject("fleet")
                val fleet = createCampaignFleetFromData(
                    extractFleetDataFromJson(fleetJson), true,
                    FleetSettings().apply {
                        includeCommanderSetFlagship = false
                        includeCommanderAsOfficer = false
                        includeAggression = false
                    },
                    missing
                )
                compiled.aggressionDoctrine = fleetJson.optInt("aggression_doctrine", 2)

                compiled.fleet = fleet
            } catch (e: Exception) {
                showError(FBTxt.txt("failed_to_load_fleet"), e)
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

        if (json.has("abilityBars")) {
            try {
                fun convertJsonToSavedBars(
                    barsJson: JSONArray,
                    barCount: Int = 5,
                    slotCount: Int = 10
                ): List<List<AbilityPair>> {
                    // Initialize a blank savedBars structure
                    val savedBars = MutableList(barCount) { MutableList(slotCount) { AbilityPair() } }

                    for (i in 0 until barsJson.length()) {
                        val barObject = barsJson.getJSONObject(i)
                        val barIndex = barObject.getInt("barIndex")
                        val slotsArray = barObject.optJSONArray("slots") ?: continue

                        for (j in 0 until slotsArray.length()) {
                            val slotObject = slotsArray.getJSONObject(j)
                            val slotId = slotObject.getInt("slotId")
                            val values = slotObject.getJSONArray("values")

                            val abilityId = values.optString(0, null).takeIf { it.isNotEmpty() }
                            val inHyperAbilityId = values.optString(1, null).takeIf { it.isNotEmpty() }

                            // Reconstruct AbilityPair in the same position
                            savedBars[barIndex][slotId] = AbilityPair(abilityId, inHyperAbilityId)
                        }
                    }

                    return savedBars
                }

                val savedBars = convertJsonToSavedBars(json.optJSONArray("abilityBars") ?: JSONArray())

                compiled.abilityBars = savedBars
            } catch (e: Exception) {
                showError(FBTxt.txt("failed_to_load_ability_bars"), e)
            }
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
        handleOfficers: Boolean = true,
        handleAbilityBar: Boolean = true,
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
            cargo.clear()
            cargo.addAll(compiled.cargo)
        }

        if (handleCredits && compiled.cargo != null) {
            cargo.credits.set(0f)
            cargo.credits.add(compiled.cargo!!.credits.get())
        }

        if (handleFleet && compiled.fleet != null) {
            replacePlayerFleetWith(
                compiled.fleet!!, replacePlayer = false, aggression = compiled.aggressionDoctrine,
                settings = FleetSettings().apply {
                    memberSettings.includeOfficer = handleOfficers
                    includeIdleOfficers = handleOfficers
                    includeCommanderSetFlagship = false
                    includeCommanderAsOfficer = false
                    includeAggression = false
                })
        }

        if (handleKnownHullmods && compiled.hullMods != null) {
            sector.characterData.hullMods.toList().forEach {
                val spec = runCatching { Global.getSettings().getHullModSpec(it) }.getOrNull()
                if (spec != null && !spec.isAlwaysUnlocked && !spec.isHidden && !spec.isHiddenEverywhere) {
                    sector.characterData.removeHullMod(spec.id)
                }
            }

            val sector = Global.getSector()
            for (modId in compiled.hullMods!!) {
                //sector.playerFaction.addKnownHullMod(modId)
                sector.characterData.addHullMod(modId)
            }
        }

        if (handleKnownBlueprints) {
            sector.playerFaction.knownShips.clear()
            sector.playerFaction.knownFighters.clear()
            sector.playerFaction.knownWeapons.clear()
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

        if (handleAbilityBar) {
            val bars: Array<Array<CampaignUIPersistentData.AbilitySlot>> = try {
                @Suppress("UNCHECKED_CAST")
                Global.getSector().uiData.abilitySlotsAPI.invoke("getSlots") as Array<Array<CampaignUIPersistentData.AbilitySlot>>
            } catch (e: Exception) {
                showError(FBTxt.txt("failed_to_load_ability_bars"), e)
                emptyArray()
            }

            compiled.abilityBars?.forEachIndexed { barIndex, savedBar ->
                savedBar.forEachIndexed { slotIndex, savedSlot ->
                    val bar = bars.getOrNull(barIndex)
                    val slot = bar?.getOrNull(slotIndex)

                    fun convertMissing(id: String?): String? {
                        if (id == null)
                            return null
                        val spec = runCatching { Global.getSettings().getAbilitySpec(id) }.getOrNull()
                        val newID = if (spec != null)
                            spec.id
                        else {
                            when (id) {
                                "SKR_emergency_burn" -> convertMissing(Abilities.EMERGENCY_BURN)
                                "SKR_sustained_burn" -> convertMissing(Abilities.SUSTAINED_BURN)
                                "SKR_neutrino_detector" -> convertMissing(Abilities.GRAVITIC_SCAN)
                                "SKR_remote_survey" -> convertMissing(Abilities.REMOTE_SURVEY)
                                else -> null
                            }
                        }
                        //return newID
                        if (Global.getSector().playerStats.fleet.abilities.contains(newID))
                            return newID
                        return null
                    }

                    slot?.apply {
                        abilityId = convertMissing(savedSlot.abilityId)
                        inHyperAbilityId = convertMissing(savedSlot.inHyperAbilityId)
                    }
                }
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
        handleOfficers: Boolean = true,
        handleAbilityBar: Boolean = true,
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
            handleOfficers,
            handleAbilityBar
        )

        return missing
    }
}