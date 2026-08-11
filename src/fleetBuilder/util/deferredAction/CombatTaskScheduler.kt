package fleetBuilder.util.deferredAction

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.input.InputEventAPI
import fleetBuilder.util.deferredAction.DeferredActionUtils.safeRun
import java.util.*

class CombatTaskScheduler : BaseEveryFrameCombatPlugin() {

    companion object {
        private var active: CombatTaskScheduler? = null

        /**
         * Runs [action] once, [delay] milliseconds from now.
         *
         * If [systemTime] is false, timing follows simulation time (seconds of combat elapsed), and stops advancing while the game is paused.
         *
         * If [systemTime] is true, timing follows [System.nanoTime] in milliseconds, not combat-time, and keeps advancing while the game is paused.
         *
         * Does not persist between battles
         */
        @JvmStatic
        fun performLater(delay: Long = 0, systemTime: Boolean = false, action: () -> Unit): TaskHandle {
            val handle = TaskHandle()
            val inst = active ?: return handle
            val engine = inst.engine ?: return handle

            val task = Task(
                action = action,
                time =
                    if (systemTime) System.nanoTime() + delay * 1_000_000L
                    else engine.getTotalElapsedTime(false).toLong() + delay,
                interval = null,
                handle = handle
            )

            if (systemTime)
                inst.systemTimeQueue.add(task)
            else
                inst.combatTimeQueue.add(task)

            return handle
        }

        /**
         * Runs [action] every [interval] milliseconds, starting after one interval.
         *
         * If [systemTime] is false, timing follows simulation time (seconds of combat elapsed), and stops advancing while the game is paused.
         *
         * If [systemTime] is true, timing follows [System.nanoTime] in milliseconds, not combat-time, and keeps advancing while the game is paused.
         *
         * Does not persist between battles
         */
        @JvmStatic
        fun performEvery(interval: Long = 0, systemTime: Boolean = false, action: (TaskHandle) -> Unit): TaskHandle {
            val handle = TaskHandle()
            val inst = active ?: return handle
            val engine = inst.engine ?: return handle

            val task = Task(
                action = { action(handle) }, // inject handle into lambda
                time =
                    if (systemTime) System.nanoTime() + interval * 1_000_000L
                    else engine.getTotalElapsedTime(false).toLong() + interval,
                interval = interval * 1_000_000L,
                handle = handle
            )

            if (systemTime)
                inst.systemTimeQueue.add(task)
            else
                inst.combatTimeQueue.add(task)

            return handle
        }

        @JvmStatic
        fun performOnUnpause(action: () -> Unit) {
            active?.onUnpause?.add(action)
        }

        private val onStart = mutableListOf<() -> Unit>()

        /**
         * Happens more frequently than you might imagine.
         */
        @JvmStatic
        fun performOnPlayerBattleStart(action: () -> Unit) {
            onStart.add(action)
        }
    }

    private var engine: CombatEngineAPI? = null

    private val combatTimeQueue = PriorityQueue<Task>()
    private val systemTimeQueue = PriorityQueue<Task>()
    private val onUnpause = mutableListOf<() -> Unit>()
    private var wasPaused = false

    private var init = false
    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        if (!init) {
            this.engine = Global.getCombatEngine() ?: return

            systemTimeQueue.clear()
            combatTimeQueue.clear()

            onUnpause.clear()
            wasPaused = false

            active = this

            SectorTaskScheduler.battleStarted()

            val starters = onStart.toList()
            onStart.clear()
            starters.forEach { safeRun("onPlayerBattleStart callback") { it.invoke() } }

            init = true
        }

        val engine = this.engine ?: return
        val paused = engine.isPaused

        // detect unpause
        if (wasPaused && !paused) {
            val callbacks = onUnpause.toList()
            onUnpause.clear()
            callbacks.forEach { safeRun("onUnpause callback") { it.invoke() } }
        }
        wasPaused = paused

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

        if (paused) return

        val combatNow = engine.getTotalElapsedTime(false).toLong()
        while (true) {
            val task = combatTimeQueue.peek() ?: break
            if (task.time > combatNow) break

            combatTimeQueue.poll()

            if (task.handle?.cancelled == true) continue

            safeRun("deferred combatTime task") { task.action.invoke() }

            task.interval?.let {
                task.time = combatNow + it
                combatTimeQueue.add(task)
            }
        }
    }
}