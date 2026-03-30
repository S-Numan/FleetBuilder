package fleetBuilder.serialization.cargo

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.SpecialItemData
import fleetBuilder.serialization.MissingContentExtended
import fleetBuilder.serialization.SerializationUtils.fieldSep
import fleetBuilder.serialization.SerializationUtils.metaSep
import fleetBuilder.serialization.SerializationUtils.sep
import fleetBuilder.util.lib.CompressionUtil

object CompressedCargo {
    fun isCompressedCargo(comp: String): Boolean {
        val metaIndexStart = comp.indexOf(metaSep)
        if (metaIndexStart == -1) return false

        val metaVersion = comp.getOrNull(metaIndexStart + 1)
        return metaVersion?.equals('c', ignoreCase = true) == true
    }

    @JvmOverloads
    fun saveCargoToCompString(
        inputStacks: List<CargoStackAPI>,
        compress: Boolean = true
    ): String {
        val structureVersion =
            if (compress) "c0"
            else "C0"

        val ver = "$metaSep$structureVersion$metaSep"

        val parts = mutableListOf<String>()

        var currentType: String? = null

        val stacks = inputStacks.sortedBy { it.type }

        for (stack in stacks) {
            val size = stack.size.toInt()
            if (size <= 0) continue

            val (type, id, data) = when (stack.type) {
                CargoAPI.CargoItemType.RESOURCES ->
                    Triple("RESOURCES", stack.commodityId, null)

                CargoAPI.CargoItemType.WEAPONS -> {
                    val spec = stack.weaponSpecIfWeapon ?: continue
                    Triple("WEAPONS", spec.weaponId, null)
                }

                CargoAPI.CargoItemType.FIGHTER_CHIP -> {
                    val spec = stack.fighterWingSpecIfWing ?: continue
                    Triple("FIGHTER_CHIP", spec.id, null)
                }

                CargoAPI.CargoItemType.SPECIAL -> {
                    val special = stack.specialDataIfSpecial ?: continue
                    Triple("SPECIAL", special.id, special.data)
                }

                else -> continue
            }

            if (type != currentType) {
                parts += type
                currentType = type
            }

            val entry =
                if (data != null)
                    listOf(id, size, data).joinToString(sep)
                else
                    listOf(id, size).joinToString(sep)

            parts += entry
        }

        var cargoString = parts.joinToString(fieldSep)

        if (compress)
            cargoString = CompressionUtil.base64Deflate(cargoString)

        return "$ver$cargoString"
    }

    fun getCargoFromCompString(
        comp: String,
        cargo: CargoAPI
    ): MissingContentExtended {
        val missing = MissingContentExtended()

        val metaIndexStart = comp.indexOf(metaSep)
        val metaIndexEnd = comp.indexOf(metaSep, metaIndexStart + 1)

        if (metaIndexStart == -1 || metaIndexEnd == -1)
            return missing

        val metaVersion = comp.substring(metaIndexStart + 1, metaIndexEnd)

        val fullData = when (metaVersion) {
            "c0" -> {
                val compressedData = comp.substring(metaIndexEnd + 1)
                CompressionUtil.base64Inflate(compressedData)
            }
            "C0" -> comp.substring(metaIndexEnd + 1)
            else -> return missing
        } ?: return missing

        if (fullData.isBlank())
            return missing

        val entries = fullData.split(fieldSep)

        var currentType: String? = null

        for (entry in entries) {
            if (!entry.contains(sep)) {
                currentType = entry
                continue
            }

            val parts = entry.split(sep)

            val id = parts[0]
            val size = parts.getOrNull(1)?.toIntOrNull() ?: continue
            val data = parts.getOrNull(2)?.ifBlank { null }

            when (currentType) {
                "RESOURCES" -> {
                    val spec = runCatching {
                        Global.getSettings().getCommoditySpec(id)
                    }.getOrNull()

                    if (spec != null)
                        cargo.addCommodity(id, size.toFloat())
                    else
                        missing.itemIds.add(id)
                }
                "WEAPONS" -> {
                    val spec = runCatching {
                        Global.getSettings().getWeaponSpec(id)
                    }.getOrNull()

                    if (spec != null)
                        cargo.addWeapons(id, size)
                    else
                        missing.cargoWeaponIds.add(id)
                }
                "FIGHTER_CHIP" -> {
                    val spec = runCatching {
                        Global.getSettings().getFighterWingSpec(id)
                    }.getOrNull()

                    if (spec != null)
                        cargo.addFighters(id, size)
                    else
                        missing.cargoWingIds.add(id)
                }
                "SPECIAL" -> {
                    val spec = runCatching {
                        Global.getSettings().getSpecialItemSpec(id)
                    }.getOrNull()

                    if (spec != null) {
                        try {
                            cargo.addSpecial(SpecialItemData(id, data), size.toFloat())
                        } catch (_: Exception) {
                            missing.itemIds.add("$id:$data")
                        }
                    } else {
                        missing.itemIds.add(id)
                    }
                }
            }
        }

        cargo.sort()
        return missing
    }
}