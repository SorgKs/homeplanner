package ru.homeplanner.common

import java.util.concurrent.atomic.AtomicBoolean

object AppState {
    private val needsTasksRefresh = AtomicBoolean(false)

    fun setTasksRefreshNeeded(needed: Boolean) {
        needsTasksRefresh.set(needed)
    }

    fun checkAndClearTasksRefresh(): Boolean {
        return needsTasksRefresh.getAndSet(false)
    }
}
