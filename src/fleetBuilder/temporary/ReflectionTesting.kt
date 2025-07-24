package fleetBuilder.temporary

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.mission.MissionDefinitionPlugin
import com.fs.starfarer.loading.SpecStore
import com.fs.starfarer.loading.specs.HullVariantSpec
import starficz.ReflectionUtils
import starficz.ReflectionUtils.getMethodsMatching
import starficz.ReflectionUtils.invoke

fun Testing() {
    val testVariant = (Global.getSettings().getVariant("apogee_Balanced") as HullVariantSpec).clone()
    testVariant.hullVariantId = "apogee_Bogus"
    testVariant.setVariantDisplayName("EEP")


    val specStore = SpecStore()

    //public static void o00000(Class var0, String var1, Object var2)
    val addToSpecMethod = specStore.getMethodsMatching(numOfParams = 3, parameterTypes = arrayOf(Class::class.java, String::class.java, Any::class.java)).firstOrNull()
        ?: return

    //public static <T> T (Class<T> var0, String var1, boolean var2)
    val getFromSpecMethod = specStore.getMethodsMatching(numOfParams = 3, parameterTypes = arrayOf(Class::class.java, String::class.java, Boolean::class.java), returnType = Any::class.java).firstOrNull()
        ?: return

    //public static Collection<Object> o00000(Class var0)
    val returnCollectionOfObjectsArray = specStore.getMethodsMatching(numOfParams = 1, parameterTypes = arrayOf(Class::class.java), returnType = Collection::class.java)
    val returnCollectionOfObjects = returnCollectionOfObjectsArray[1]


    //val staticAddToSpecMethods = ReflectionUtils.getMethodsMatching(SpecStore::class.java, numOfParams = 3, parameterTypes = arrayOf(Class::class.java, String::class.java, Any::class.java))
    //val staticAddToSpecMethod = staticAddToSpecMethods.firstOrNull()
    //    ?: return
    // ReflectionUtils.invoke(SpecStore::class.java, staticAddToSpecMethod.name, HullVariantSpec::class.java, testVariant.hullVariantId, testVariant)

    //val test = SpecStore.Ò00000()//Missions

    //val test2 = SpecStore.Ô00000()//Just highres sensors and phase fields?
    //val test3 = SpecStore.ô00000()//Just Phase Field
    //val test4 = SpecStore.Ò00000(HullVariantSpec::class.java)//Self added failed HullVariantSpec

    val methodMatching = ReflectionUtils.getMethodsMatching(SpecStore::class.java, parameterTypes = arrayOf(Class::class.java)).firstOrNull()
    val someThing = methodMatching?.invoke(SpecStore::class.java, HullVariantSpec::class.java)
    //"Ò00000"


    /*val variantSpecMap = SpecStore::class.java
        .getDeclaredMethod("Ò00000", Class::class.java)
        .apply { isAccessible = true }
        .invoke(null, HullVariantSpec::class.java) as Map<String, HullVariantSpec>*/


    val testGet = SpecStore.o00000(HullVariantSpec::class.java)


    val variantSpecCollection = returnCollectionOfObjects.invoke(specStore, HullVariantSpec::class.java) as Collection<*>


    val test6 = ReflectionUtils.getMethodsMatching(SpecStore::class.java, "return")
    val test7 = test6[1].invoke(SpecStore::class.java)


    val methodsMatchinge = ReflectionUtils.getMethodsMatching(SpecStore::class.java, name = "o00000", numOfParams = 1, parameterTypes = arrayOf(Class::class.java))
    val test24 = methodsMatchinge[0].invoke(SpecStore::class.java, HullVariantSpec::class.java)


    val fieldS = ReflectionUtils.getFieldsMatching(SpecStore::class.java, fieldAccepts = Map::class.java)
    //val aaa = SpecStore::class.java.get(type = )
    val test35 = fieldS[0].get(SpecStore::class.java)
    val test36 = fieldS[1].get(SpecStore::class.java)

    val curVariant = getFromSpecMethod.invoke(specStore, HullVariantSpec::class.java, "FBT_${testVariant.hullVariantId}", true)//Get from spec store

    curVariant
}