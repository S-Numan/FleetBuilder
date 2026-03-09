package fleetBuilder.features.autoMothball

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import fleetBuilder.util.listeners.FleetMemberChangeEvents
import fleetBuilder.util.listeners.FleetMemberChangeTracker

private const val CR_THRESHOLD = 0.4f
private const val HULL_THRESHOLD = 1f

class AutoMothballRecoveredShips : EveryFrameScript {
    private var init = false

    override fun isDone(): Boolean = false

    override fun runWhilePaused(): Boolean = true

    override fun advance(amount: Float) {
        if (!init) {
            val sector = Global.getSector() ?: return

            FleetMemberChangeEvents.addTransientListener { change ->
                if (sector.currentlyOpenMarket != null)
                    return@addTransientListener
                if (change.type == FleetMemberChangeTracker.ChangeType.REMOVED)
                    return@addTransientListener

                val tracker = change.member.repairTracker
                val isDamaged = tracker.cr <= CR_THRESHOLD && change.member.status.hullFraction <= HULL_THRESHOLD
                val notAlreadyMothballed = !tracker.isMothballed && !tracker.isCrashMothballed

                if (isDamaged && notAlreadyMothballed)
                    tracker.isMothballed = true
            }

            init = true
        }
    }

}