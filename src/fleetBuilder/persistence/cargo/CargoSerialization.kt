package fleetBuilder.persistence.cargo

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.SpecialItemData
import fleetBuilder.util.DisplayMessage
import fleetBuilder.variants.MissingElementsExtended
import org.json.JSONArray
import org.json.JSONObject

object CargoSerialization {

    // Adds cargo to fleet
    // Use JSONObject
    fun getCargoFromJson(json: Any, cargo: CargoAPI): MissingElementsExtended {
        val missingElements = MissingElementsExtended()

        when (json) {
            is JSONObject -> {
                // --- New format: root object with groups ---
                for (type in json.keys()) {
                    val arr = json.optJSONArray(type as String) ?: continue
                    for (i in 0 until arr.length()) {
                        val cargoThing = arr.optJSONObject(i) ?: continue
                        processCargoEntry(type, cargoThing, cargo, missingElements)
                    }
                }
            }

            is JSONArray -> {
                // --- Old format: flat list with "type" ---
                for (i in 0 until json.length()) {
                    val cargoThing = json.optJSONObject(i) ?: continue
                    val type = cargoThing.optString("type", null) ?: continue
                    processCargoEntry(type, cargoThing, cargo, missingElements)
                }
            }

            else -> {
                DisplayMessage.showError("Unsupported cargo JSON format")
            }
        }

        return missingElements
    }

    private fun processCargoEntry(
        type: String,
        cargoThing: JSONObject,
        cargo: CargoAPI,
        missingElements: MissingElementsExtended
    ) {
        val id = cargoThing.optString("id", null) ?: return
        val size = cargoThing.optInt("size", -1)
        if (size <= 0) return

        when (type) {
            "RESOURCES" -> {
                val spec = runCatching { Global.getSettings().getCommoditySpec(id) }.getOrNull()
                if (spec != null) cargo.addCommodity(id, size.toFloat())
                else missingElements.itemIds.add(id)
            }

            "WEAPONS" -> {
                val spec = runCatching { Global.getSettings().getWeaponSpec(id) }.getOrNull()
                if (spec != null) cargo.addWeapons(id, size)
                else missingElements.cargoWeaponIds.add(id)
            }

            "FIGHTER_CHIP" -> {
                val spec = runCatching { Global.getSettings().getFighterWingSpec(id) }.getOrNull()
                if (spec != null) cargo.addFighters(id, size)
                else missingElements.cargoWingIds.add(id)
            }

            "SPECIAL" -> {
                val data = cargoThing.optString("data", "").ifEmpty { null }
                val spec = runCatching { Global.getSettings().getSpecialItemSpec(id) }.getOrNull()
                if (spec != null) {
                    try {
                        when {
                            id == "fighter_bp" && Global.getSettings().allFighterWingSpecs.none { it.id == data } ->
                                missingElements.blueprintWingIds.add(data ?: "null")

                            id == "weapon_bp" && Global.getSettings().allWeaponSpecs.none { it.weaponId == data } ->
                                missingElements.blueprintWeaponIds.add(data ?: "null")

                            id == "ship_bp" && Global.getSettings().allShipHullSpecs.none { it.hullId == data } ->
                                missingElements.blueprintHullIds.add(data ?: "null")

                            id == "modspec" && Global.getSettings().allHullModSpecs.none { it.id == data } ->
                                missingElements.hullModIdsKnown.add(data ?: "null")

                            else -> cargo.addSpecial(SpecialItemData(id, data), size.toFloat())
                        }
                    } catch (_: Exception) {
                        missingElements.itemIds.add("$id:$data")
                    }
                } else {
                    missingElements.itemIds.add(id)
                }
            }
        }
    }

    // Save cargo grouped by type into a single object
    fun saveCargoToJson(stacks: List<CargoStackAPI>): JSONObject {
        val grouped = mutableMapOf<String, JSONArray>()

        for (stack in stacks) {
            val obj = JSONObject().apply {
                put("size", stack.size.toInt())
            }

            when (stack.type) {
                CargoAPI.CargoItemType.RESOURCES -> {
                    obj.put("id", stack.commodityId)
                    grouped.getOrPut("RESOURCES") { JSONArray() }.put(obj)
                }

                CargoAPI.CargoItemType.WEAPONS -> {
                    val spec = stack.weaponSpecIfWeapon ?: continue
                    obj.put("id", spec.weaponId)
                    grouped.getOrPut("WEAPONS") { JSONArray() }.put(obj)
                }

                CargoAPI.CargoItemType.FIGHTER_CHIP -> {
                    val spec = stack.fighterWingSpecIfWing ?: continue
                    obj.put("id", spec.id)
                    grouped.getOrPut("FIGHTER_CHIP") { JSONArray() }.put(obj)
                }

                CargoAPI.CargoItemType.SPECIAL -> {
                    val special = stack.specialDataIfSpecial ?: continue
                    obj.put("id", special.id)
                    obj.put("data", special.data)
                    grouped.getOrPut("SPECIAL") { JSONArray() }.put(obj)
                }

                else -> continue
            }
        }

        return JSONObject(grouped as Map<*, *>)
    }
}