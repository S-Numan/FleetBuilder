package fleetBuilder.util.deferredAction

data class Task(
    val action: () -> Unit,          // The deferred logic to run when this task comes due.
    var time: Long,                  // Scheduled-run time
    val interval: Long? = null,      // Repeat period in ms; null means run once, non-null reschedules after each run.
    val handle: TaskHandle? = null   // Cancellation token checked before each run; a canceled task is dropped instead of executed.
) : Comparable<Task> {
    override fun compareTo(other: Task): Int = time.compareTo(other.time)
}