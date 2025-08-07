package fleetBuilder.features

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
    val id: String,
    val icon: String,
    val displayName: String,
    val amount: Int? = null,
    val percent: Double? = null,
    val take: Boolean = false,
    val put: Boolean = false,
    val quickStack: Boolean = false
)