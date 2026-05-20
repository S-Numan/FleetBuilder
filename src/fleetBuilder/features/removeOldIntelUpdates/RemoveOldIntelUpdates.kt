package fleetBuilder.features.removeOldIntelUpdates

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.impl.campaign.intel.misc.SimUpdateIntel
import com.fs.starfarer.api.impl.codex.CodexUpdateIntel
import com.fs.starfarer.api.util.IntervalUtil
import fleetBuilder.core.FBSettings

class RemoveOldIntelUpdates : EveryFrameScript {
    companion object {
        private const val CHECK_INTERVAL_DAYS = 0.25f // Check every 1/4th a day
    }

    private val interval = IntervalUtil(CHECK_INTERVAL_DAYS, CHECK_INTERVAL_DAYS)

    override fun isDone(): Boolean = false

    override fun runWhilePaused(): Boolean = true
    override fun advance(amount: Float) {
        val sector = Global.getSector()

        val maxDays = FBSettings.removeIntelUpdatesAfterXDays

        if (maxDays != 0) {
            val days = sector.clock.convertToDays(amount)
            interval.advance(days)

            if (!interval.intervalElapsed()) return
        }

        val intelManager = sector.intelManager
        val clock = sector.clock

        fun process(list: List<IntelInfoPlugin>) {
            val toRemove = mutableListOf<IntelInfoPlugin>()

            for (intel in list) {
                if (intel.isHidden || intel.isImportant) continue

                val days = clock.getElapsedDaysSince(intel.playerVisibleTimestamp)
                if (days > maxDays) {
                    toRemove.add(intel)
                }
            }

            for (intel in toRemove) {
                intelManager.removeIntel(intel)
            }
        }

        process(intelManager.getIntel(CodexUpdateIntel::class.java))
        process(intelManager.getIntel(SimUpdateIntel::class.java))
    }
}