package com.oprawa.twitch20.update

/** Orders queued updater operations independently of the worker mutex. */
internal class UpdateOperationGate {
    private var generation = 0L

    @Synchronized fun issue(): Long = ++generation

    @Synchronized fun invalidate(action: () -> Unit = {}) {
        generation++
        action()
    }

    @Synchronized fun admit(token: Long, action: () -> Unit): Boolean {
        if (token != generation) return false
        action()
        return true
    }
}
