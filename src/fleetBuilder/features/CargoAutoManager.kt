package fleetBuilder.features

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.MonthlyReport
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.shared.SharedData
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin
import com.fs.starfarer.api.util.Misc
import fleetBuilder.util.DisplayMessage
import fleetBuilder.util.getWeaponAndWingQuantity
import fleetBuilder.util.moveItem
import fleetBuilder.util.moveStack
import fleetBuilder.util.moveWeaponAndWings


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

        if (market != null && interactionMarket == null) {
            interactionMarket = market
            DisplayMessage.showMessage("Market docked")

            interactionMarket?.submarketsCopy?.forEach { submarket ->
                val cargoAutoManage = interactionMarket?.memoryWithoutUpdate?.get("\$FBC_${submarket.specId}") as? CargoAutoManage
                    ?: return@forEach
                if (!cargoAutoManage.applyOnInteraction) return@forEach

                cargoAutoManage.autoManageItems.forEach { item ->
                    manageCargo(item, submarket)
                }
            }
        } else if (market == null && interactionMarket != null) {
            interactionMarket?.submarketsCopy?.forEach { submarket ->
                val cargoAutoManage = interactionMarket?.memoryWithoutUpdate?.get("\$FBC_${submarket.specId}") as? CargoAutoManage
                    ?: return@forEach
                if (!cargoAutoManage.applyOnLeave) return@forEach

                cargoAutoManage.autoManageItems.forEach { item ->
                    manageCargo(item, submarket)
                }
            }

            interactionMarket = market
            DisplayMessage.showMessage("Market undocked")
        }
    }

    private fun manageCargo(
        item: ItemAutoManage,
        submarket: SubmarketAPI
    ) {
        val playerCargo = Global.getSector().playerFleet.cargo

        var playerItemQuantity =
            if (item.data == "weapon_and_wings")
                playerCargo.getWeaponAndWingQuantity()
            else
                playerCargo.getQuantity(item.type, item.data)

        if (item.put && playerItemQuantity > 0) {
            var amount = 0
            if (item.amount != null) {
                if (playerItemQuantity > item.amount.toFloat()) {
                    amount = (playerItemQuantity - item.amount.toFloat()).coerceAtMost(playerItemQuantity).toInt()
                    if (item.data == "weapon_and_wings")
                        playerCargo.moveWeaponAndWings(submarket.cargo, amount.toFloat())
                    else
                        playerCargo.moveItem(item.type, item.data, submarket.cargo, amount.toFloat())
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
                    if (item.data == "weapon_and_wings")
                        playerCargo.moveWeaponAndWings(submarket.cargo, amount.toFloat())
                    else
                        playerCargo.moveItem(item.type, item.data, submarket.cargo, amount.toFloat())
                }
            }
        }

        playerItemQuantity =
            if (item.data == "weapon_and_wings")
                playerCargo.getWeaponAndWingQuantity()
            else
                playerCargo.getQuantity(item.type, item.data)

        var submarketItemQuantity =
            if (item.data == "weapon_and_wings")
                submarket.cargo.getWeaponAndWingQuantity()
            else
                submarket.cargo.getQuantity(item.type, item.data)

        if (item.take && submarketItemQuantity > 0) {
            var amount = 0

            if (item.amount != null) {
                if (playerItemQuantity < item.amount.toFloat()) {
                    amount = (item.amount.toFloat() - playerItemQuantity).coerceAtMost(submarketItemQuantity).toInt()

                    if (item.data == "weapon_and_wings")
                        submarket.cargo.moveWeaponAndWings(playerCargo, amount.toFloat())
                    else
                        submarket.cargo.moveItem(item.type, item.data, playerCargo, amount.toFloat())
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

                    if (item.data == "weapon_and_wings")
                        submarket.cargo.moveWeaponAndWings(playerCargo, amount.toFloat())
                    else
                        submarket.cargo.moveItem(item.type, item.data, playerCargo, amount.toFloat())
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

        playerItemQuantity =
            if (item.data == "weapon_and_wings")
                playerCargo.getWeaponAndWingQuantity()
            else
                playerCargo.getQuantity(item.type, item.data)

        submarketItemQuantity =
            if (item.data == "weapon_and_wings")
                submarket.cargo.getWeaponAndWingQuantity()
            else
                submarket.cargo.getQuantity(item.type, item.data)

        if (item.quickStack && playerItemQuantity > 0 && submarketItemQuantity > 0) {
            if (item.data == "weapon_and_wings")
                playerCargo.moveWeaponAndWings(submarket.cargo)
            else
                playerCargo.moveItem(item.type, item.data, submarket.cargo)
        }
    }
}