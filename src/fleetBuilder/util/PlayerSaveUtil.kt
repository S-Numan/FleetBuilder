package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
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

object PlayerSaveUtil {
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
                    (hullMods == null || hullMods!!.isEmpty())
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
                missing.add(getCargoFromJson(json.getJSONArray("cargo"), cargo))
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
                showError("Failed to load relations", e)
            }
        }

        if (json.has("player")) {
            try {
                val playerJson = json.getJSONObject("player")
                val loadedPlayer = getPersonFromJson(playerJson, missing = missing)

                compiled.player = loadedPlayer
            } catch (e: Exception) {
                showError("Failed to load player", e)
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
                settings = FleetSettings().apply {
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

        return missing
    }
}