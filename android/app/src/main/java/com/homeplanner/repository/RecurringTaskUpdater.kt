package com.homeplanner.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.homeplanner.model.Task
import com.homeplanner.utils.TaskDateCalculator

/**
 * Обновляет повторяющиеся задачи для нового дня.
 */
class RecurringTaskUpdater(
    private val context: Context,
    private val taskCacheRepository: TaskCacheRepository
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "RecurringTaskUpdater"
        private const val KEY_LAST_UPDATE = "tasks_last_update"
        private const val KEY_LAST_DAY_START_HOUR = "tasks_last_day_start_hour"
    }

    /**
     * Получить timestamp последнего пересчёта задач или null, если ещё не пересчитывали.
     */
    suspend fun getLastUpdateTimestamp(): Long? {
        return try {
            if (!prefs.contains(KEY_LAST_UPDATE)) {
                null
            } else {
                prefs.getLong(KEY_LAST_UPDATE, 0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading last update timestamp", e)
            null
        }
    }

    /**
     * Получить hour начала дня, который использовался при последнем пересчёте.
     */
    suspend fun getLastDayStartHour(): Int? {
        return try {
            if (!prefs.contains(KEY_LAST_DAY_START_HOUR)) {
                null
            } else {
                prefs.getInt(KEY_LAST_DAY_START_HOUR, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading last dayStartHour", e)
            null
        }
    }

    /**
     * Сохранить данные о последнем пересчёте задач.
     */
    suspend fun setLastUpdateTimestamp(timestamp: Long, dayStartHour: Int) {
        try {
            prefs.edit()
                .putLong(KEY_LAST_UPDATE, timestamp)
                .putInt(KEY_LAST_DAY_START_HOUR, dayStartHour)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving last update timestamp", e)
        }
    }

    /**
     * Пересчёт задач при наступлении нового логического дня.
     *
     * Возвращает true, если пересчёт был выполнен (и кэш обновлён),
     * и false, если новый день ещё не наступил или пересчитывать нечего.
     *
     * ВАЖНО:
     * - При пустом кэше `last_update` не проверяется, пересчёт не выполняется.
     * - Значение `last_update` считается источником истины на стороне сервера и
     *   должно устанавливаться при получении данных от сервера.
     */
    suspend fun updateRecurringTasksForNewDay(dayStartHour: Int): Boolean {
        return try {
            val now = System.currentTimeMillis()

            // Сначала проверяем содержимое кэша: если нечего пересчитывать, сразу выходим.
            val cachedTasks = taskCacheRepository.loadTasksFromCache()
            if (cachedTasks.isEmpty()) {
                Log.d(TAG, "updateRecurringTasksForNewDay: cache is empty, nothing to recalculate")
                return false
            }

            val lastUpdate = getLastUpdateTimestamp()
            val lastDayStartHour = getLastDayStartHour()

            val isNewDay = TaskDateCalculator.isNewDay(
                lastUpdateMillis = lastUpdate,
                nowMillis = now,
                lastDayStartHour = lastDayStartHour,
                currentDayStartHour = dayStartHour
            )

            if (!isNewDay) {
                Log.d(TAG, "updateRecurringTasksForNewDay: same logical day, skipping")
                return false
            }

            val updatedTasks = mutableListOf<Task>()
            for (task in cachedTasks) {
                if (!task.completed) {
                    // Ничего не меняем
                    updatedTasks.add(task)
                    continue
                }

                when (task.taskType) {
                    "one_time" -> {
                        // Завершённые one_time становятся неактивными, дата не меняется
                        updatedTasks.add(
                            task.copy(
                                enabled = false,
                                // completed остаётся true — как и на сервере перед "уходом" задачи
                            )
                        )
                    }
                    "recurring", "interval" -> {
                        val nextReminder = TaskDateCalculator.calculateNextReminderTime(
                            task = task,
                            nowMillis = now,
                            dayStartHour = dayStartHour
                        )
                        updatedTasks.add(
                            task.copy(
                                reminderTime = nextReminder,
                                completed = false
                            )
                        )
                    }
                    else -> {
                        // Неизвестный тип — оставляем без изменений
                        updatedTasks.add(task)
                    }
                }
            }

            // Перезаписываем кэш пересчитанными задачами
            taskCacheRepository.saveTasksToCache(updatedTasks)
            setLastUpdateTimestamp(now, dayStartHour)

            Log.d(TAG, "updateRecurringTasksForNewDay: recalculated ${updatedTasks.size} tasks")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating recurring tasks for new day", e)
            false
        }
    }
}