package fleetBuilder.core.integration.plugin

import com.fs.starfarer.api.combat.BattleCreationContext
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.combat.BattleCreationPluginImpl
import com.fs.starfarer.api.mission.MissionDefinitionAPI

class FBBattleCreationPlugin : BattleCreationPluginImpl() {
    override fun initBattle(context: BattleCreationContext, loader: MissionDefinitionAPI) {
        super.initBattle(context, loader)

        if (context.otherFleet.memoryWithoutUpdate.getBoolean(MemFlags.FLEET_FIGHT_TO_THE_LAST)) {
            context.aiRetreatAllowed = false
            context.fightToTheLast = true // Should already be set but do it again.
        }
    }
}