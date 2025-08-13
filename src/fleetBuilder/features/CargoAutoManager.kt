package fleetBuilder.features

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import fleetBuilder.config.ModSettings
import fleetBuilder.util.*


class CargoAutoManager : EveryFrameScript {
    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    var interactionMarket: MarketAPI? = null
    override fun advance(amount: Float) {
        val interaction = Global.getSector().campaignUI.currentInteractionDialog
        val interactionTarget = interaction?.interactionTarget
        val market = interactionTarget?.market

        if (market != null && interactionMarket == null) {// Market Enter
            var changeOccured = false

            interactionMarket = market

            interactionMarket?.submarketsCopy?.forEach { submarket ->
                val cargoAutoManage = loadCargoAutoManage(submarket)
                    ?: return@forEach
                if (!cargoAutoManage.applyOnInteraction) return@forEach

                cargoAutoManage.autoManageItems.forEach { item ->
                    if (manageCargo(item, submarket))
                        changeOccured = true
                }
            }

            if (changeOccured)
                DisplayMessage.showMessage("Cargo auto-managed")

        } else if (market == null && interactionMarket != null) { // Market Leave
            var changeOccured = false

            interactionMarket?.submarketsCopy?.forEach { submarket ->
                val cargoAutoManage = loadCargoAutoManage(submarket)
                    ?: return@forEach
                if (!cargoAutoManage.applyOnLeave) return@forEach

                cargoAutoManage.autoManageItems.forEach { item ->
                    if (manageCargo(item, submarket))
                        changeOccured = true
                }
            }

            interactionMarket = market

            if (changeOccured && ModSettings.reportCargoAutoManagerChanges)
                DisplayMessage.showMessage("Cargo auto-managed")
        }


    }

    private fun manageCargo(
        item: ItemAutoManage,
        submarket: SubmarketAPI
    ): Boolean {
        val playerCargo = Global.getSector().playerFleet.cargo

        var playerItemQuantity = getItemQuantity(item, playerCargo)

        var changeOccured = false

        if (item.put && playerItemQuantity > 0) {
            var amount = 0
            if (item.amount != null) {
                if (playerItemQuantity > item.amount.toFloat()) {
                    amount = (playerItemQuantity - item.amount.toFloat()).coerceAtMost(playerItemQuantity).toInt()
                    moveItem(item, playerCargo, submarket.cargo, amount)
                    changeOccured = true
                }
            } else if (item.percent != null) {
                val percent = item.percent.toFloat().coerceIn(0f, 1f)
                val capacity = when (item.data) {
                    "fuel" -> playerCargo.maxFuel
                    "crew" -> playerCargo.maxPersonnel
                    "marines" -> playerCargo.maxPersonnel
                    else -> playerCargo.maxCapacity // fallback
                }

                val desiredAmount = percent * capacity
                if (playerItemQuantity > desiredAmount) {
                    amount = (playerItemQuantity - desiredAmount).coerceIn(0f, playerItemQuantity).toInt()
                    moveItem(item, playerCargo, submarket.cargo, amount)
                    changeOccured = true
                }
            }
        }

        playerItemQuantity = getItemQuantity(item, playerCargo)

        var submarketItemQuantity = getItemQuantity(item, submarket.cargo)

        if (item.take && submarketItemQuantity > 0) {
            var amount = 0

            if (item.amount != null) {
                if (playerItemQuantity < item.amount.toFloat()) {
                    amount = (item.amount.toFloat() - playerItemQuantity).coerceAtMost(submarketItemQuantity).toInt()
                    moveItem(item, submarket.cargo, playerCargo, amount)
                    changeOccured = true
                }
            } else if (item.percent != null) {
                val percent = item.percent.toFloat().coerceIn(0f, 1f)
                val capacity = when (item.data) {
                    "fuel" -> playerCargo.maxFuel
                    "crew" -> playerCargo.maxPersonnel
                    "marines" -> playerCargo.maxPersonnel
                    else -> playerCargo.maxCapacity // fallback
                }

                val desiredAmount = percent * capacity
                if (playerItemQuantity < desiredAmount) {
                    amount = (desiredAmount - playerItemQuantity).coerceIn(0f, submarketItemQuantity).toInt()
                    moveItem(item, submarket.cargo, playerCargo, amount)
                    changeOccured = true
                }
            }

            /*
             if (submarket.plugin is LocalResourcesSubmarketPlugin) {
                 val report = SharedData.getData().currentReport
                 val itemName = submarketStack.displayName // or use a custom name if needed
                 val creditCost = submarketStack.baseValuePerUnit * amount

                 val marketsNode = report.getNode(MonthlyReport.RESTOCKING)
                 marketsNode.name = "Restocking"
                 marketsNode.custom = MonthlyReport.RESTOCKING
                 marketsNode.tooltipCreator = report.getMonthlyReportTooltip()

                 var costNode = report.getNode("id_${submarketStack.commodityId}")
                 costNode?.upkeep += creditCost
                 costNode?.name = itemName

             }
             */
        }

        playerItemQuantity = getItemQuantity(item, playerCargo)

        submarketItemQuantity = getItemQuantity(item, submarket.cargo)

        if (item.quickStack && playerItemQuantity > 0 && submarketItemQuantity > 0) {
            moveItem(item, playerCargo, submarket.cargo)
            changeOccured = true
        }

        return changeOccured
    }

    private fun moveItem(
        item: ItemAutoManage,
        fromCargo: CargoAPI,
        toCargo: CargoAPI,
        amount: Int = -1
    ) {
        if (item.data == "weapon_and_wings")
            fromCargo.moveWeaponAndWings(toCargo, amount.toFloat())
        else if (item.data == "blueprints_and_modspecs")
            fromCargo.moveBlueprintAndModSpec(toCargo, amount.toFloat())
        else
            fromCargo.moveItem(item.type, item.data, toCargo, amount.toFloat())
    }

    private fun getItemQuantity(
        item: ItemAutoManage,
        cargo: CargoAPI
    ): Float = if (item.data == "weapon_and_wings")
        cargo.getWeaponAndWingQuantity()
    else if (item.data == "blueprints_and_modspecs")
        cargo.getBlueprintAndModSpecQuantity()
    else
        cargo.getQuantity(item.type, item.data)
}