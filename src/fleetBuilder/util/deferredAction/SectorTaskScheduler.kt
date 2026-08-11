package fleetBuilder.util.deferredAction

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import fleetBuilder.util.deferredAction.DeferredActionUtils.safeRun
import java.util.*


class SectorTaskScheduler : EveryFrameScript {

    companion object {
        private var active: SectorTaskScheduler? = null
        internal fun setActive(value: SectorTaskScheduler?) {
            active = value
        }

        /**
         * Runs [action] once, [delay] from now.
         *
         * If [systemTime] is false, timing follows sector time, and stops advancing while the game is paused.
         *
         * If [systemTime] is true, timing follows [System.nanoTime] in milliseconds, not sector-days, and keeps advancing while the game is paused.
         *
         * Does not persist in save file, re-register every session if needed.
         */
        @JvmStatic
        fun performLater(delay: Long = 0, systemTime: Boolean = false, action: () -> Unit): TaskHandle {
            val handle = TaskHandle()
            val inst = active ?: return handle

            val task = Task(
                action = action,
                time =
                    if (systemTime) System.nanoTime() + delay * 1_000_000L
                    else Global.getSector().clock.timestamp + delay,
                interval = null,
                handle = handle
            )

            if (systemTime)
                inst.systemTimeQueue.add(task)
            else
                inst.sectorTimeQueue.add(task)

            return handle
        }

        /**
         * Runs [action] every [interval], starting after one interval.
         *
         * If [systemTime] is false, timing follows sector time, and stops advancing while the game is paused.
         *
         * If [systemTime] is true, timing follows [System.nanoTime] in milliseconds, not sector-days, and keeps advancing while the game is paused.
         *
         * Does not persist in save file, re-register every session if needed.
         */
        @JvmStatic
        fun performEvery(interval: Long = 0, systemTime: Boolean = false, action: (TaskHandle) -> Unit): TaskHandle {
            val handle = TaskHandle()
            val inst = active ?: return handle

            val task = Task(
                action = { action(handle) }, // inject handle into lambda
                time =
                    if (systemTime) System.nanoTime() + interval * 1_000_000L
                    else Global.getSector().clock.timestamp + interval,
                interval = interval * 1_000_000L,
                handle = handle
            )

            if (systemTime)
                inst.systemTimeQueue.add(task)
            else
                inst.sectorTimeQueue.add(task)

            return handle
        }

        @JvmStatic
        fun performOnUnpause(action: () -> Unit) {
            active?.onUnpause?.add(action)
        }

        @JvmStatic
        fun performOnPlayerBattleFinish(action: () -> Unit) {
            active?.onPlayerBattleFinish?.add(action)
        }

        internal fun battleStarted() {
            active?.battleStarted = true
        }
    }

    private var battleStarted = false
    private val systemTimeQueue = PriorityQueue<Task>()
    private val sectorTimeQueue = PriorityQueue<Task>()

    private val onUnpause = mutableListOf<() -> Unit>()

    private val onPlayerBattleFinish = mutableListOf<() -> Unit>()

    override fun isDone() = false
    override fun runWhilePaused() = true

    override fun advance(amount: Float) {
        val sector = Global.getSector() ?: return

        if (battleStarted) {
            val callbacks = onPlayerBattleFinish.toList()
            onPlayerBattleFinish.clear()
            battleStarted = false
            callbacks.forEach { safeRun("onPlayerBattleFinish callback") { it.invoke() } }
        }

        if (!sector.isPaused && onUnpause.isNotEmpty()) {
            val callbacks = onUnpause.toList()
            onUnpause.clear()
            callbacks.forEach { safeRun("onUnpause callback") { it.invoke() } }
        }

        val systemNow = System.nanoTime()
        while (true) {
            val task = systemTimeQueue.peek() ?: break
            if (task.time > systemNow) break

            systemTimeQueue.poll()

            if (task.handle?.cancelled == true) continue

            safeRun("deferred systemTime task") { task.action.invoke() }

            task.interval?.let {
                task.time = systemNow + it
                systemTimeQueue.add(task)
            }
        }

        if (sector.isPaused)
            return

        val sectorNow = sector.clock.timestamp
        while (true) {
            val task = sectorTimeQueue.peek() ?: break
            if (task.time > sectorNow) break

            sectorTimeQueue.poll()

            if (task.handle?.cancelled == true) continue

            safeRun("deferred sectorTime task") { task.action.invoke() }

            task.interval?.let {
                task.time = sectorNow + it
                sectorTimeQueue.add(task)
            }
        }
    }
}