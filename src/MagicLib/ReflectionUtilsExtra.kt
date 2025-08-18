package MagicLib

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal object ReflectionUtilsExtra {
    private val constructorClass = Class.forName("java.lang.reflect.Constructor", false, Class::class.java.classLoader)
    private val getConstructorParametersHandle = MethodHandles.lookup().findVirtual(
        constructorClass, "getParameterTypes", MethodType.methodType(arrayOf<Class<*>>().javaClass)
    )

    internal fun hasConstructorOfParameters(instance: Any, vararg parameterTypes: Class<*>): Boolean {
        val constructors = instance.javaClass.declaredConstructors as Array<*>
        // Iterate over each constructor and check its parameter types.
        for (ctor in constructors) {
            val params = getConstructorParametersHandle.invoke(ctor) as Array<Class<*>>
            if (params.contentEquals(parameterTypes)) return true
        }
        return false
    }

    internal fun getConstructor(clazz: Class<*>, vararg arguments: Class<*>): MethodHandle =
        MethodHandles.lookup().findConstructor(clazz, MethodType.methodType(Void.TYPE, arguments))

    internal fun instantiate(clazz: Class<*>, vararg arguments: Any?): Any? {
        val args = arguments.map { it!!::class.javaPrimitiveType ?: it::class.java }

        val constructorHandle = getConstructor(clazz, *args.toTypedArray())

        return constructorHandle.invokeWithArguments(arguments.toList())
    }
}