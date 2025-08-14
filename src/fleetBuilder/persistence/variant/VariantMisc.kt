package fleetBuilder.persistence.variant

import fleetBuilder.variants.VariantLib

object VariantMisc {
    fun getSourceModsFromVariant(
        addedModIds: MutableSet<Triple<String, String, String>>,
        data: DataVariant.ParsedVariantData
    ) {
        data.moduleVariants.forEach { (_, module) ->
            getSourceModsFromVariant(addedModIds, module)
        }

        fun addSourceMod(modId: String, modName: String, modVersion: String) {
            if (Triple(modId, modName, modVersion) !in addedModIds) {
                addedModIds.add(Triple(modId, modName, modVersion))
            }
        }

        //HullSpec
        VariantLib.getHullSpec(data.hullId)?.let { hullSpec ->
            hullSpec.sourceMod?.let { sm ->
                addSourceMod(sm.id, sm.name, sm.version)
            }
        }

        // HullMods
        for (mod in data.hullMods) {
            VariantLib.getHullModSpec(mod)?.sourceMod?.let { sm ->

                addSourceMod(sm.id, sm.name, sm.version)
            }
        }

        // Weapons
        for (group in data.weaponGroups) {
            group.weapons.forEach { (slot, weaponId) ->
                VariantLib.getWeaponSpec(weaponId)?.sourceMod?.let { sm ->
                    addSourceMod(sm.id, sm.name, sm.version)
                }
            }
        }

        // Fighter Wings
        for (wing in data.wings) {
            VariantLib.getFighterWingSpec(wing)?.sourceMod?.let { sm ->
                addSourceMod(sm.id, sm.name, sm.version)
            }
        }
    }
}