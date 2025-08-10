package fleetBuilder.features

import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI

data class CargoAutoManage(
    var applyOnInteraction: Boolean = true,
    var applyOnLeave: Boolean = true,
    val autoManageItems: MutableList<ItemAutoManage> = mutableListOf()
) {
    fun isDefault(): Boolean {
        if (!applyOnInteraction || !applyOnLeave || autoManageItems.isNotEmpty()) return false
        return true
    }
}

data class ItemAutoManage(
    val type: CargoAPI.CargoItemType,
    val data: Any?,
    val icon: String,
    val displayName: String,
    val amount: Int? = null,
    val percent: Double? = null,
    val take: Boolean = false,
    val put: Boolean = false,
    val quickStack: Boolean = false
)


//Cannot save the classes directly as that would prevent the mod from being removed.

fun saveCargoAutoManage(submarket: SubmarketAPI, cargoAutoManage: CargoAutoManage) {
    val market = submarket.market

    val safeMap = mapOf(
        "applyOnInteraction" to cargoAutoManage.applyOnInteraction,
        "applyOnLeave" to cargoAutoManage.applyOnLeave,
        "autoManageItems" to cargoAutoManage.autoManageItems.map { item ->
            mapOf(
                "type" to item.type.name, // store enum as String
                "data" to item.data, // must be primitive or serializable
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
    market.memoryWithoutUpdate.set("\$FBC_${submarket.specId}", safeMap)
}

fun loadCargoAutoManage(submarket: SubmarketAPI): CargoAutoManage? {
    val market = submarket.market

    val stored = market.memoryWithoutUpdate.get("\$FBC_${submarket.specId}") as? Map<*, *> ?: return null

    val applyOnInteraction = stored["applyOnInteraction"] as? Boolean ?: true
    val applyOnLeave = stored["applyOnLeave"] as? Boolean ?: true

    val autoManageItems = (stored["autoManageItems"] as? List<*>)?.mapNotNull { raw ->
        (raw as? Map<*, *>)?.let { m ->
            val typeName = m["type"] as? String ?: return@let null
            val type = try {
                CargoAPI.CargoItemType.valueOf(typeName)
            } catch (_: Exception) {
                return@let null
            }

            ItemAutoManage(
                type = type,
                data = m["data"], // Still Any? If the data is a class from a removed mod, the save won't load
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

    return CargoAutoManage(applyOnInteraction, applyOnLeave, autoManageItems)
}

fun unsetCargoAutoManage(submarket: SubmarketAPI) {
    submarket.market.memoryWithoutUpdate.unset("\$FBC_${submarket.specId}")
}