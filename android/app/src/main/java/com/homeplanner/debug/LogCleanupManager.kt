package com.homeplanner.debug

import android.content.Context
import com.homeplanner.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Менеджер автоматической очистки старых логов.
 * 
 * Согласно LOGGING_FORMAT.md:
 * - Обычные логи удаляются через 7 дней после создания
 * - Очистка запускается при старте приложения и периодически
 * - Только для дебаг-версий
 */
class LogCleanupManager private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var cleanupJob: Job? = null
    private var isRunning = false
    
    companion object {
        private const val CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L  // 24 hours
        private const val LOG_RETENTION_DAYS = 7
        private var instance: LogCleanupManager? = null

        /**
         * Запустить менеджер очистки логов.
         * Только для дебаг-версий.
         */
        fun start(context: Context) {
            if (!BuildConfig.DEBUG) {
                return
            }

            synchronized(this) {
                if (instance != null) {
                    return
                }

                val scope = CoroutineScope(Dispatchers.IO)
                instance = LogCleanupManager(context, scope)
                instance!!.startInternal()
            }
        }

        /**
         * Получить экземпляр LogCleanupManager.
         */
        fun getInstance(): LogCleanupManager? = instance

        /**
         * Остановить менеджер очистки.
         */
        fun stop() {
            synchronized(this) {
                instance?.stopInternal()
                instance = null
            }
        }
    }

    private fun startInternal() {
        if (isRunning) return
        isRunning = true

        // Запустить немедленную очистку при старте
        scope.launch {
            cleanupOldLogs()
        }

        // Запустить периодическую очистку
        cleanupJob = scope.launch {
            cleanupLoop()
        }
    }

    private fun stopInternal() {
        if (!isRunning) return
        isRunning = false

        cleanupJob?.cancel()
        cleanupJob = null
    }

    /**
     * Цикл периодической очистки логов.
     */
    private suspend fun cleanupLoop() {
        while (scope.isActive && isRunning) {
            try {
                delay(CLEANUP_INTERVAL_MS)
                cleanupOldLogs()
            } catch (e: Exception) {
                // Не логируем ошибки, чтобы не зациклиться
            }
        }
    }

    /**
     * Очистить старые логи.
     */
    suspend fun cleanupOldLogs() {
        try {
            val logsDir = File(context.filesDir, "debug_logs_bin")
            
            if (!logsDir.exists()) {
                return
            }

            val now = System.currentTimeMillis()
            val retentionPeriodMs = TimeUnit.DAYS.toMillis(LOG_RETENTION_DAYS.toLong())
            val cutoffTime = now - retentionPeriodMs

            var deletedCount = 0
            
            // Удалить старые файлы логов
            logsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("debug_logs_") && file.name.endsWith(".bin")) {
                    // Проверяем время последней модификации файла
                    if (file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }
            }

            // Логируем результат очистки (если есть удалённые файлы)
            if (deletedCount > 0) {
                val logger = BinaryLogger.getInstance()
                logger?.log(
                    LogMessageCode.LOGS_CLEANUP,
                    mapOf(
                        "deletedCount" to deletedCount,
                        "retentionDays" to LOG_RETENTION_DAYS,
                        "timestamp" to now
                    )
                )
            }
        } catch (e: Exception) {
            // Не логируем ошибки, чтобы не зациклиться
        }
    }
}

