package fleetBuilder.features.cargoAutoManage

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.SpecialItemData
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import fleetBuilder.core.FBSettings.PRIMARYDIR
import fleetBuilder.util.FBMisc.jsonArrayToList
import org.json.JSONArray
import org.json.JSONObject

internal object CargoAutoManage {
    data class AutoManage(
        var applyOnInteraction: Boolean = true,
        var applyOnLeave: Boolean = false,
        var orderInList: Int = 0,
        var name: String = "",
        var autoManageItems: MutableList<ItemAutoManage> = mutableListOf()
    ) {
        fun isDefault(): Boolean {
            if (!applyOnInteraction || applyOnLeave || autoManageItems.isNotEmpty() || orderInList != 0 || name != "") return false
            return true
        }

        /*
        fun Any?.toSpecialItemString(): Any? {
            return when (this) {
                is SpecialItemData -> "(${this.id}, ${this.data})"
                else -> this
            }
        }
        fun isEqual(other: Any?): Boolean {
            if (other !is AutoManage) return false
            return applyOnInteraction == other.applyOnInteraction &&
                    applyOnLeave == other.applyOnLeave &&
                    orderInList == other.orderInList &&
                    name == other.name &&
                    autoManageItems.size == other.autoManageItems.size &&
                    autoManageItems.zip(other.autoManageItems).all { (a, b) ->
                        a.copy(data = a.data.toSpecialItemString()) ==
                                b.copy(data = b.data.toSpecialItemString())
                    }
        }
        */

        fun <T> List<T>.toFrequencyMap() =
            groupingBy { it }.eachCount()

        fun equalsIgnoringItemOrder(other: AutoManage): Boolean {
            return applyOnInteraction == other.applyOnInteraction &&
                    applyOnLeave == other.applyOnLeave &&
                    orderInList == other.orderInList &&
                    name == other.name &&
                    autoManageItems.toFrequencyMap() == other.autoManageItems.toFrequencyMap()
        }

    }

    fun getSavedPolicies(): List<CargoAutoManage.AutoManage> {
        val cargoAutoManagerPoliciesPath = "${PRIMARYDIR}CargoAutoManagerPolicies"
        var cargoAutoManagerPoliciesJSON = runCatching {
            if (Global.getSettings().fileExistsInCommon(cargoAutoManagerPoliciesPath))
                Global.getSettings().readJSONFromCommon(cargoAutoManagerPoliciesPath, false)
            else
                JSONObject()
        }.getOrNull()

        if (cargoAutoManagerPoliciesJSON == null || cargoAutoManagerPoliciesJSON.length() == 0) {
            cargoAutoManagerPoliciesJSON = JSONObject()
            cargoAutoManagerPoliciesJSON.put("policies", JSONArray())
        }
        @Suppress("UNCHECKED_CAST")
        val cargoAutoManagerPoliciesTemp = jsonArrayToList(cargoAutoManagerPoliciesJSON.getJSONArray("policies")) as List<Map<*, *>>
        return cargoAutoManagerPoliciesTemp.map { loadCargoAutoManageFromMap(it) }.sortedBy { it.orderInList }.toMutableList()
    }

    data class ItemAutoManage(
        val type: CargoAPI.CargoItemType,
        var data: Any?,
        val icon: String,
        val displayName: String,
        val amount: Int? = null,
        val percent: Double? = null,
        val take: Boolean = false,
        val put: Boolean = false,
        val quickStack: Boolean = false
    )

    //Cannot save the classes directly as that would prevent the mod from being removed.

    fun saveCargoAutoManageToMap(cargoAutoManage: AutoManage, usePair: Boolean = false): Map<String, Any?> {
        return mapOf(
            "applyOnInteraction" to cargoAutoManage.applyOnInteraction,
            "applyOnLeave" to cargoAutoManage.applyOnLeave,
            "orderInList" to cargoAutoManage.orderInList,
            "name" to cargoAutoManage.name,
            "autoManageItems" to cargoAutoManage.autoManageItems.map { item ->
                val data = if (!usePair)
                    item.data
                else if (item.data is SpecialItemData)
                    (item.data as SpecialItemData).id to (item.data as SpecialItemData).data
                else
                    item.data

                mapOf(
                    "type" to item.type.name, // store enum as String
                    "data" to data, // must be primitive or serializable
                    "icon" to item.icon,
                    "displayName" to item.displayName,
                    "amount" to item.amount,
                    "percent" to item.percent,
                    "take" to item.take,
                    "put" to item.put,
                    "quickStack" to item.quickStack
                )
            }
        )
    }

    fun saveCargoAutoManageToSubmarket(submarket: SubmarketAPI, cargoAutoManage: AutoManage) {
        val safeMap = saveCargoAutoManageToMap(cargoAutoManage)
        submarket.market.memoryWithoutUpdate.set("\$FBC_${submarket.specId}", safeMap)
    }

    fun loadCargoAutoManageFromSubmarket(submarket: SubmarketAPI): AutoManage? {
        val stored = submarket.market.memoryWithoutUpdate.get("\$FBC_${submarket.specId}") as? Map<*, *> ?: return null
        return loadCargoAutoManageFromMap(stored)
    }

    fun loadCargoAutoManageFromMap(stored: Map<*, *>, usePair: Boolean = false): AutoManage {
        val applyOnInteraction = stored["applyOnInteraction"] as? Boolean ?: true
        val applyOnLeave = stored["applyOnLeave"] as? Boolean ?: false
        val orderInList = stored["orderInList"] as? Int ?: 0
        val name = stored["name"] as? String ?: ""

        val autoManageItems = (stored["autoManageItems"] as? List<*>)?.mapNotNull { raw ->
            (raw as? Map<*, *>)?.let { m ->
                val typeName = m["type"] as? String ?: return@let null
                val type = try {
                    CargoAPI.CargoItemType.valueOf(typeName)
                } catch (_: Exception) {
                    return@let null
                }

                // This is awful
                var data = if (!usePair)
                    m["data"]
                else {
                    val raw = m["data"]

                    val pair = when (raw) {
                        is Pair<*, *> -> raw
                        is String -> raw
                            .removeSurrounding("(", ")")
                            .split(",")
                            .map { it.trim() }
                            .let { Pair(it.getOrNull(0), it.getOrNull(1)) }
                        else -> null
                    }

                    pair?.let {
                        SpecialItemData(
                            it.first as String?,
                            it.second as String?
                        )
                    } ?: raw
                }
                if (usePair && data is SpecialItemData) {
                    if (data.data == null)
                        data = data.id
                    else if (data.data == "null")
                        data.data = null
                }



                ItemAutoManage(
                    type = type,
                    data = data, // Still Any? If the data is a class from a removed mod, the save won't load
                    icon = m["icon"] as? String ?: "",
                    displayName = m["displayName"] as? String ?: "",
                    amount = (m["amount"] as? Number)?.toInt(),
                    percent = (m["percent"] as? Number)?.toDouble(),
                    take = m["take"] as? Boolean ?: false,
                    put = m["put"] as? Boolean ?: false,
                    quickStack = m["quickStack"] as? Boolean ?: false
                )
            }
        }?.toMutableList() ?: mutableListOf()

        return AutoManage(applyOnInteraction, applyOnLeave, orderInList = orderInList, name = name, autoManageItems = autoManageItems)
    }

    fun unsetCargoAutoManage(submarket: SubmarketAPI) {
        submarket.market.memoryWithoutUpdate.unset("\$FBC_${submarket.specId}")
    }
}