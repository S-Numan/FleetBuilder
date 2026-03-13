package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI

object TimeKeeper {

    /**
     * Returns campaign delta at roughly normal speed,
     * independent of fast-forward.
     *
     * Warning, not completely accurate. Do not rely on this for anything which requires precision.
     */
    fun campaignDelta(amount: Float): Float {
        val ui = Global.getSector().campaignUI

        return if (ui != null && ui.isFastForward) {
            val speedMult = Global.getSettings().getFloat("campaignSpeedupMult")
            1f / speedMult * amount // remove the fast-forward effect
        } else {
            amount
        }
    }


    /** Combat delta in seconds, normalized for combat time acceleration */
    fun combatDelta(amount: Float): Float {
        val engine: CombatEngineAPI? = Global.getCombatEngine()
        return if (engine != null) {
            amount / engine.timeMult.modifiedValue
        } else {
            amount
        }
    }
}