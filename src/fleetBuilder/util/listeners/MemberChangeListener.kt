package fleetBuilder.util.listeners

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI
import java.util.concurrent.CopyOnWriteArrayList

fun interface MemberChangeListener {
    fun onFleetMemberChanged(change: MemberChangeTracker.MemberChange)
}

object MemberChangeEvents {
    private val listeners = CopyOnWriteArrayList<MemberChangeListener>()
    fun getListeners(): List<MemberChangeListener> = listeners.toList()

    fun addListener(listener: MemberChangeListener) {
        listeners += listener
    }

    fun removeListener(listener: MemberChangeListener) {
        listeners -= listener
    }

    fun removeListenerOfClass(clazz: Class<out MemberChangeListener>) {
        listeners.removeAll { clazz.isInstance(it) }
    }

    fun hasListener(listener: MemberChangeListener): Boolean {
        return listener in listeners
    }

    fun hasListenerOfClass(clazz: Class<out MemberChangeListener>): Boolean {
        return listeners.any { clazz.isInstance(it) }
    }

    internal fun clearAll() {
        listeners.clear()
    }

    fun notify(change: MemberChangeTracker.MemberChange) =
        listeners.forEach { it.onFleetMemberChanged(change) }

    fun notify(changes: Iterable<MemberChangeTracker.MemberChange>) {
        changes.forEach { notify(it) }
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