package fleetBuilder.util.deferredAction

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.input.InputEventAPI
import java.util.*

class CombatDeferredActionPlugin : BaseEveryFrameCombatPlugin() {

    data class Task(
        val action: () -> Unit,
        var time: Float,
        val interval: Float? = null,
        val handle: TaskHandle? = null
    ) : Comparable<Task> {
        override fun compareTo(other: Task): Int = time.compareTo(other.time)
    }

    companion object {
        private var active: CombatDeferredActionPlugin? = null

        // Not system time, depends on combat time. Stops on pause.

        fun performLater(delayInMilli: Float, action: () -> Unit): TaskHandle {
            val handle = TaskHandle()
            val inst = active ?: return handle

            inst.queue.add(Task(action, inst.time + delayInMilli / 1000f, null, handle))
            return handle
        }

        fun performEvery(intervalInMilli: Float, action: (TaskHandle) -> Unit): TaskHandle {
            val handle = TaskHandle()
            val inst = active ?: return handle

            inst.queue.add(
                Task(
                    action = { action(handle) },
                    time = inst.time + intervalInMilli / 1000f,
                    interval = intervalInMilli / 1000f,
                    handle = handle
                )
            )

            return handle
        }

        fun performOnUnpause(action: () -> Unit) {
            active?.onUnpause?.add(action)
        }
    }

    private var engine: CombatEngineAPI? = null
    private var time = 0f

    private val queue = PriorityQueue<Task>()
    private val onUnpause = mutableListOf<() -> Unit>()
    private var wasPaused = false

    var init = false
    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        if (!init) {
            this.engine = Global.getCombatEngine()
            time = 0f
            queue.clear()
            onUnpause.clear()

            active = this

            CampaignDeferredActionPlugin.battleStarted()

            init = true
        }

        val engine = this.engine ?: return
        val paused = engine.isPaused

        // detect unpause
        if (wasPaused && !paused) {
            onUnpause.forEach { it.invoke() }
            onUnpause.clear()
        }
        wasPaused = paused

        if (paused) return

        time += amount

        while (true) {
            val task = queue.peek() ?: break
            if (task.time > time) break

            queue.poll()

            if (task.handle?.cancelled == true) continue

            task.action.invoke()

            task.interval?.let {
                task.time = time + it
                queue.add(task)
            }
        }
    }
}