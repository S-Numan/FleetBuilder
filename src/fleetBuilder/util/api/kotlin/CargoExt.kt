package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CargoStackAPI
import fleetBuilder.util.api.CargoUtils


/**
 * Returns the manufacturer of the item this stack represents.
 *
 * This function delegates to the [CargoUtils.getItemTech]
 *
 * Due to the way the game handles special items, this function may return null for some items when you might expect it to not.
 * @return The manufacturer of the item, or null if the item is not recognized.
 */
fun CargoStackAPI.getItemTech(): String? =
    CargoUtils.getItemTech(this)

fun CargoStackAPI.moveStack(to: CargoAPI, inputAmount: Float = -1f) {
    if (!this.isNull && this.cargo !== to && inputAmount != 0f) {
        val moveAmount = minOf(if (inputAmount == -1f) this.size else inputAmount, this.size)
        if (moveAmount > 0) {
            to.addItems(this.type, this.data, moveAmount)
            this.cargo.removeItems(this.type, this.data, moveAmount)
        }
    }
}

fun CargoAPI.moveItem(
    type: CargoAPI.CargoItemType,
    data: Any?,
    to: CargoAPI,
    inputAmount: Float = -1f
) {
    var remaining = inputAmount

    for (stack in this.stacksCopy) {
        if (stack.type != type || stack.data != data) continue

        val stackSize = stack.size
        val moveAmount = when {
            inputAmount == -1f -> stackSize // move entire stack
            remaining <= 0f -> break
            else -> minOf(stackSize, remaining)
        }

        stack.moveStack(to, moveAmount)

        if (inputAmount != -1f) {
            remaining -= moveAmount
        }
    }
}

fun CargoAPI.moveCargo(to: CargoAPI) {
    if (this == to) return
    
    this.stacksCopy.forEach { stack ->
        stack.moveStack(to)
    }
}