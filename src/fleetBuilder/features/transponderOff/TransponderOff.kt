package fleetBuilder.features.transponderOff

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener
import com.fs.starfarer.api.impl.campaign.ids.Abilities


class TransponderOff : CurrentLocationChangedListener {
    override fun reportCurrentLocationChanged(prev: LocationAPI?, curr: LocationAPI?) {
        if (prev == null || curr == null)
            return

        val playerFleet = Global.getSector().playerFleet ?: return

        if (!playerFleet.isTransponderOn && curr.isHyperspace && !prev.isHyperspace) {
            playerFleet.isTransponderOn = false
            playerFleet.getAbility(Abilities.TRANSPONDER).deactivate()
        }
    }
}