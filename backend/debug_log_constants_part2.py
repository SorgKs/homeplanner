"""Dictionary of log message codes for decoding binary logs - Part 2.

This module contains the second part of log message constants.
Used for decoding logs received from Android clients.

Version: 1.0 (matches DictionaryRevision(1, 0) in Android app)
"""

from typing import Dict

# Dictionary of message codes to descriptions - Part 2 (codes 200-408)
# Format: numeric_code -> {"template": str, "level": str, "context_schema": list}
# Version 1.0 of the dictionary
LOG_MESSAGE_DICTIONARY_PART2: Dict[int, Dict[str, any]] = {
    # API операции (коды 200-299)
    200: {
        "template": "Загружены задачи из кэша",
        "level": "DEBUG",
        "context_schema": []
    },
    201: {
        "template": "getTasks: [STEP 1] Выполнение HTTP запроса к серверу",
        "level": "DEBUG",
        "context_schema": []
    },
    202: {
        "template": "getTasks: [STEP 2] Получен HTTP ответ",
        "level": "DEBUG",
        "context_schema": []
    },
    203: {
        "template": "getTasks: [STEP 3] Получено тело ответа",
        "level": "DEBUG",
        "context_schema": []
    },
    204: {
        "template": "getTasks: [STEP 4] Распарсен JSON массив",
        "level": "DEBUG",
        "context_schema": []
    },
    205: {
        "template": "getTasks: [STEP 4] Успешно распарсена задача",
        "level": "DEBUG",
        "context_schema": [
            {"name": "task_id", "type": "int"}
        ]
    },
    206: {
        "template": "getTasks: [STEP 5] Успешно распарсены задачи",
        "level": "DEBUG",
        "context_schema": []
    },
    207: {
        "template": "Создание задачи",
        "level": "DEBUG",
        "context_schema": []
    },
    208: {
        "template": "Отправка POST запроса",
        "level": "DEBUG",
        "context_schema": []
    },
    209: {
        "template": "Получен ответ",
        "level": "DEBUG",
        "context_schema": []
    },
    210: {
        "template": "Получено тело ответа",
        "level": "DEBUG",
        "context_schema": []
    },
    211: {
        "template": "Обновление задачи",
        "level": "DEBUG",
        "context_schema": []
    },
    212: {
        "template": "Отправка PATCH запроса",
        "level": "DEBUG",
        "context_schema": []
    },
    213: {
        "template": "Задача имеет null reminder_time",
        "level": "ERROR",
        "context_schema": [
            {"name": "task_id", "type": "int"}
        ]
    },
    214: {
        "template": "Задача имеет пустой reminder_time",
        "level": "ERROR",
        "context_schema": [
            {"name": "task_id", "type": "int"}
        ]
    },

    # Синхронизация детальная (коды 300-399)
    300: {
        "template": "syncStateBeforeRecalculation: синхронизация ожидающих операций",
        "level": "DEBUG",
        "context_schema": []
    },
    301: {
        "template": "syncStateBeforeRecalculation: syncQueue завершился с ошибкой, но продолжается",
        "level": "WARN",
        "context_schema": []
    },
    302: {
        "template": "syncStateBeforeRecalculation: загрузка задач с сервера",
        "level": "DEBUG",
        "context_schema": []
    },
    303: {
        "template": "syncCacheWithServer: [STEP 1] Начало синхронизации",
        "level": "DEBUG",
        "context_schema": []
    },
    304: {
        "template": "syncCacheWithServer: [STEP 2] Получены задачи с сервера",
        "level": "DEBUG",
        "context_schema": []
    },
    305: {
        "template": "syncCacheWithServer: загружены задачи из кэша",
        "level": "DEBUG",
        "context_schema": []
    },
    306: {
        "template": "syncCacheWithServer: [STEP 6] Несоответствие хеша, обновление кэша",
        "level": "DEBUG",
        "context_schema": []
    },
    307: {
        "template": "syncCacheWithServer: [STEP 7] Сохранение задач в кэш",
        "level": "DEBUG",
        "context_schema": []
    },
    308: {
        "template": "syncCacheWithServer: [STEP 8] Успешно сохранены задачи в кэш",
        "level": "DEBUG",
        "context_schema": []
    },
    309: {
        "template": "syncCacheWithServer: [STEP 6] Хеши совпадают, кэш актуален",
        "level": "DEBUG",
        "context_schema": []
    },
    310: {
        "template": "syncCacheWithServer: синхронизация очереди завершилась с ошибкой, но продолжается",
        "level": "WARN",
        "context_schema": []
    },
    311: {
        "template": "syncCacheWithServer: загружены группы",
        "level": "DEBUG",
        "context_schema": []
    },
    312: {
        "template": "syncCacheWithServer: загружены пользователи",
        "level": "DEBUG",
        "context_schema": []
    },
    313: {
        "template": "syncCacheWithServer: успешно завершено",
        "level": "DEBUG",
        "context_schema": []
    },
    314: {
        "template": "saveTasksToCache: [STEP 1] Начало сохранения задач в кэш",
        "level": "DEBUG",
        "context_schema": [
            {"name": "tasks_count", "type": "int"}
        ]
    },
    315: {
        "template": "saveTasksToCache: [STEP 2] Задачи преобразованы в TaskCache",
        "level": "DEBUG",
        "context_schema": [
            {"name": "cache_tasks_count", "type": "int"}
        ]
    },
    316: {
        "template": "saveTasksToCache: [STEP 3] Задачи вставлены в базу данных",
        "level": "DEBUG",
        "context_schema": [
            {"name": "inserted_count", "type": "int"}
        ]
    },
    317: {
        "template": "saveTasksToCache: [STEP 4] Обновлен lastAccessed для всех задач",
        "level": "DEBUG",
        "context_schema": [
            {"name": "updated_count", "type": "int"}
        ]
    },
    318: {
        "template": "saveTasksToCache: [STEP 5] Успешно сохранено задач в кэш",
        "level": "DEBUG",
        "context_schema": [
            {"name": "saved_count", "type": "int"}
        ]
    },
    319: {
        "template": "syncCacheWithServer: загрузка задач с сервера для полной синхронизации",
        "level": "DEBUG",
        "context_schema": []
    },
    320: {
        "template": "syncCacheWithServer: хеши совпадают, кэш не обновлен",
        "level": "DEBUG",
        "context_schema": []
    },

    # Пользователи (коды 400-408)
    400: {
        "template": "syncCacheWithServer: вызов загрузки пользователей с сервера",
        "level": "DEBUG",
        "context_schema": []
    },
    401: {
        "template": "syncCacheWithServer: получен ответ от сервера с пользователями",
        "level": "DEBUG",
        "context_schema": [
            {"name": "users_count", "type": "int"}
        ]
    },
    402: {
        "template": "syncCacheWithServer: пользователи сохранены в локальный кеш",
        "level": "DEBUG",
        "context_schema": [
            {"name": "saved_count", "type": "int"}
        ]
    },
    403: {
        "template": "UsersServerApi: HTTP запрос к серверу",
        "level": "DEBUG",
        "context_schema": [
            {"name": "url", "type": "string"}
        ]
    },
    404: {
        "template": "UsersServerApi: получен HTTP ответ",
        "level": "DEBUG",
        "context_schema": [
            {"name": "code", "type": "int"},
            {"name": "message", "type": "string"}
        ]
    },
    405: {
        "template": "UsersServerApi: распарсено пользователей",
        "level": "DEBUG",
        "context_schema": [
            {"name": "parsed_count", "type": "int"}
        ]
    },
    406: {
        "template": "UsersServerApi: длина body ответа",
        "level": "DEBUG",
        "context_schema": [
            {"name": "body_length", "type": "int"}
        ]
    },
    407: {
        "template": "UsersServerApi: длина JSONArray",
        "level": "DEBUG",
        "context_schema": [
            {"name": "array_length", "type": "int"}
        ]
    },
    408: {
        "template": "saveUsersToCache: пользователи сохранены в SharedPreferences",
        "level": "DEBUG",
        "context_schema": [
            {"name": "saved_count", "type": "int"}
        ]
    },
}