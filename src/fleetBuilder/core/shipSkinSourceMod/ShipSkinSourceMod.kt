package fleetBuilder.core.shipSkinSourceMod

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.util.kotlin.isSkin
import org.json.JSONObject

internal object ShipSkinSourceMod {
    var init = false
    fun setShipSkinSourceMods() {
        // This is an expensive function, let's not do it twice
        if (init)
            return

        val allHullSpecs = Global.getSettings().allShipHullSpecs
        allHullSpecs.forEach { hull ->
            if (hull.sourceMod == null && hull.isSkin()) {
                val sourceMod = getSourceModFromSkin(hull)
                if (sourceMod != null)
                    hull.getFieldsMatching(type = ModSpecAPI::class.java).getOrNull(0)?.set(hull, sourceMod)
            }
        }
        init = true
    }

    // If this is intended to be done more than once, make a map instead.
    fun getSourceModFromSkin(spec: ShipHullSpecAPI?): ModSpecAPI? {
        if (spec == null) return null

        // If already defined, just return it
        spec.sourceMod?.let { return it }

        val settings = Global.getSettings()
        val hullId = spec.hullId

        // Typical skin path
        val filename = "data/hulls/skins/$hullId.skin"

        for (mod in settings.modManager.enabledModsCopy) {
            val modId = mod.id
            try {
                val json: JSONObject = settings.loadJSON(filename, modId)

                // If this succeeds, the file exists in this mod
                return mod
            } catch (ex: Exception) {
                // File doesn't exist in this mod -> ignore
            }
        }

        return null
    }
}