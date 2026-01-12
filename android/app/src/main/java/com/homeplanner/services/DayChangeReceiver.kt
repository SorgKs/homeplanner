package com.homeplanner.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.homeplanner.repository.OfflineRepository
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

/**
 * BroadcastReceiver для обработки будильника дня изменений.
 */
class DayChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DayChangeReceiver"
        private const val ACTION_DAY_CHANGE = "com.homeplanner.DAY_CHANGE"
        private const val DAY_START_HOUR = 4 // TODO: Получать из настроек
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DAY_CHANGE) {
            Log.d(TAG, "Day change alarm triggered, performing task recalculation")

            runBlocking {
                try {
                    // Получаем OfflineRepository из DI
                    val koin = GlobalContext.get()
                    val offlineRepository: OfflineRepository = koin.get()

                    // Пересчитываем задачи
                    val updated = offlineRepository.updateRecurringTasksForNewDay(DAY_START_HOUR)
                    if (updated) {
                        Log.i(TAG, "Tasks recalculated for new day")
                    } else {
                        Log.d(TAG, "No day change detected")
                    }

                    // Перепланируем следующий будильник
                    val scheduler = DayChangeScheduler(context)
                    scheduler.scheduleDailyRecalculation()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during day change recalculation", e)
                }
            }
        }
    }
}