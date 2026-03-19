package fleetBuilder.util.listeners

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.getActualCurrentTab
import java.util.concurrent.CopyOnWriteArrayList

fun interface OfficerChangeListener {
    fun onOfficerAssignmentChanged(change: OfficerChangeTracker.OfficerChange)
}

object OfficerChangeEvents {
    private val listeners = CopyOnWriteArrayList<OfficerChangeListener>()
    fun getListeners(): List<OfficerChangeListener> = listeners.toList()

    fun addListener(listener: OfficerChangeListener) {
        listeners += listener
    }

    fun removeListener(listener: OfficerChangeListener) {
        listeners -= listener
    }

    fun removeListenerOfClass(clazz: Class<out OfficerChangeListener>) {
        listeners.removeAll { clazz.isInstance(it) }
    }

    fun hasListener(listener: OfficerChangeListener): Boolean {
        return listener in listeners
    }

    fun hasListenerOfClass(clazz: Class<out OfficerChangeListener>): Boolean {
        return listeners.any { clazz.isInstance(it) }
    }

    internal fun clearAll() {
        listeners.clear()
    }

    fun notify(change: OfficerChangeTracker.OfficerChange) =
        listeners.forEach { it.onOfficerAssignmentChanged(change) }

    fun notify(changes: Iterable<OfficerChangeTracker.OfficerChange>) {
        changes.forEach { notify(it) }
    }
}

class OfficerChangeTracker {
    data class OfficerChange(
        val member: FleetMemberAPI,
        val previous: PersonAPI?,
        val current: PersonAPI?,
        val newMember: Boolean // Is true if this officer change was activated by a new member being added to the fleet, otherwise false.
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