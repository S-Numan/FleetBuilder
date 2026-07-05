package fleetBuilder.core.integration.plugin

import com.fs.starfarer.api.PluginPick
import com.fs.starfarer.api.campaign.BaseCampaignPlugin
import com.fs.starfarer.api.campaign.BattleCreationPlugin
import com.fs.starfarer.api.campaign.CampaignPlugin.PickPriority
import com.fs.starfarer.api.campaign.SectorEntityToken

class FBCampaignPlugin : BaseCampaignPlugin() {
    override fun getId(): String = "FBCampaignPlugin"
    override fun isTransient(): Boolean = true

    override fun pickBattleCreationPlugin(opponent: SectorEntityToken): PluginPick<BattleCreationPlugin>? {
        return if (opponent.memoryWithoutUpdate.getBoolean("\$#FB_customBattleCreationPlugin")) {
            PluginPick<BattleCreationPlugin>(FBBattleCreationPlugin(), PickPriority.MOD_SET)
        } else {
            super.pickBattleCreationPlugin(opponent)
        }
    }
}