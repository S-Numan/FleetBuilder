package fleetkit.core.integration.plugin

import com.fs.starfarer.api.PluginPick
import com.fs.starfarer.api.campaign.BaseCampaignPlugin
import com.fs.starfarer.api.campaign.BattleCreationPlugin
import com.fs.starfarer.api.campaign.CampaignPlugin.PickPriority
import com.fs.starfarer.api.campaign.SectorEntityToken
import fleetkit.core.config.FTVariables

class FTCampaignPlugin : BaseCampaignPlugin() {
    override fun getId(): String = "FTCampaignPlugin"
    override fun isTransient(): Boolean = true

    override fun pickBattleCreationPlugin(opponent: SectorEntityToken): PluginPick<BattleCreationPlugin>? {
        return if (opponent.memoryWithoutUpdate.getBoolean("\$#${FTVariables.PREFIX}_customBattleCreationPlugin")) {
            PluginPick<BattleCreationPlugin>(FTBattleCreationPlugin(), PickPriority.MOD_SET)
        } else {
            super.pickBattleCreationPlugin(opponent)
        }
    }
}