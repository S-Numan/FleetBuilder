package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.SettingsAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.ui.*
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.otherMods.starficz.ReflectionUtils.getMethodsMatching
import fleetBuilder.otherMods.starficz.addButton
import fleetBuilder.otherMods.starficz.getChildrenCopy
import fleetBuilder.otherMods.starficz.height
import fleetBuilder.otherMods.starficz.width
import org.magiclib.kotlin.setAlpha
import java.awt.Color


internal fun UIPanelAPI.whiteBoxForTesting(width: Float? = null, height: Float? = null, alpha: Int = 255): ButtonAPI {
    val whiteBox = this.addButton(
        "", null, Color.BLACK, Color.WHITE.setAlpha(alpha), Alignment.MID, CutStyle.NONE, width ?: this.width, height
            ?: this.height
    )
    return whiteBox
}

internal var previouslyLoadedSprite = HashMap<String, Boolean>()
internal fun SettingsAPI.getAndLoadSprite(filename: String): SpriteAPI? {
    if (!previouslyLoadedSprite.contains(filename)) {
        this.loadTexture(filename)
        previouslyLoadedSprite[filename] = true
    }
    return this.getSprite(filename)
}

internal fun SettingsAPI.loadTextureCached(filename: String) {
    if (!previouslyLoadedSprite.contains(filename)) {
        this.loadTexture(filename)
        previouslyLoadedSprite[filename] = true
    }
}


internal fun Any.safeInvoke(name: String? = null, vararg args: Any?): Any? {
    val paramTypes = args.map { arg -> arg?.let { it::class.javaPrimitiveType ?: it::class.java } }.toTypedArray()
    val reflectedMethods = this.getMethodsMatching(name, parameterTypes = paramTypes)
    if (reflectedMethods.isEmpty()) {
        DisplayMessage.showError(
            short = "ERROR: No method found on class: ${this::class.java.name}. See console for more details.",
            full = "No method found for name: '$name' on class: ${this::class.java.name} " +
                    "with compatible parameter types derived from arguments: ${paramTypes.contentToString()}"
        )
    } else if (reflectedMethods.size > 1) {
        DisplayMessage.showError(
            short = "ERROR: Ambiguous method call on class: ${this::class.java.name}. See console for more details.",
            full = "Ambiguous method call for name: '$name' on class: ${this::class.java.name}. " +
                    "Multiple methods match parameter types derived from arguments: ${paramTypes.contentToString()}"
        )
    } else return reflectedMethods[0].invoke(this, *args)

    return null
}

internal fun Class<*>.safeInvoke(name: String? = null, vararg args: Any?): Any? {
    val paramTypes = args.map { arg -> arg?.let { it::class.javaPrimitiveType ?: it::class.java } }.toTypedArray()
    val reflectedMethods = this.getMethodsMatching(name, parameterTypes = paramTypes)
    if (reflectedMethods.isEmpty())
        DisplayMessage.showError(
            short = "ERROR: No method found on class: ${this::class.java.name}. See console for more details.",
            full = "No method found for name: '$name' on class: ${this::class.java.name} " +
                    "with compatible parameter types derived from arguments: ${paramTypes.contentToString()}"
        )
    else if (reflectedMethods.size > 1)
        DisplayMessage.showError(
            short = "ERROR: Ambiguous method call on class: ${this::class.java.name}. See console for more details.",
            full = "Ambiguous method call for name: '$name' on class: ${this::class.java.name}. " +
                    "Multiple methods match parameter types derived from arguments: ${paramTypes.contentToString()}"
        )
    else return reflectedMethods[0].invoke(null, *args)
    return null
}

internal fun Any.safeGet(name: String? = null, type: Class<*>? = null, searchSuperclass: Boolean = false): Any? {
    val reflectedFields = this.getFieldsMatching(name, fieldAssignableTo = type, searchSuperclass = searchSuperclass)
    if (reflectedFields.isEmpty())
        DisplayMessage.showError(
            short = "ERROR: No field found on class: ${this::class.java.name}. See console for more details.",
            full = "No field found for name: '${name ?: "<any>"}' on class: ${this::class.java.name} " +
                    "that is assignable to type: '${type?.name ?: "<any>"}'."
        )
    else if (reflectedFields.size > 1)
        DisplayMessage.showError(
            short = "ERROR: Ambiguous fields on class: ${this::class.java.name}. See console for more details.",
            full = "Ambiguous fields with name: '${name ?: "<any>"}' on class ${this::class.java.name} " +
                    "assignable to type: '${type?.name ?: "<any>"}'. Multiple fields match."
        )
    else return reflectedFields[0].get(this)

    return null
}

fun Any.safeSet(name: String? = null, value: Any?, searchSuperclass: Boolean = false) {
    val valueType = value?.let { it::class.javaPrimitiveType ?: it::class.java }
    val reflectedFields = this.getFieldsMatching(name, fieldAccepts = valueType, searchSuperclass = searchSuperclass)
    if (reflectedFields.isEmpty())
        DisplayMessage.showError(
            short = "ERROR: No field found on class: ${this::class.java.name}. See console for more details.",
            full = "No field found for name: '${name ?: "<any>"}' on class: ${this::class.java.name} " +
                    "that is accepts type: '${valueType?.name ?: "null"}'."
        )
    else if (reflectedFields.size > 1)
        DisplayMessage.showError(
            short = "ERROR: Ambiguous fields on class: ${this::class.java.name}. See console for more details.",
            full = "Ambiguous fields with name: '${name ?: "<any>"}' on class ${this::class.java.name} " +
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