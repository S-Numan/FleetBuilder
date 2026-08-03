package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import fleetBuilder.core.util.DisplayMessage
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
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

internal fun Any.safeInvoke(name: String? = null, vararg args: Any?): Any? {
    val paramTypes = args.map { arg -> arg?.let { it::class.javaPrimitiveType ?: it::class.java } }.toTypedArray()
    val reflectedMethods = this.getMethodsMatching(name, parameterTypes = paramTypes)
    if (reflectedMethods.isEmpty()) {
        // TODO, replace with logger error. User does not need to be informed of non user understandable errors.
        //Global.getLogger(this.javaClass).error()
        DisplayMessage.showErrorFull(
            displayed = "ERROR: No method found on class: ${this::class.java.name}. See console for more details.",
            logged = "No method found for name: '$name' on class: ${this::class.java.name} " +
                    "with compatible parameter types derived from arguments: ${paramTypes.contentToString()}"
        )
    } else if (reflectedMethods.size > 1) {
        DisplayMessage.showErrorFull(
            displayed = "ERROR: Ambiguous method call on class: ${this::class.java.name}. See console for more details.",
            logged = "Ambiguous method call for name: '$name' on class: ${this::class.java.name}. " +
                    "Multiple methods match parameter types derived from arguments: ${paramTypes.contentToString()}"
        )
    } else return reflectedMethods[0].invoke(this, *args)

    return null
}

internal fun Class<*>.safeInvoke(name: String? = null, vararg args: Any?): Any? {
    val paramTypes = args.map { arg -> arg?.let { it::class.javaPrimitiveType ?: it::class.java } }.toTypedArray()
    val reflectedMethods = this.getMethodsMatching(name, parameterTypes = paramTypes)
    if (reflectedMethods.isEmpty())
        DisplayMessage.showErrorFull(
            displayed = "ERROR: No method found on class: ${this::class.java.name}. See console for more details.",
            logged = "No method found for name: '$name' on class: ${this::class.java.name} " +
                    "with compatible parameter types derived from arguments: ${paramTypes.contentToString()}"
        )
    else if (reflectedMethods.size > 1)
        DisplayMessage.showErrorFull(
            displayed = "ERROR: Ambiguous method call on class: ${this::class.java.name}. See console for more details.",
            logged = "Ambiguous method call for name: '$name' on class: ${this::class.java.name}. " +
                    "Multiple methods match parameter types derived from arguments: ${paramTypes.contentToString()}"
        )
    else return reflectedMethods[0].invoke(null, *args)
    return null
}

internal fun Any.safeGet(name: String? = null, type: Class<*>? = null, searchSuperclass: Boolean = false): Any? {
    val reflectedFields = this.getFieldsMatching(name, fieldAssignableTo = type, searchSuperclass = searchSuperclass)
    if (reflectedFields.isEmpty())
        DisplayMessage.showErrorFull(
            displayed = "ERROR: No field found on class: ${this::class.java.name}. See console for more details.",
            logged = "No field found for name: '${name ?: "<any>"}' on class: ${this::class.java.name} " +
                    "that is assignable to type: '${type?.name ?: "<any>"}'."
        )
    else if (reflectedFields.size > 1)
        DisplayMessage.showErrorFull(
            displayed = "ERROR: Ambiguous fields on class: ${this::class.java.name}. See console for more details.",
            logged = "Ambiguous fields with name: '${name ?: "<any>"}' on class ${this::class.java.name} " +
                    "assignable to type: '${type?.name ?: "<any>"}'. Multiple fields match."
        )
    else return reflectedFields[0].get(this)

    return null
}

internal fun Any.safeSet(name: String? = null, value: Any?, searchSuperclass: Boolean = false) {
    val valueType = value?.let { it::class.javaPrimitiveType ?: it::class.java }
    val reflectedFields = this.getFieldsMatching(name, fieldAccepts = valueType, searchSuperclass = searchSuperclass)
    if (reflectedFields.isEmpty())
        DisplayMessage.showErrorFull(
            displayed = "ERROR: No field found on class: ${this::class.java.name}. See console for more details.",
            logged = "No field found for name: '${name ?: "<any>"}' on class: ${this::class.java.name} " +
                    "that is accepts type: '${valueType?.name ?: "null"}'."
        )
    else if (reflectedFields.size > 1)
        DisplayMessage.showErrorFull(
            displayed = "ERROR: Ambiguous fields on class: ${this::class.java.name}. See console for more details.",
            logged = "Ambiguous fields with name: '${name ?: "<any>"}' on class ${this::class.java.name} " +
                    "assignable to type: '${valueType?.name ?: "null"}'. Multiple fields match."
        )
    else return reflectedFields[0].set(this, value)
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