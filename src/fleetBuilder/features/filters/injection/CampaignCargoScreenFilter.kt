package fleetBuilder.features.filters.injection

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.PlayerMarketTransaction
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.campaign.listeners.CargoScreenListener
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.features.filters.filterPanels.CargoFilterPanel
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.kotlin.safeInvoke

internal class CampaignCargoScreenFilter : CargoScreenListener, EveryFrameScript {
    var marketOpened = false
    var playerStorageOpened = false

    override fun reportCargoScreenOpened() {
        marketOpened = true
        playerStorageOpened = true
    }

    override fun reportSubmarketOpened(submarket: SubmarketAPI?) {
        if (submarket == null)
            return

        marketOpened = true

        if (submarket.market.id == "fake_market")
            playerStorageOpened = true
    }

    override fun advance(amount: Float) {
        if (!marketOpened)
            return

        val cargoPanel = ReflectionMisc.getCargoPanel() ?: return

        fun makePlayerFilter() {
            val playerCargoDisplay = cargoPanel.safeInvoke("getPlayerCargoDisplay") as? UIPanelAPI
            if (playerCargoDisplay != null)
                CargoFilterPanel(200f, 19f, playerCargoDisplay)

        }

        val entityCargoDisplay = cargoPanel.safeInvoke("getEntityCargoDisplay") as? UIPanelAPI
        fun makeEntityFilter() {
            if (entityCargoDisplay?.safeInvoke("getMode")?.toString() == "CARGO_ENTITY")
                CargoFilterPanel(200f, 19f, entityCargoDisplay)
        }

        //if (playerFilterPanel == null)
        if (playerStorageOpened)
            makePlayerFilter()

        //if (entityFilterPanel == null || entityCargoDisplay?.getChildrenCopy()?.none { (it as? CustomPanelAPI)?.plugin is BaseFilterPanel } == true) {
        makeEntityFilter()
        //}

        marketOpened = false
        playerStorageOpened = false
    }

    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    override fun reportPlayerLeftCargoPods(entity: SectorEntityToken?) {

    }

    override fun reportPlayerNonMarketTransaction(
        transaction: PlayerMarketTransaction?,
        dialog: InteractionDialogAPI?
    ) {

    }
}