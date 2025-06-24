package fleetBuilder.persistence

import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.SpecialItemData
import org.json.JSONArray
import org.json.JSONObject

object CargoSerialization {

    //Adds cargo to fleet
    fun getCargoFromJson(json: JSONArray, cargo: CargoAPI) {
        for (i in 0 until json.length()) {
            val cargoThing = json.optJSONObject(i) ?: continue
            val type = cargoThing.optString("type", null) ?: continue
            val id = cargoThing.optString("id", null) ?: continue
            val size = cargoThing.optInt("size", -1)
            if (size <= 0) continue

            when (type) {
                "RESOURCES" -> {
                    cargo.addCommodity(id, size.toFloat())
                }

                "WEAPONS" -> {
                    cargo.addWeapons(id, size)
                }

                "FIGHTER_CHIP" -> {
                    cargo.addFighters(id, size)
                }

                "SPECIAL" -> {
                    var data = cargoThing.optString("data", "")
                    if (data.isEmpty())
                        data = null
                    cargo.addSpecial(SpecialItemData(id, data), size.toFloat())
                }
            }
        }
    }

    fun saveCargoToJson(stacks: List<CargoStackAPI>): JSONArray {
        val json = JSONArray()

        for (stack in stacks) {
            val obj = JSONObject().apply {
                put("type", stack.type.toString())
                put("size", stack.size.toInt())
            }

            when (stack.type) {
                CargoItemType.RESOURCES -> {
                    obj.put("id", stack.commodityId)
                }

                CargoItemType.WEAPONS -> {
                    val spec = stack.weaponSpecIfWeapon ?: continue
                    obj.put("id", spec.weaponId)
                }

                CargoItemType.FIGHTER_CHIP -> {
                    val spec = stack.fighterWingSpecIfWing ?: continue
                    obj.put("id", spec.id)
                }

                CargoItemType.SPECIAL -> {
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