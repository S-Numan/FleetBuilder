package fleetBuilder.core.listener

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.combat.entities.Ship
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.safeInvoke
import starficz.BoxedUIShipPreview
import starficz.ReflectionUtils.getConstructorsMatching
import starficz.ReflectionUtils.getFieldsMatching
import starficz.findChildWithMethod
import starficz.getChildrenCopy

internal class CacheObfOnTitle : BaseEveryFrameCombatPlugin() {
    companion object {
        var init = false
        fun onAdvance() {
            if (!init) {
                if (Global.getCurrentState() != GameState.TITLE)
                    return
                val screenPanel = ReflectionMisc.getScreenPanel() ?: return
                cacheObfClassesIfNeeded(screenPanel)
                init = true
            }
        }

        private fun cacheObfClassesIfNeeded(titleScreenPanel: UIPanelAPI) {
            if (BoxedUIShipPreview.SHIP_PREVIEW_CLASS != null) return

            val missionWidget = titleScreenPanel.findChildWithMethod("getMissionList") as? UIPanelAPI ?: return
            val holographicBG = missionWidget.getChildrenCopy()[1] // 2 of the same class in the tree here

            val missionDetail = holographicBG.safeInvoke("getCurr") as? UIPanelAPI ?: return

            val missionShipPreview = missionDetail.findChildWithMethod("setVariant") as? UIPanelAPI ?: return

            val shipPreview = missionShipPreview.findChildWithMethod("isSchematicMode") ?: return

            BoxedUIShipPreview.SHIP_PREVIEW_CLASS = shipPreview.javaClass

            val constructors = BoxedUIShipPreview.SHIP_PREVIEW_CLASS!!.getConstructorsMatching()

            BoxedUIShipPreview.FLEETMEMBER_CONSTRUCTOR = constructors.find { constructor ->
                constructor.parameterTypes.firstOrNull()?.let { FleetMemberAPI::class.java.isAssignableFrom(it) }
                    ?: false
            }
            BoxedUIShipPreview.ENUM_CONSTRUCTOR = constructors.find { it.parameterTypes.size == 2 }
            BoxedUIShipPreview.ENUM_ARRAY = BoxedUIShipPreview.ENUM_CONSTRUCTOR!!.parameterTypes.first().enumConstants

            val shipFields = shipPreview.getFieldsMatching(type = Array<Ship>::class.java)
            BoxedUIShipPreview.SHIPS_FIELD = shipFields[0].name // only one field should be Array<Ship>
        }
    }

    override fun advance(amount: Float, events: List<InputEventAPI?>?) {
        onAdvance()
    }
}