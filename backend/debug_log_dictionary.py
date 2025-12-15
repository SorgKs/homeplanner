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
        "context_schema": []
    },
    2: {
        "template": "Синхронизация успешно завершена",
        "level": "INFO",
        "context_schema": []
    },
    3: {
        "template": "Ошибка синхронизации: проблемы с сетью",
        "level": "ERROR",
        "context_schema": []
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
        "context_schema": []
    },
    12: {
        "template": "Соединение деградировало",
        "level": "WARN",
        "context_schema": []
    },
    
    # Задачи (коды 20-25)
    20: {
        "template": "Создана задача",
        "level": "INFO",
        "context_schema": []
    },
    21: {
        "template": "Задача обновлена",
        "level": "INFO",
        "context_schema": []
    },
    22: {
        "template": "Задача выполнена",
        "level": "INFO",
        "context_schema": []
    },
    23: {
        "template": "Задача удалена",
        "level": "INFO",
        "context_schema": []
    },
    24: {
        "template": "Выполнение задачи отменено",
        "level": "INFO",
        "context_schema": []
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
        "context_schema": []
    },
    42: {
        "template": "Кэш обновлен",
        "level": "INFO",
        "context_schema": []
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
    
    # Общие ошибки (коды 90-91)
    90: {
        "template": "Неизвестная ошибка",
        "level": "ERROR",
        "context_schema": []
    },
    91: {
        "template": "Исключение",
        "level": "ERROR",
        "context_schema": []
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

# Обратная совместимость: строковые коды для старого JSON API
STRING_CODE_TO_NUMERIC: Dict[str, int] = {
    "SYNC_START": 1,
    "SYNC_SUCCESS": 2,
    "SYNC_FAIL_NETWORK": 3,
    "SYNC_FAIL_500": 4,
    "SYNC_FAIL_503": 5,
    "SYNC_FAIL_409": 6,
    "SYNC_FAIL_400": 7,
    "SYNC_FAIL_401": 8,
    "SYNC_FAIL_403": 9,
    "CONNECTION_ONLINE": 10,
    "CONNECTION_OFFLINE": 11,
    "CONNECTION_DEGRADED": 12,
    "TASK_CREATE": 20,
    "TASK_UPDATE": 21,
    "TASK_COMPLETE": 22,
    "TASK_DELETE": 23,
    "TASK_UNCOMPLETE": 24,
    "QUEUE_ADD": 30,
    "QUEUE_CLEAR": 31,
    "QUEUE_SIZE": 32,
    "SYNC_QUEUE_EMPTY": 40,
    "SYNC_CACHE_UPDATED": 41,
    "CACHE_UPDATED": 42,
    "INCIDENTS_SENT": 50,
    "INCIDENTS_SEND_FAIL": 51,
    "LOGS_CLEANUP": 60,
    "ERROR_UNKNOWN": 90,
    "ERROR_EXCEPTION": 91,
    "APP_START": 100,
    "APP_STOP": 101,
    "UI_UPDATE": 102,
    "UNKNOWN": 0,
}


def get_message_description(code: str | int, dictionary_revision: str = "1.0") -> str:
    """Get description for a message code.
    
    Args:
        code: Message code from log entry (string or numeric)
        dictionary_revision: Dictionary revision (e.g., "1.0")
    
    Returns:
        Description text or code itself if not found
    """
    # For now, we only support version 1.0
    # In the future, this could check revision and use appropriate dictionary
    if dictionary_revision.startswith("1."):
        # Convert string code to numeric if needed
        if isinstance(code, str):
            numeric_code = STRING_CODE_TO_NUMERIC.get(code)
            if numeric_code is None:
                return code  # Unknown string code
            code = numeric_code
        
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
