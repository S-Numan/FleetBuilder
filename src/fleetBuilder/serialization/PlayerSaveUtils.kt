package fleetBuilder.serialization

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.*
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.addNonSalvageEntity
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.CampaignUIPersistentData
import fleetBuilder.core.config.FBConst
import fleetBuilder.core.util.DisplayMessage
import fleetBuilder.core.util.FBTxt
import fleetBuilder.serialization.SerializationUtils.getJSONFromStringSafe
import fleetBuilder.serialization.cargo.CompressedCargo
import fleetBuilder.serialization.cargo.JSONCargo
import fleetBuilder.serialization.fleet.CompressedFleet
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.serialization.fleet.FleetSettings
import fleetBuilder.serialization.fleet.JSONFleet
import fleetBuilder.serialization.person.JSONPerson
import fleetBuilder.serialization.person.PersonSettings
import fleetBuilder.util.api.CampaignUtils
import fleetBuilder.util.api.FleetUtils
import fleetBuilder.util.api.kotlin.optJSONArrayToStringList
import fleetBuilder.util.api.kotlin.safeInvoke
import fleetBuilder.util.lib.CompressionUtil
import org.json.JSONArray
import org.json.JSONObject
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.ext.json.optFloat
import org.lwjgl.util.vector.Vector2f
import org.magiclib.kotlin.getStorageCargo
import org.magiclib.util.MagicCampaign
import kotlin.math.cos
import kotlin.math.sin


object PlayerSaveUtils {

    class RemoveEmptyStation(private val station: SectorEntityToken) : EveryFrameScript {
        var isEmpty = false
        override fun isDone() = isEmpty
        override fun runWhilePaused() = false
        private val interval = IntervalUtil(0.1f, 0.1f)
        override fun advance(amount: Float) {
            val days = Global.getSector().clock.convertToDays(amount)
            interval.advance(days)
            if (!interval.intervalElapsed()) return

            val storage = station.market?.getStorageCargo() ?: return
            if (storage.isEmpty && (storage.mothballedShips == null || storage.mothballedShips.numMembers == 0)) {
                isEmpty = true
                station.containingLocation.removeEntity(station)
            }
        }
    }

    data class AbilityPair(
        var abilityId: String? = null,
        var inHyperAbilityId: String? = null
    )

    fun createSaveJson(
        handleCargo: Boolean = true,
        handleSubmarketCargo: Boolean = true,
        handleRelations: Boolean = true,
        handleKnownBlueprints: Boolean = true,
        handlePlayer: Boolean = true,
        handleFleet: Boolean = true,
        handleCredits: Boolean = true,
        handleKnownHullmods: Boolean = true,
        handleOfficers: Boolean = true,
        handleAbilityBar: Boolean = true,
        superCompressSave: Boolean = false,
        showErrorMessages: Boolean = true,
    ): String {
        val json = JSONObject()
        val sector = Global.getSector() ?: return ""
        val playerFleet = sector.playerFleet ?: return ""

        if (handleCargo) {
            //val cargoJson = CargoSerialization.saveCargoToJson(playerFleet.cargo.stacksCopy)
            //json.put("cargo", cargoJson)
            val cargoComp = CompressedCargo.saveCargoToCompString(playerFleet.cargo.stacksCopy, compress = !superCompressSave, showErrorMessages = showErrorMessages)
            json.put("cargo", cargoComp)
        }

        if (handleSubmarketCargo) {
            try {
                val markets = CampaignUtils.getSectorMarkets()
                
                val marketsArray = JSONArray()
                for (market in markets) {
                    //if (market.isHidden)
                    //    continue
                    //if (market.surveyLevel != MarketAPI.SurveyLevel.SEEN && market.surveyLevel != MarketAPI.SurveyLevel.FULL)
                    //    continue
                    if (market.memoryWithoutUpdate.getBoolean("\$FB_SaveTransferStation"))
                        continue
                    if (market.memoryWithoutUpdate.isEmpty)
                        continue
                    if (market.memoryWithoutUpdate.contains("\$isPlayerOwned") && !market.memoryWithoutUpdate.getBoolean("\$isPlayerOwned") && !market.memoryWithoutUpdate.getBoolean("\$isSurveyed"))
                        continue

                    val submarketArray = JSONArray()
                    for (submarket in market.submarketsCopy) {
                        val plugin = submarket.plugin

                        if (!plugin.isFreeTransfer)
                            continue
                        if (plugin is LocalResourcesSubmarketPlugin)
                            continue

                        val cargo = plugin.cargoNullOk ?: continue
                        if (cargo.isEmpty && (cargo.mothballedShips == null || cargo.mothballedShips.numMembers == 0))
                            continue

                        val submarketContentsObject = JSONObject()
                        if (!cargo.isEmpty)
                            submarketContentsObject.put("c", CompressedCargo.saveCargoToCompString(cargo.stacksCopy, compress = !superCompressSave, showErrorMessages = showErrorMessages))
                        if (cargo.mothballedShips != null && cargo.mothballedShips.numMembers > 0)
                            submarketContentsObject.put("f", CompressedFleet.saveFleetToCompString(cargo.mothballedShips, compress = !superCompressSave))
                        submarketArray.put(submarket.nameOneLine)
                        submarketArray.put(submarketContentsObject)
                    }
                    if (submarketArray.length() > 0) {
                        marketsArray.put(market.name)
                        marketsArray.put(submarketArray)
                    }
                }
                if (marketsArray.length() > 0)
                    json.put("submarket_cargos", marketsArray)

            } catch (e: Exception) {
                DisplayMessage.showError("Failed to save submarket cargos", e)
            }
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
            val playerJson = JSONPerson.savePersonToJson(sector.playerPerson, settings = PersonSettings().apply {
                includeMemKeys = false
            })
            playerJson.put("storyPoints", sector.playerStats.storyPoints)
            json.put("player", playerJson)
        }

        if (handleFleet) {
            val compFleet = CompressedFleet.saveFleetToCompString(
                playerFleet,
                FleetSettings().apply {
                    includeCommanderSetFlagship = false
                    includeCommanderAsOfficer = false
                    memberSettings.includeOfficer = handleOfficers
                    includeIdleOfficers = handleOfficers
                },
                includePrepend = false,
                compress = !superCompressSave
            )
            json.put("fleet", compFleet)
        }

        if (handleCredits) {
            val credits = playerFleet.cargo.credits
            json.put("credits", credits.get())
        }

        if (handleKnownHullmods) {
            val hullMods = sector.characterData?.hullMods
            json.put("knownHullMods", JSONArray(hullMods))
        }

        if (handleAbilityBar) {
            val bars: Array<Array<CampaignUIPersistentData.AbilitySlot>> = try {
                @Suppress("UNCHECKED_CAST")
                sector.uiData.abilitySlotsAPI.safeInvoke("getSlots") as Array<Array<CampaignUIPersistentData.AbilitySlot>>
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

            json.put("abilities", JSONArray(sector.characterData.abilities))
        }

        return if (superCompressSave)
            "\$z0$" + CompressionUtil.base64Deflate(json.toString())
        else
            json.toString(4)
    }

    data class MarketCargo(
        val marketName: String,
        val submarkets: List<SubmarketCargo>
    )

    data class SubmarketCargo(
        val submarketName: String,
        val fleet: FleetDataAPI?,
        val cargo: CargoAPI?,
    )

    data class CompiledSave(
        var fleet: CampaignFleetAPI? = null,
        var aggressionDoctrine: Int = -1,
        var player: PersonAPI? = null,
        var cargo: CargoAPI? = null,
        var marketCargos: List<MarketCargo>? = null,
        var relations: Map<String, Float>? = null,
        var shipBlueprints: List<String> = listOf(),
        var fighterBlueprints: List<String> = listOf(),
        var weaponBlueprints: List<String> = listOf(),
        var hullMods: List<String>? = null,
        var abilityBars: List<List<AbilityPair>>? = null,
        var abilities: List<String>? = null,
    ) {
        fun isEmpty(): Boolean {
            return fleet == null &&
                    aggressionDoctrine == -1 &&
                    player == null &&
                    cargo == null &&
                    relations.isNullOrEmpty() &&
                    shipBlueprints.isEmpty() &&
                    fighterBlueprints.isEmpty() &&
                    weaponBlueprints.isEmpty() &&
                    hullMods.isNullOrEmpty() &&
                    abilityBars.isNullOrEmpty() &&
                    abilities.isNullOrEmpty()
        }
    }

    @JvmOverloads
    fun compileSaveAny(
        value: Any?,
        missing: MissingContentExtended = MissingContentExtended()
    ): CompiledSave {
        return when (value) {
            is JSONObject -> compileSaveJson(value, missing)
            is String -> compileSaveString(value, missing)
            else -> CompiledSave()
        }
    }

    @JvmOverloads
    fun compileSaveString(
        value: String,
        missing: MissingContentExtended = MissingContentExtended()
    ): CompiledSave {
        val json = getJSONFromStringSafe(value)
        if (json != null) {
            return compileSaveJson(json, missing)
        } else {
            if (value.contains("\$z0$")) {
                val valueSub = value.substringAfter("\$z0$")
                val valueDecomp = CompressionUtil.base64Inflate(valueSub)
                if (valueDecomp != null) {
                    val json = getJSONFromStringSafe(valueDecomp)
                    if (json != null)
                        return compileSaveJson(json, missing)
                }
            }
        }
        return CompiledSave()
    }

    @JvmOverloads
    fun compileSaveJson(
        json: JSONObject,
        missing: MissingContentExtended = MissingContentExtended()
    ): CompiledSave {
        val compiled = CompiledSave()

        val sector = Global.getSector() ?: return compiled

        val cargo = Global.getFactory().createCargo(true)

        if (json.has("cargo")) {
            try {
                val cargoComp = json.optString("cargo", "")
                if (!cargoComp.isNullOrBlank() && CompressedCargo.isCompressedCargo(cargoComp)) {
                    missing.add(CompressedCargo.getCargoFromCompString(cargoComp, cargo))
                } else {
                    json.optJSONObject("cargo")?.let { // New
                        missing.add(JSONCargo.getCargoFromJson(it, cargo))
                    } ?: json.getJSONArray("cargo")?.let { // Legacy
                        missing.add(JSONCargo.getCargoFromJson(it, cargo))
                    }
                }
            } catch (e: Exception) {
                DisplayMessage.showError(FBTxt.txt("failed_to_load_cargo"), e)
            }
        }

        if (json.has("submarket_cargos")) {
            try {
                val marketsArray = json.optJSONArray("submarket_cargos")
                val marketCargos = mutableListOf<MarketCargo>()

                for (i in 0 until marketsArray.length() step 2) {
                    val marketName = marketsArray.get(i) as String
                    val submarketsArray = marketsArray.get(i + 1) as JSONArray

                    val submarketCargo = mutableListOf<SubmarketCargo>()
                    for (i in 0 until submarketsArray.length() step 2) {
                        val submarketName = submarketsArray.get(i) as String
                        val submarketContentsObject = submarketsArray.get(i + 1) as JSONObject

                        val cargoComp = submarketContentsObject.optString("c", "")
                        val fleetComp = submarketContentsObject.optString("f", "")
                        var realCargo: CargoAPI? = null
                        var realFleet: FleetDataAPI? = null

                        if (cargoComp.isNotEmpty()) {
                            realCargo = Global.getFactory().createCargo(true)
                            missing.add(CompressedCargo.getCargoFromCompString(cargoComp, realCargo))
                        }
                        if (fleetComp.isNotEmpty()) {
                            realFleet = CompressedFleet.getFleetFromCompString(fleetComp, true, missing = missing).fleetData
                        }

                        submarketCargo.add(SubmarketCargo(submarketName, realFleet, realCargo))
                    }
                    if (submarketCargo.isNotEmpty())
                        marketCargos.add(MarketCargo(marketName, submarketCargo))
                }

                if (marketCargos.isNotEmpty())
                    compiled.marketCargos = marketCargos

            } catch (e: Exception) {
                DisplayMessage.showError(FBTxt.txt("failed_to_load_submarket_cargo"), e)
            }
        }

        if (json.has("credits")) {
            try {
                cargo.credits.add(json.optDouble("credits", 0.0).toFloat())
            } catch (e: Exception) {
                DisplayMessage.showError(FBTxt.txt("failed_to_load_credits"), e)
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
                DisplayMessage.showError(FBTxt.txt("failed_to_load_relations"), e)
            }
        }

        if (json.has("player")) {
            try {
                val playerJson = json.getJSONObject("player")
                val loadedPlayer = JSONPerson.getPersonFromJson(playerJson, missing = missing)


                compiled.player = loadedPlayer
            } catch (e: Exception) {
                DisplayMessage.showError(FBTxt.txt("failed_to_load_player"), e)
            }
        }

        if (json.has("fleet")) {
            try {
                val fleetJson = json.optJSONObject("fleet")
                val parsedData = if (fleetJson == null) {
                    val compFleet = json.getString("fleet")
                    CompressedFleet.extractFleetDataFromCompString(compFleet, missing) ?: throw Exception()
                } else {
                    JSONFleet.extractFleetDataFromJson(fleetJson, missing)
                }

                val fleet = DataFleet.createCampaignFleetFromData(
                    parsedData, true,
                    FleetSettings().apply {
                        includeCommanderSetFlagship = false
                        includeCommanderAsOfficer = false
                        includeAggression = false
                    },
                    missing
                )
                if (parsedData.aggression != -1)
                    compiled.aggressionDoctrine = parsedData.aggression
                else
                    compiled.aggressionDoctrine = 2

                compiled.fleet = fleet
            } catch (e: Exception) {
                DisplayMessage.showError(FBTxt.txt("failed_to_load_fleet"), e)
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

        if (json.has("abilities")) {
            compiled.abilities = json.optJSONArrayToStringList("abilities")
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
                DisplayMessage.showError(FBTxt.txt("failed_to_load_ability_bars"), e)
            }
        }

        return compiled
    }

    fun loadCompiledSave(
        compiled: CompiledSave,
        handleCargo: Boolean = true,
        handleSubmarketCargo: Boolean = true,
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
        val playerFleet = sector?.playerFleet ?: return

        val cargo = playerFleet.cargo ?: return

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

        fun findNearbyFreeLocation(
            system: LocationAPI,
            center: Vector2f,
            minDist: Float = 70f,
            step: Float = 120f,
            samplesPerRing: Int = 12,
            maxRadius: Float = 2000f
        ): Vector2f {
            val entities = system.allEntities

            fun isSafe(point: Vector2f): Boolean {
                for (e in entities) {
                    val loc = e.location ?: continue
                    if (e.isExpired) continue
                    if (MathUtils.getDistance(point, loc) < minDist) return false
                }
                return true
            }

            var radius = 0f

            while (radius <= maxRadius) {

                // check multiple angles per ring
                for (i in 0 until samplesPerRing) {
                    val angle = (i.toFloat() / samplesPerRing) * (Math.PI * 2)

                    val x = center.x + (cos(angle) * radius).toFloat()
                    val y = center.y + (sin(angle) * radius).toFloat()

                    val candidate = Vector2f(x, y)

                    if (isSafe(candidate)) {
                        return candidate
                    }
                }

                radius += step
            }

            // fallback (should rarely happen)
            return Vector2f(center.x, center.y)
        }

        try {
            val markets = CampaignUtils.getSectorMarkets()
            markets.toList().forEach {
                if (it.memoryWithoutUpdate.contains("\$FB_SaveTransferStation"))
                    it.primaryEntity.containingLocation.removeEntity(it.primaryEntity)
            }
            if (handleSubmarketCargo && compiled.marketCargos != null) {
                // Remove all previous save transfer stations.

                for (marketCargo in compiled.marketCargos) {
                    for (submarketCargo in marketCargo.submarkets) {
                        val loc = BaseThemeGenerator.EntityLocation()
                        loc.location = findNearbyFreeLocation(sector.currentLocation, playerFleet.location)
                        val added = addNonSalvageEntity(sector.currentLocation, loc, Entities.CARGO_POD_SPECIAL, Factions.NEUTRAL)
                        val entity = added.entity

                        val name = marketCargo.marketName + " - " + submarketCargo.submarketName
                        val market = MagicCampaign.addSimpleMarket(entity, "save_transfer_${sector.genUID()}", name, 0, Factions.NEUTRAL, false, true, listOf(Conditions.ABANDONED_STATION), emptyList(), true, true, false, false, false, true)
                        entity.name = name
                        MagicCampaign.placeOnStableOrbit(entity, true)

                        market.memoryWithoutUpdate.set("\$FB_SaveTransferStation", true)

                        val cargo = market.getSubmarket(Submarkets.SUBMARKET_STORAGE).cargo
                        submarketCargo.cargo?.let { cargo.addAll(it) }
                        submarketCargo.fleet?.membersListCopy?.forEach {
                            if (it.captain != null && !it.captain.isDefault && !it.captain.memoryWithoutUpdate.contains(Misc.CAPTAIN_UNREMOVABLE)) {
                                it.captain.memoryWithoutUpdate.set(FBConst.STORED_OFFICER_TAG, true)
                                it.captain.memoryWithoutUpdate.set(Misc.CAPTAIN_UNREMOVABLE, true)
                            }
                            cargo.mothballedShips.addFleetMember(it)
                        }

                        entity.addScript(RemoveEmptyStation(entity))
                    }
                }
            }
        } catch (e: Exception) {
            DisplayMessage.showError("Error when interacting with save transfer stations", e)
        }

        if (handleCredits && compiled.cargo != null) {
            cargo.credits.set(0f)
            cargo.credits.add(compiled.cargo!!.credits.get())
        }

        if (handleFleet && compiled.fleet != null) {
            FleetUtils.replacePlayerFleetWith(
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

            compiled.abilities?.forEach { id ->
                val spec = runCatching { Global.getSettings().getAbilitySpec(id) }.getOrNull() ?: return@forEach

                if (!sector.characterData.abilities.contains(spec.id)
                    && spec.id != "rat_exoship_management"
                ) // Will cause a crash if the player controllable exoship is not present
                    sector.characterData.addAbility(spec.id)
            }

            val bars: Array<Array<CampaignUIPersistentData.AbilitySlot>> = try {
                @Suppress("UNCHECKED_CAST")
                sector.uiData.abilitySlotsAPI.safeInvoke("getSlots") as Array<Array<CampaignUIPersistentData.AbilitySlot>>
            } catch (e: Exception) {
                DisplayMessage.showError(FBTxt.txt("failed_to_load_ability_bars"), e)
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
                        var newID: String? = spec?.id ?: return null

                        // The ability spec is in the game from this point on

                        val isAbilityUnlocked = playerFleet.abilities?.contains(newID) == true || sector.characterData.abilities.contains(newID) || sector.playerStats.grantedAbilityIds.contains(newID)
                        if (!isAbilityUnlocked) {
                            newID = when (id) {
                                "SKR_emergency_burn" -> convertMissing(Abilities.EMERGENCY_BURN)
                                "SKR_sustained_burn" -> convertMissing(Abilities.SUSTAINED_BURN)
                                "SKR_neutrino_detector" -> convertMissing(Abilities.GRAVITIC_SCAN)
                                "SKR_remote_survey" -> convertMissing(Abilities.REMOTE_SURVEY)
                                else -> null
                            }
                        }

                        if (isAbilityUnlocked)
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
}