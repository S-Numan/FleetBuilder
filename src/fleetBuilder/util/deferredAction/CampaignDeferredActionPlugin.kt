package fleetBuilder.util.deferredAction

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import java.util.*

class CampaignDeferredActionPlugin : EveryFrameScript {

    data class Task(
        val action: () -> Unit,
        var time: Long,
        val interval: Float? = null,
        val handle: TaskHandle? = null
    ) : Comparable<Task> {
        override fun compareTo(other: Task): Int = time.compareTo(other.time)
    }

    companion object {
        private var active: CampaignDeferredActionPlugin? = null
        internal fun setActive(value: CampaignDeferredActionPlugin?) {
            active = value
        }

        // Does not persist on load
        @JvmOverloads
        fun performLater(delayInMilli: Float = 0f, action: () -> Unit): TaskHandle {
            val handle = TaskHandle()
            val inst = active ?: return handle

            val time = System.currentTimeMillis() + delayInMilli.toLong()
            inst.queue.add(Task(action, time, null, handle))
            return handle
        }

        fun performEvery(intervalInMilli: Float, action: (TaskHandle) -> Unit): TaskHandle {
            val handle = TaskHandle()
            val inst = active ?: return handle

            val time = System.currentTimeMillis() + intervalInMilli.toLong()

            inst.queue.add(
                Task(
                    action = { action(handle) }, // inject handle into lambda
                    time = time,
                    interval = intervalInMilli,
                    handle = handle
                )
            )

            return handle
        }

        fun performOnUnpause(action: () -> Unit) {
            active?.onUnpause?.add(action)
        }

        fun performOnPlayerBattleFinish(action: () -> Unit) {
            active?.onPlayerBattleFinish?.add(action)
        }

        internal fun battleStarted() {
            active?.battleStarted = true
        }
    }

    private var battleStarted = false
    private val queue = PriorityQueue<Task>()
    private val onUnpause = mutableListOf<() -> Unit>()
    private val onPlayerBattleFinish = mutableListOf<() -> Unit>()

    override fun isDone() = false
    override fun runWhilePaused() = true

    override fun advance(amount: Float) {
        val now = System.currentTimeMillis()

        while (true) {
            val task = queue.peek() ?: break
            if (task.time > now) break

            queue.poll()

            if (task.handle?.cancelled == true) continue

            task.action.invoke()

            task.interval?.let {
                task.time = now + it.toLong()
                queue.add(task)
            }
        }

        if (!Global.getSector().isPaused && onUnpause.isNotEmpty()) {
            onUnpause.forEach { it.invoke() }
            onUnpause.clear()
        }

        if (battleStarted) {
            onPlayerBattleFinish.forEach { it.invoke() }
            onPlayerBattleFinish.clear()
            battleStarted = false
        }
    }
}