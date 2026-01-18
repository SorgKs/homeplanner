package com.homeplanner.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.homeplanner.BuildConfig
import com.homeplanner.api.ServerSyncApi
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.services.ConnectionMonitor
import com.homeplanner.utils.HashCalculator
import com.homeplanner.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker для периодической синхронизации каждый час.
 * Выполняет сверку хешей задач, пользователей и групп с сервером.
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val offlineRepository: com.homeplanner.repository.OfflineRepository by lazy {
        org.koin.core.context.GlobalContext.get().get()
    }

    private val connectionMonitor = ConnectionMonitor(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "SyncWorker started")

            // Проверяем сеть
            if (!connectionMonitor.isNetworkAvailableNow()) {
                Log.d(TAG, "No network available, skipping sync")
                return@withContext Result.retry()
            }

            // Получаем данные из локального хранилища
            val tasks = offlineRepository.loadTasksFromCache()
            val users = offlineRepository.loadUsersFromCache()
            val groups = offlineRepository.loadGroupsFromCache()

            // Вычисляем хеши
            val taskHashes = tasks.map { task ->
                val hash = HashCalculator.calculateTaskHash(task)
                Triple("task", task.id, hash)
            }

            val userHashes = users.map { user ->
                val hash = HashCalculator.calculateUserHash(user)
                Triple("user", user.id, hash)
            }

            val groupHashes = groups.map { group ->
                val hash = HashCalculator.calculateGroupHash(group)
                Triple("group", group.id, hash)
            }

            // Объединяем все хеши
            val allHashes = (taskHashes + userHashes + groupHashes).sortedBy { it.second }

            // Отправляем на сервер для сверки
            val serverApi = ServerSyncApi(baseUrl = BuildConfig.API_BASE_URL)
            val hashesArray = JSONArray()
            allHashes.forEach { (type, id, hash) ->
                val hashObj = JSONObject()
                hashObj.put("entity_type", type)
                hashObj.put("id", id)
                hashObj.put("hash", hash)
                hashesArray.put(hashObj)
            }
            val hashCheckRequest = JSONObject()
            hashCheckRequest.put("hashes", hashesArray)

            val response = serverApi.hashCheck(hashCheckRequest.toString()).getOrThrow()
            val responseJson = JSONObject(response)

            val differences = responseJson.optJSONArray("differences")
            if (differences != null && differences.length() > 0) {
                Log.d(TAG, "Found ${differences.length()} differences, resolving conflicts")

                // Разрешаем конфликты
                val conflictResolver = ConflictResolver()
                val resolvedTasks = conflictResolver.resolveConflicts(
                    differences,
                    serverApi,
                    tasks,
                    users,
                    groups
                )

                // Сохраняем разрешенные задачи
                if (resolvedTasks.isNotEmpty()) {
                    offlineRepository.saveTasksToCache(resolvedTasks)
                    Log.d(TAG, "Resolved and saved ${resolvedTasks.size} tasks")

                    // Показываем уведомление пользователю
                    showConflictNotification(applicationContext, resolvedTasks.size)
                }
            } else {
                Log.d(TAG, "Hashes match, no sync needed")
            }

            Log.d(TAG, "SyncWorker completed successfully")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "sync_worker"

        /**
         * Создает запрос на периодическую работу каждый час.
         */
        fun createPeriodicWorkRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        }
    }

    private fun showConflictNotification(context: Context, resolvedCount: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Создаем канал уведомлений, если нужно
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sync_conflicts",
                "Синхронизация конфликтов",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о разрешенных конфликтах синхронизации"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "sync_conflicts")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Синхронизация завершена")
            .setContentText("Разрешено конфликтов: $resolvedCount")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}