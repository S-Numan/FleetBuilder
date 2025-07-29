package fleetBuilder.persistence.cargo

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.SpecialItemData
import fleetBuilder.variants.MissingElementsExtended
import org.json.JSONArray
import org.json.JSONObject

object CargoSerialization {

    //Adds cargo to fleet
    fun getCargoFromJson(json: JSONArray, cargo: CargoAPI): MissingElementsExtended {
        val missingElements = MissingElementsExtended()
        for (i in 0 until json.length()) {
            val cargoThing = json.optJSONObject(i) ?: continue
            val type = cargoThing.optString("type", null) ?: continue
            val id = cargoThing.optString("id", null) ?: continue
            val size = cargoThing.optInt("size", -1)
            if (size <= 0) continue

            when (type) {
                "RESOURCES" -> {
                    val spec = runCatching { Global.getSettings().getCommoditySpec(id) }.getOrNull()
                    if (spec != null) {
                        cargo.addCommodity(id, size.toFloat())
                    } else {
                        missingElements.itemIds.add(id)
                    }
                }

                "WEAPONS" -> {
                    val spec = runCatching { Global.getSettings().getWeaponSpec(id) }.getOrNull()
                    if (spec != null) {
                        cargo.addWeapons(id, size)
                    } else {
                        missingElements.cargoWeaponIds.add(id)
                    }
                }

                "FIGHTER_CHIP" -> {
                    val spec = runCatching { Global.getSettings().getFighterWingSpec(id) }.getOrNull()
                    if (spec != null) {
                        cargo.addFighters(id, size)
                    } else {
                        missingElements.cargoWingIds.add(id)
                    }
                }

                "SPECIAL" -> {
                    var data = cargoThing.optString("data", "")
                    if (data.isEmpty())
                        data = null

                    val spec = runCatching { Global.getSettings().getSpecialItemSpec(id) }.getOrNull()
                    if (spec != null) {
                        try {
                            if (id == "fighter_bp" && Global.getSettings().allFighterWingSpecs.find { it.id == data } == null)
                                missingElements.blueprintWingIds.add(data)
                            else if (id == "weapon_bp" && Global.getSettings().allWeaponSpecs.find { it.weaponId == data } == null)
                                missingElements.blueprintWeaponIds.add(data)
                            else if (id == "ship_bp" && Global.getSettings().allShipHullSpecs.find { it.hullId == data } == null)
                                missingElements.blueprintHullIds.add(data)
                            else if (id == "modspec" && Global.getSettings().allHullModSpecs.find { it.id == data } == null)
                                missingElements.hullModIdsKnown.add(data)
                            else
                                cargo.addSpecial(SpecialItemData(id, data), size.toFloat())
                        } catch (_: Exception) {
                            missingElements.itemIds.add("$id:$data")
                        }
                    } else {
                        missingElements.itemIds.add(id)
                    }
                }
            }
        }

        return missingElements
    }

    fun saveCargoToJson(stacks: List<CargoStackAPI>): JSONArray {
        val json = JSONArray()

        for (stack in stacks) {
            val obj = JSONObject().apply {
                put("type", stack.type.toString())
                put("size", stack.size.toInt())
            }

            when (stack.type) {
                CargoAPI.CargoItemType.RESOURCES -> {
                    obj.put("id", stack.commodityId)
                }

                CargoAPI.CargoItemType.WEAPONS -> {
                    val spec = stack.weaponSpecIfWeapon ?: continue
                    obj.put("id", spec.weaponId)
                }

                CargoAPI.CargoItemType.FIGHTER_CHIP -> {
                    val spec = stack.fighterWingSpecIfWing ?: continue
                    obj.put("id", spec.id)
                }

                CargoAPI.CargoItemType.SPECIAL -> {
                    val special = stack.specialDataIfSpecial ?: continue
                    obj.put("id", special.id)
                    obj.put("data", special.data)
                }

                else -> continue
            }

            json.put(obj)
        }

        return json
    }

}