package fleetBuilder.util.deferredAction

class TaskHandle {
    var cancelled = false
    fun cancel() {
        cancelled = true
    }
}