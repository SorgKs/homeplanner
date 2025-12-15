package com.homeplanner.debug

/**
 * Коды сообщений для логирования.
 * 
 * Синхронизирован со словарем на сервере (backend/debug_log_dictionary.py).
 * Версия словаря: 1.0
 */
object LogMessageCode {
    // Синхронизация (коды 1-9)
    const val SYNC_START = "SYNC_START"
    const val SYNC_SUCCESS = "SYNC_SUCCESS"
    const val SYNC_FAIL_NETWORK = "SYNC_FAIL_NETWORK"
    const val SYNC_FAIL_500 = "SYNC_FAIL_500"
    const val SYNC_FAIL_503 = "SYNC_FAIL_503"
    const val SYNC_FAIL_409 = "SYNC_FAIL_409"
    const val SYNC_FAIL_400 = "SYNC_FAIL_400"
    const val SYNC_FAIL_401 = "SYNC_FAIL_401"
    const val SYNC_FAIL_403 = "SYNC_FAIL_403"
    
    // Подключение (коды 10-12)
    const val CONNECTION_ONLINE = "CONNECTION_ONLINE"
    const val CONNECTION_OFFLINE = "CONNECTION_OFFLINE"
    const val CONNECTION_DEGRADED = "CONNECTION_DEGRADED"
    
    // Задачи (коды 20-25)
    const val TASK_CREATE = "TASK_CREATE"
    const val TASK_UPDATE = "TASK_UPDATE"
    const val TASK_COMPLETE = "TASK_COMPLETE"
    const val TASK_DELETE = "TASK_DELETE"
    const val TASK_UNCOMPLETE = "TASK_UNCOMPLETE"
    
    // Очередь (коды 30-32)
    const val QUEUE_ADD = "QUEUE_ADD"
    const val QUEUE_CLEAR = "QUEUE_CLEAR"
    const val QUEUE_SIZE = "QUEUE_SIZE"
    
    // Состояние (коды 40-42)
    const val SYNC_QUEUE_EMPTY = "SYNC_QUEUE_EMPTY"
    const val SYNC_CACHE_UPDATED = "SYNC_CACHE_UPDATED"
    const val CACHE_UPDATED = "CACHE_UPDATED"
    
    // Инциденты (коды 50-51)
    const val INCIDENTS_SENT = "INCIDENTS_SENT"
    const val INCIDENTS_SEND_FAIL = "INCIDENTS_SEND_FAIL"
    
    // Очистка (код 60)
    const val LOGS_CLEANUP = "LOGS_CLEANUP"
    
    // Общие ошибки (коды 90-91)
    const val ERROR_UNKNOWN = "ERROR_UNKNOWN"
    const val ERROR_EXCEPTION = "ERROR_EXCEPTION"
    
    // Общие события (коды 100-102)
    const val APP_START = "APP_START"
    const val APP_STOP = "APP_STOP"
    const val UI_UPDATE = "UI_UPDATE"
    
    // Fallback (код 0)
    const val UNKNOWN = "UNKNOWN"
}
