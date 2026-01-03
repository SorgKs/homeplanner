package com.homeplanner.debug

/**
 * Схема контекста для сообщений логирования.
 *
 * Определяет порядок полей контекста для каждого кода сообщения.
 * Порядок должен точно соответствовать context_schema в backend/debug_log_dictionary.py.
 */
object ContextSchema {

    /**
     * Получить порядок полей контекста для указанного кода сообщения.
     *
     * @param messageCode Код сообщения (числовой)
     * @return Список имен полей в правильном порядке, или null если порядок не определен
     */
    fun getSchemaOrder(messageCode: Int): List<String>? {
        return when (messageCode) {
            // Синхронизация (коды 1-9)
            1 -> listOf("cache_size")                                    // SYNC_START
            2 -> listOf("server_tasks", "groups", "users")             // SYNC_SUCCESS

            // Подключение (коды 10-12)
            11 -> listOf("failures")                                    // CONNECTION_LOST
            12 -> listOf("failures")                                    // CONNECTION_DEGRADED

            // Задачи (коды 20-25)
            20 -> listOf("task_id", "title")                            // TASK_CREATE
            21 -> listOf("task_id", "title")                            // TASK_UPDATE
            22 -> listOf("task_id", "title")                            // TASK_COMPLETE
            23 -> listOf("task_id")                                     // TASK_DELETE
            24 -> listOf("task_id")                                     // TASK_CANCEL

            // Состояние (коды 40-42)
            41 -> listOf("tasks_count", "source", "queue_items")       // SYNC_CACHE_UPDATED

            // Пользователи (коды 400-408)
            401 -> listOf("users_count")                                 // USERS_RESPONSE_RECEIVED
            402 -> listOf("saved_count")                                 // USERS_SAVED_TO_CACHE
            403 -> listOf("url")                                         // USERS_HTTP_REQUEST
            404 -> listOf("code", "message")                             // USERS_HTTP_RESPONSE
            405 -> listOf("parsed_count")                                // USERS_PARSED
            406 -> listOf("body_length")                                 // USERS_BODY_LENGTH
            407 -> listOf("array_length")                                // USERS_ARRAY_LENGTH
            408 -> listOf("saved_count")                                 // USERS_SAVED_TO_PREFS

            // API операции (коды 200-299)
            205 -> listOf("task_id")                                    // API_TASK_PARSED
            213 -> listOf("task_id")                                    // API_TASK_NULL_REMINDER
            214 -> listOf("task_id")                                    // API_TASK_EMPTY_REMINDER

            // Синхронизация детальная (коды 300-399)
            314 -> listOf("tasks_count")                                // SAVE_TASKS_START
            315 -> listOf("cache_tasks_count")                          // SAVE_TASKS_TRANSFORMED
            316 -> listOf("inserted_count")                             // SAVE_TASKS_INSERTED
            317 -> listOf("updated_count")                              // SAVE_TASKS_UPDATED
            318 -> listOf("saved_count")                                // SAVE_TASKS_SUCCESS

            // Общие ошибки (коды 90-91)
            91 -> listOf("wait", "fact")                                // ERROR_EXCEPTION

            // Пустой контекст для остальных сообщений
            else -> null
        }
    }


}