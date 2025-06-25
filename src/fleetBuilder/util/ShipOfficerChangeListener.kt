package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI

fun interface ShipOfficerChangeListener {
    fun onOfficerAssignmentChanged(change: ShipOfficerChangeTracker.OfficerChange)
}

object ShipOfficerChangeEvents {
    private val listeners = mutableListOf<ShipOfficerChangeListener>()

    fun addListener(listener: ShipOfficerChangeListener) {
        listeners += listener
    }

    fun removeListener(listener: ShipOfficerChangeListener) {
        listeners -= listener
    }

    fun removeAllListeners() {
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
        val current: PersonAPI?
    )

    private val lastAssignments = mutableMapOf<String, PersonAPI?>()

    private val changed = mutableListOf<OfficerChange>()


    fun getChangedAssignments(): List<OfficerChange> {
        val fleet = Global.getSector().playerFleet ?: return emptyList()
        changed.clear()

        for (member in fleet.fleetData.membersListCopy) {
            val current = member.captain
            val previous = lastAssignments[member.id]

            if (previous !== current) {
                val currentIsDefault = current?.isDefault != false
                val previousIsDefault = previous?.isDefault != false

                if (!(currentIsDefault && previousIsDefault)) {
                    changed.add(OfficerChange(member, previous, current))
                }
            }

            lastAssignments[member.id] = current
        }

        val currentIds = HashSet<String>(fleet.fleetData.membersListCopy.size)
        for (member in fleet.fleetData.membersListCopy) {
            currentIds.add(member.id)
        }
        lastAssignments.keys.retainAll(currentIds)

        return changed
    }

    fun reset() {
        lastAssignments.clear()
        changed.clear()
        getChangedAssignments()
    }
}