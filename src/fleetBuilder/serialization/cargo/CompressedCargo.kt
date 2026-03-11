package fleetBuilder.serialization.cargo

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.SpecialItemData
import fleetBuilder.serialization.MissingElementsExtended
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
        stacks: List<CargoStackAPI>,
        compress: Boolean = true
    ): String {
        val structureVersion =
            if (compress) "c0"
            else "C0"

        val ver = "$metaSep$structureVersion$metaSep"

        val parts = mutableListOf<String>()

        for (stack in stacks) {
            val size = stack.size.toInt()
            if (size <= 0) continue

            when (stack.type) {
                CargoAPI.CargoItemType.RESOURCES -> {
                    val id = stack.commodityId
                    parts += listOf("RESOURCES", id, size).joinToString(sep)
                }
                CargoAPI.CargoItemType.WEAPONS -> {
                    val spec = stack.weaponSpecIfWeapon ?: continue
                    parts += listOf("WEAPONS", spec.weaponId, size).joinToString(sep)
                }
                CargoAPI.CargoItemType.FIGHTER_CHIP -> {
                    val spec = stack.fighterWingSpecIfWing ?: continue
                    parts += listOf("FIGHTER_CHIP", spec.id, size).joinToString(sep)
                }
                CargoAPI.CargoItemType.SPECIAL -> {
                    val special = stack.specialDataIfSpecial ?: continue

                    val data = special.data ?: ""
                    parts += listOf("SPECIAL", special.id, size, data).joinToString(sep)
                }
                else -> continue
            }
        }

        var cargoString = parts.joinToString(fieldSep)

        if (compress)
            cargoString = CompressionUtil.compressString(cargoString)

        return "$ver$cargoString"
    }

    fun getCargoFromCompString(
        comp: String,
        cargo: CargoAPI
    ): MissingElementsExtended {
        val missing = MissingElementsExtended()

        val metaIndexStart = comp.indexOf(metaSep)
        val metaIndexEnd = comp.indexOf(metaSep, metaIndexStart + 1)

        if (metaIndexStart == -1 || metaIndexEnd == -1)
            return missing

        val metaVersion = comp.substring(metaIndexStart + 1, metaIndexEnd)

        val fullData = when (metaVersion) {
            "c0" -> {
                val compressedData = comp.substring(metaIndexEnd + 1)
                CompressionUtil.decompressString(compressedData)
            }
            "C0" -> comp.substring(metaIndexEnd + 1)
            else -> return missing
        } ?: return missing

        if (fullData.isBlank())
            return missing

        val stacks = fullData.split(fieldSep)

        for (entry in stacks) {
            val parts = entry.split(sep)
            if (parts.size < 3) continue

            val type = parts[0]
            val id = parts[1]
            val size = parts[2].toIntOrNull() ?: continue

            when (type) {
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
                    val data = parts.getOrNull(3)?.ifBlank { null }

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

        return missing
    }
}