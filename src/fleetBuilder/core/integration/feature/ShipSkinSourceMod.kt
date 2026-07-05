package fleetBuilder.core.integration.feature

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.util.api.kotlin.doesFileExist
import fleetBuilder.util.api.kotlin.isSkin

internal object ShipSkinSourceMod {
    fun setShipSkinSourceMods() {
        Global.getLogger(this.javaClass).info("Setting modded ship skin source mods (if present)...")
        var count = 0
        val allHullSpecs = Global.getSettings().allShipHullSpecs
        allHullSpecs.forEach { hull ->
            if (hull.sourceMod == null && hull.isSkin()) {
                val sourceMod = getSourceModFromSkin(hull.hullId)
                if (sourceMod != null) {
                    hull.getFieldsMatching(type = ModSpecAPI::class.java).getOrNull(0)?.set(hull, sourceMod)
                    Global.getLogger(this.javaClass).info("Set modded ship skin source mod for ${hull.hullId} to modID ${sourceMod.id}.")
                    count++
                }
            }
        }

        if (count > 0)
            Global.getLogger(this.javaClass).info("Set modded ship skins source mods for $count hulls.")
        else
            Global.getLogger(this.javaClass).info("None present, no changes made.")
    }

    fun getSourceModFromSkin(hullId: String?): ModSpecAPI? {
        val settings = Global.getSettings()

        val filename = "data/hulls/skins/$hullId.skin"
        settings.modManager.enabledModsCopy.forEach { mod ->
            if (settings.doesFileExist(filename, mod.id))
                return mod
        }

        return null
    }
}