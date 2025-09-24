package fleetBuilder.util

import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import starficz.ReflectionUtils.invoke

class PlaceholderDialog() : InteractionDialogPlugin {

    var dialog: InteractionDialogAPI? = null

    override fun init(dialog: InteractionDialogAPI?) {
        this.dialog = dialog

        dialog!!.promptText = "Test"

        dialog.invoke("setOpacity", 0f)
        dialog.invoke("setBackgroundDimAmount", 0f)
        dialog.invoke("setAbsorbOutsideEvents", false)
        dialog.invoke("makeOptionInstant", 0)
    }

    override fun optionSelected(optionText: String?, optionData: Any?) {

    }

    override fun optionMousedOver(optionText: String?, optionData: Any?) {

    }

    override fun advance(amount: Float) {
        //dialog?.setOpacity(0f)
        //val fader = dialog?.visualPanel?.invoke("getFader") as? Fader
        //fader?.brightness = 0f
    }

    override fun backFromEngagement(battleResult: EngagementResultAPI?) {

    }

    override fun getContext(): Any? {
        return null
    }

    override fun getMemoryMap(): MutableMap<String, MemoryAPI> {
        return hashMapOf()
    }
}