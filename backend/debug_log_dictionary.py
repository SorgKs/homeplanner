"""Dictionary of log message codes for decoding binary logs.

This dictionary maps message codes to their text descriptions.
Used for decoding logs received from Android clients.

Version: 1.0 (matches DictionaryRevision(1, 0) in Android app)
"""

from typing import Dict

# Dictionary of message codes to descriptions
# Format: numeric_code -> {"template": str, "level": str, "context_schema": list}
# Version 1.0 of the dictionary
LOG_MESSAGE_DICTIONARY: Dict[int, Dict[str, any]] = {
    # Синхронизация (коды 1-9)
    1: {
        "template": "Синхронизация начата",
        "level": "INFO",
        "context_schema": [
            {"name": "cache_size", "type": "int"}
        ]
    },
    2: {
        "template": "Синхронизация успешно завершена",
        "level": "INFO",
        "context_schema": [
            {"name": "server_tasks", "type": "int"},
            {"name": "groups", "type": "int"},
            {"name": "users", "type": "int"}
        ]
    },
    3: {
        "template": "Ошибка синхронизации: проблемы с сетью",
        "level": "ERROR",
        "context_schema": [
            {"name": "error", "type": "string"}
        ]
    },
    4: {
        "template": "Ошибка синхронизации: ошибка сервера 500",
        "level": "ERROR",
        "context_schema": []
    },
    5: {
        "template": "Ошибка синхронизации: сервис недоступен 503",
        "level": "ERROR",
        "context_schema": []
    },
    6: {
        "template": "Ошибка синхронизации: конфликт 409",
        "level": "ERROR",
        "context_schema": []
    },
    7: {
        "template": "Ошибка синхронизации: ошибка валидации 400",
        "level": "ERROR",
        "context_schema": []
    },
    8: {
        "template": "Ошибка синхронизации: ошибка авторизации 401",
        "level": "ERROR",
        "context_schema": []
    },
    9: {
        "template": "Ошибка синхронизации: доступ запрещен 403",
        "level": "ERROR",
        "context_schema": []
    },
    
    # Подключение (коды 10-12)
    10: {
        "template": "Соединение установлено",
        "level": "INFO",
        "context_schema": []
    },
    11: {
        "template": "Соединение потеряно",
        "level": "WARN",
        "context_schema": [
            {"name": "failures", "type": "int"}
        ]
    },
    12: {
        "template": "Соединение деградировало",
        "level": "WARN",
        "context_schema": [
            {"name": "failures", "type": "int"}
        ]
    },
    
    # Задачи (коды 20-25)
    20: {
        "template": "Создана задача",
        "level": "INFO",
        "context_schema": [
            {"name": "task_id", "type": "int"},
            {"name": "title", "type": "string"}
        ]
    },
    21: {
        "template": "Задача обновлена",
        "level": "INFO",
        "context_schema": [
            {"name": "task_id", "type": "int"},
            {"name": "title", "type": "string"}
        ]
    },
    22: {
        "template": "Задача выполнена",
        "level": "INFO",
        "context_schema": [
            {"name": "task_id", "type": "int"},
            {"name": "title", "type": "string"}
        ]
    },
    23: {
        "template": "Задача удалена",
        "level": "INFO",
        "context_schema": [
            {"name": "task_id", "type": "int"}
        ]
    },
    24: {
        "template": "Выполнение задачи отменено",
        "level": "INFO",
        "context_schema": [
            {"name": "task_id", "type": "int"}
        ]
    },
    
    # Очередь (коды 30-32)
    30: {
        "template": "Операция добавлена в очередь",
        "level": "DEBUG",
        "context_schema": []
    },
    31: {
        "template": "Очередь очищена",
        "level": "INFO",
        "context_schema": []
    },
    32: {
        "template": "Размер очереди",
        "level": "DEBUG",
        "context_schema": []
    },
    
    # Состояние (коды 40-42)
    40: {
        "template": "Очередь синхронизации пуста",
        "level": "DEBUG",
        "context_schema": []
    },
    41: {
        "template": "Кэш обновлен после синхронизации",
        "level": "INFO",
        "context_schema": [
            {"name": "tasks_count", "type": "int"},
            {"name": "source", "type": "string"},
            {"name": "queue_items", "type": "int"}
        ]
    },
    
    # Инциденты (коды 50-51)
    50: {
        "template": "Инциденты отправлены на сервер",
        "level": "INFO",
        "context_schema": []
    },
    51: {
        "template": "Ошибка отправки инцидентов на сервер",
        "level": "ERROR",
        "context_schema": []
    },
    
    # Очистка (коды 60-61)
    60: {
        "template": "Очистка старых логов",
        "level": "INFO",
        "context_schema": []
    },
    
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
    
    # Общие ошибки (коды 90-91)
    90: {
        "template": "Неизвестная ошибка",
        "level": "ERROR",
        "context_schema": []
    },
    91: {
        "template": "Исключение: ожидалось %wait%, фактически %fact%",
        "level": "ERROR",
        "context_schema": [
            {"name": "wait", "type": "string"},
            {"name": "fact", "type": "string"}
        ]
    },
    
    # Общие события (коды 100-102)
    100: {
        "template": "Приложение запущено",
        "level": "INFO",
        "context_schema": []
    },
    101: {
        "template": "Приложение остановлено",
        "level": "INFO",
        "context_schema": []
    },
    102: {
        "template": "Обновление интерфейса",
        "level": "DEBUG",
        "context_schema": []
    },
    
    # Fallback (код 0)
    0: {
        "template": "Неизвестное сообщение",
        "level": "DEBUG",
        "context_schema": []
    },
}


def get_message_description(code: int, dictionary_revision: str = "1.0") -> str:
    """Get description for a message code.
    
    Args:
        code: Numeric message code from log entry
        dictionary_revision: Dictionary revision (e.g., "1.0")
    
    Returns:
        Description text or code itself if not found
    """
    # For now, we only support version 1.0
    # In the future, this could check revision and use appropriate dictionary
    if dictionary_revision.startswith("1."):
        # Get message info from dictionary
        message_info = LOG_MESSAGE_DICTIONARY.get(code)
        if message_info:
            return message_info["template"]
        
        return f"Unknown message code: {code}"
    
    # Fallback: return code itself
    return str(code)


def get_message_level(code: int, dictionary_revision: str = "1.0") -> str:
    """Get log level for a message code.
    
    Args:
        code: Numeric message code
        dictionary_revision: Dictionary revision (e.g., "1.0")
    
    Returns:
        Log level string (DEBUG, INFO, WARN, ERROR)
    """
    if dictionary_revision.startswith("1."):
        message_info = LOG_MESSAGE_DICTIONARY.get(code)
        if message_info:
            return message_info["level"]
    
    return "INFO"  # Default level
