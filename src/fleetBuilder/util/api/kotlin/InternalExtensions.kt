package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import fleetBuilder.otherMods.starficz.ReflectionUtils.getMethodsMatching
import fleetBuilder.otherMods.starficz.addButton
import fleetBuilder.otherMods.starficz.getChildrenCopy
import fleetBuilder.otherMods.starficz.height
import fleetBuilder.otherMods.starficz.width
import org.json.JSONArray
import org.json.JSONObject
import org.magiclib.kotlin.setAlpha
import java.awt.Color


internal fun UIPanelAPI.whiteBoxForTesting(width: Float? = null, height: Float? = null, alpha: Int = 255): ButtonAPI {
    val whiteBox = this.addButton(
        "", null, Color.BLACK, Color.WHITE.setAlpha(alpha), Alignment.MID, CutStyle.NONE, width ?: this.width, height
            ?: this.height
    )
    return whiteBox
}

//For optimization purposes
internal fun UIPanelAPI.findChildWithMethodReversed(methodName: String): UIComponentAPI? {
    return getChildrenCopy().asReversed().find { it.getMethodsMatching(name = methodName).isNotEmpty() }
}

internal fun UIPanelAPI.findChildWithPlugin(clazz: Class<*>): CustomPanelAPI? {
    return getChildrenCopy().firstOrNull { child ->
        (child as? CustomPanelAPI)?.plugin?.let { clazz.isInstance(it) } == true
    } as? CustomPanelAPI
}

internal fun JSONArray.containsString(value: String): Boolean {
    for (i in 0 until this.length()) {
        if (this.optString(i) == value) return true
    }
    return false
}

internal fun JSONObject.optJSONArrayToStringList(fieldName: String): List<String> {
    val array = optJSONArray(fieldName) ?: return emptyList()
    val list = mutableListOf<String>()
    for (i in 0 until array.length()) {
        val value = array.optString(i, null)
        if (value != null) {
            list.add(value)
        } else {
            Global.getLogger(javaClass).warn("Invalid string at index $i in '$fieldName'")
        }
    }
    return list
}