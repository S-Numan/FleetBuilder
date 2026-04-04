package fleetBuilder.features.displayRecoveryEarly

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.OptionPanelAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipRecoverySpecialData
import com.fs.starfarer.campaign.CustomCampaignEntity
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.util.kotlin.safeGet
import fleetBuilder.util.kotlin.safeInvoke

internal class DisplayDerelictRecoveryEarly : EveryFrameScript {
    override fun isDone(): Boolean = false

    override fun runWhilePaused(): Boolean = true

    var added = false
    override fun advance(amount: Float) {
        if (!Global.getSector().isPaused || Global.getSector().campaignUI.currentInteractionDialog == null) {
            added = false
            return
        }
        val interaction = Global.getSector().campaignUI.currentInteractionDialog
        if (interaction?.interactionTarget !is CustomCampaignEntity || interaction.interactionTarget.customEntityType != "wreck") {
            added = false
            return
        }

        val options = interaction.plugin.getFieldsMatching(type = OptionPanelAPI::class.java).getOrNull(0)?.get(interaction.plugin)
        val optionList = options?.safeInvoke("getSavedOptionList") as? List<*>
        if (optionList.isNullOrEmpty())
            added = false
        else {
            optionList.forEach { option ->
                val data = option?.getFieldsMatching(fieldAccepts = Any::class.java)?.getOrNull(0)?.get(option)
                if (data == "salMakeRecoverable") {
                    val entity = interaction.interactionTarget
                    val derelictShipEntityPlugin = entity.customPlugin as? DerelictShipEntityPlugin
                    if (derelictShipEntityPlugin == null) {
                        added = false
                        return
                    }
                    val perShipData = derelictShipEntityPlugin.data.ship.clone()

                    val data = ShipRecoverySpecialData("")
                    val shipRecoverySpecial = data.createSpecialPlugin()

                    shipRecoverySpecial.init(interaction, data)
                    shipRecoverySpecial.safeInvoke("addMember", perShipData)

                    @Suppress("UNCHECKED_CAST")
                    val membersList = shipRecoverySpecial.safeGet("members") as? List<FleetMemberAPI>
                        ?: return
                    if (!added) {
                        membersList.getOrNull(0)?.let {
                            interaction.visualPanel.showFleetMemberInfo(it, true)
                            added = true
                        }
                    }
                }
            }
        }
    }
}