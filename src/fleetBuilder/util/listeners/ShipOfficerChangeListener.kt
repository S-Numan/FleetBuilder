package fleetBuilder.util.listeners

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.getActualCurrentTab

fun interface ShipOfficerChangeListener {
    fun onOfficerAssignmentChanged(change: ShipOfficerChangeTracker.OfficerChange)
}

object ShipOfficerChangeEvents {
    private val listeners = mutableListOf<ShipOfficerChangeListener>()

    fun addTransientListener(listener: ShipOfficerChangeListener) {
        listeners += listener
    }

    fun removeTransientListener(listener: ShipOfficerChangeListener) {
        listeners -= listener
    }

    fun removeTransientListenerWithClass(clazz: Class<out ShipOfficerChangeListener>) {
        listeners.removeAll { it::class.java == clazz }
    }

    fun clearAll() {
        listeners.clear()
    }

    fun notify(change: ShipOfficerChangeTracker.OfficerChange) {
        for (listener in listeners) {
            listener.onOfficerAssignmentChanged(change)
        }
    }

    fun notifyAll(changes: List<ShipOfficerChangeTracker.OfficerChange>) {
        for (change in changes) {
            notify(change)
        }
    }
}

class ShipOfficerChangeTracker {
    data class OfficerChange(
        val member: FleetMemberAPI,
        val previous: PersonAPI?,
        val current: PersonAPI?,
        val newMember: Boolean // Is true if this officer change was activated by a new ship being added to the fleet, otherwise false.
    )

    private val lastAssignments = mutableMapOf<String, PersonAPI?>()

    private val changed = mutableListOf<OfficerChange>()

    private var justPickedUpMember = false

    fun getChangedAssignments(): List<OfficerChange> {
        val fleet = Global.getSector()?.playerFleet ?: return emptyList()
        if (fleet.fleetData == null) return emptyList()

        changed.clear()

        for (member in fleet.fleetData.membersListCopy) {
            if (member == null) continue

            val current = member.captain
            val previous = lastAssignments[member.id]

            if (previous !== current) {
                val currentIsDefault = current?.isDefault != false
                val previousIsNull = previous == null
                val previousIsDefault = previous?.isDefault != false

                if (!(currentIsDefault && previousIsDefault)) {
                    if (!justPickedUpMember)
                        changed.add(OfficerChange(member, previous, current, previousIsNull))
                }
            }

            lastAssignments[member.id] = current
        }

        val currentIds = HashSet<String>(fleet.fleetData.membersListCopy.size)
        for (member in fleet.fleetData.membersListCopy) {
            currentIds.add(member.id)
        }
        lastAssignments.keys.retainAll(currentIds)

        // Picking up and putting down a member in the fleet screen would be considered an officer change by default, this prevents that
        if (Global.getSector().campaignUI.getActualCurrentTab() == CoreUITabId.FLEET)
            justPickedUpMember = ReflectionMisc.getFleetScreenPickedUpMember() != null

        return changed
    }

    fun reset() {
        lastAssignments.clear()
        changed.clear()
        getChangedAssignments()
    }
}