package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI

interface OfficerAssignmentListener {
    fun onOfficerAssignmentChanged(change: OfficerAssignmentTracker.OfficerChange)
}

object OfficerAssignmentEvents {
    private val listeners = mutableListOf<OfficerAssignmentListener>()

    fun addListener(listener: OfficerAssignmentListener) {
        listeners += listener
    }

    fun removeListener(listener: OfficerAssignmentListener) {
        listeners -= listener
    }

    fun removeAllListeners() {
        listeners.clear()
    }

    fun notify(change: OfficerAssignmentTracker.OfficerChange) {
        for (listener in listeners) {
            listener.onOfficerAssignmentChanged(change)
        }
    }

    fun notifyAll(changes: List<OfficerAssignmentTracker.OfficerChange>) {
        for (change in changes) {
            notify(change)
        }
    }
}

class OfficerAssignmentTracker {
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
    }
}