package fleetBuilder.core.shipSkinSourceMod

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.util.api.kotlin.isSkin

internal object ShipSkinSourceMod {
    fun setShipSkinSourceMods() {
        val allHullSpecs = Global.getSettings().allShipHullSpecs
        allHullSpecs.forEach { hull ->
            if (hull.sourceMod == null && hull.isSkin()) {
                val sourceMod = getSourceModFromSkin(hull.hullId)
                if (sourceMod != null)
                    hull.getFieldsMatching(type = ModSpecAPI::class.java).getOrNull(0)?.set(hull, sourceMod)
            }
        }
    }

    fun getSourceModFromSkin(hullId: String?): ModSpecAPI? {
        val settings = Global.getSettings()

        val filename = "data/hulls/skins/$hullId.skin"
        settings.modManager.enabledModsCopy.forEach { mod ->
            try {
                settings.loadJSON(filename, mod.id) // If this does not throw an exception, the file exists in this mod.
                return mod
            } catch (_: Exception) {
            } // File doesn't exist in this mod -> ignore
        }

        return null
    }
}