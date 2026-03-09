package fleetBuilder.util.listeners

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI

fun interface MemberChangeListener {
    fun onFleetMemberChanged(change: MemberChangeTracker.MemberChange)
}

object MemberChangeEvents {
    private val listeners = mutableListOf<MemberChangeListener>()

    fun addTransientListener(listener: MemberChangeListener) {
        listeners += listener
    }

    fun removeTransientListener(listener: MemberChangeListener) {
        listeners -= listener
    }

    fun removeTransientListenerWithClass(clazz: Class<out MemberChangeListener>) {
        listeners.removeAll { it::class.java == clazz }
    }

    fun clearAll() {
        listeners.clear()
    }

    fun notify(change: MemberChangeTracker.MemberChange) {
        for (listener in listeners) {
            listener.onFleetMemberChanged(change)
        }
    }

    fun notifyAll(changes: List<MemberChangeTracker.MemberChange>) {
        for (change in changes) {
            notify(change)
        }
    }
}

class MemberChangeTracker {
    data class MemberChange(
        val member: FleetMemberAPI,
        val type: ChangeType
    )

    enum class ChangeType {
        ADDED,
        REMOVED
    }

    private val lastMembers = mutableMapOf<String, FleetMemberAPI>()

    private val changed = mutableListOf<MemberChange>()

    fun getChangedMembers(): List<MemberChange> {
        val fleet = Global.getSector()?.playerFleet ?: return emptyList()
        val data = fleet.fleetData ?: return emptyList()

        changed.clear()

        val currentMembers = data.membersListCopy
        val currentIds = HashSet<String>()

        for (member in currentMembers) {
            currentIds.add(member.id)

            if (!lastMembers.containsKey(member.id)) {
                changed.add(MemberChange(member, ChangeType.ADDED))
            }

            lastMembers[member.id] = member
        }

        val removed = lastMembers.keys.filter { it !in currentIds }

        for (id in removed) {
            val oldMember = lastMembers[id]
            if (oldMember != null) {
                changed.add(MemberChange(oldMember, ChangeType.REMOVED))
            }
        }

        lastMembers.keys.retainAll(currentIds)

        return changed
    }

    fun reset() {
        lastMembers.clear()
        changed.clear()
        getChangedMembers()
    }
}