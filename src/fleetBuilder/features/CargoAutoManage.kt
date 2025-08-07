package fleetBuilder.features

import com.fs.starfarer.api.campaign.CargoAPI

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