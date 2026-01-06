"""Dictionary of log message codes for decoding binary logs - Part 1.

This module contains the first part of log message constants.
Used for decoding logs received from Android clients.

Version: 1.0 (matches DictionaryRevision(1, 0) in Android app)
"""

from typing import Dict

# Dictionary of file codes for logging (byte codes mapped to file names)
# Version 2.0 - file codes are assigned sequentially from 1
FILE_CODES: Dict[int, str] = {
    1: "AllTasksScreen.kt",
    2: "AppDatabase.kt",
    3: "AppState.kt",
    4: "AppVersionSection.kt",
    5: "Application.kt",
    6: "BinaryLogEncoder.kt",
    7: "BinaryLogEntry.kt",
    8: "BinaryLogStorage.kt",
    9: "BinaryLogger.kt",
    10: "CacheSyncService.kt",
    11: "ChunkSender.kt",
    12: "ConnectionMonitor.kt",
    13: "ConnectionStatus.kt",
    14: "ConnectionStatusManager.kt",
    15: "ContextSchema.kt",
    16: "DebugPanelSection.kt",
    17: "DeviceIdHelper.kt",
    18: "DictionaryRevision.kt",
    19: "Group.kt",
    20: "GroupsAndUsersCacheRepository.kt",
    21: "GroupsApi.kt",
    22: "GroupsLocalApi.kt",
    23: "LocalApi.kt",
    24: "LogCleanupManager.kt",
    25: "LogLevel.kt",
    26: "MainActivity.kt",
    27: "MainScreen.kt",
    28: "Metadata.kt",
    29: "MetadataDao.kt",
    30: "NetworkConfig.kt",
    31: "NetworkSettings.kt",
    32: "NetworkSettingsSection.kt",
    33: "NotificationHelper.kt",
    34: "OfflineRepository.kt",
    35: "QrCodeScanner.kt",
    36: "QueueSyncService.kt",
    37: "RecurringTaskUpdater.kt",
    38: "ReminderActivity.kt",
    39: "ReminderReceiver.kt",
    40: "ReminderScheduler.kt",
    41: "Screen.kt",
    42: "ServerApi.kt",
    43: "ServerApiBase.kt",
    44: "ServerSyncApi.kt",
    45: "ServerTaskApi.kt",
    46: "SettingsScreen.kt",
    47: "SettingsViewModel.kt",
    48: "StatusBar.kt",
    49: "StorageMetadataManager.kt",
    50: "SyncModels.kt",
    51: "SyncQueueDao.kt",
    53: "SyncQueueItem.kt",
    54: "SyncQueueRepository.kt",
    55: "SyncService.kt",
    56: "Task.kt",
    57: "TaskCache.kt",
    58: "TaskCacheDao.kt",
    59: "TaskCacheRepository.kt",
    60: "TaskDateCalculator.kt",
    61: "TaskDialogs.kt",
    62: "TaskFilter.kt",
    63: "TaskFilterType.kt",
    64: "TaskItem.kt",
    65: "TaskListContent.kt",
    66: "TaskSyncManager.kt",
    67: "TaskUtils.kt",
    68: "TaskValidationService.kt",
    69: "TaskViewModel.kt",
    70: "TodayScreen.kt",
    71: "TodayTaskFilter.kt",
    72: "User.kt",
    73: "UserSelectionDialogScreen.kt",
    74: "UserSelectionSection.kt",
    75: "UserSettings.kt",
    77: "UserSummary.kt",
    78: "UsersApi.kt",
    79: "UsersLocalApi.kt",
    80: "WebSocketService.kt",
    129: "UnknownFile.kt",
    154: "AnotherUnknown.kt",
}

# Dictionary of message codes to descriptions - Part 1 (codes 0-199)
# Format: numeric_code -> {"template": str, "level": str, "context_schema": list}
# Version 1.0 of the dictionary
LOG_MESSAGE_DICTIONARY_PART1: Dict[int, Dict[str, any]] = {


    # Синхронизация (коды 1-9)
    1: {
        "template": "Синхронизация начата (размер кэша: %cache_size%)",
        "level": "INFO",
        "context_schema": [
            {"name": "cache_size", "type": "int"}
        ]
    },
    2: {
        "template": "Синхронизация успешно завершена: сервер %server_tasks% задач, %groups% групп, %users% пользователей",
        "level": "INFO",
        "context_schema": [
            {"name": "server_tasks", "type": "int"},
            {"name": "groups", "type": "int"},
            {"name": "users", "type": "int"}
        ]
    },
    3: {
        "template": "Ошибка синхронизации: проблемы с сетью (%error%)",
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
        "template": "Соединение потеряно (неудачных попыток: %failures%)",
        "level": "WARN",
        "context_schema": [
            {"name": "failures", "type": "int"}
        ]
    },
    12: {
        "template": "Соединение деградировало (неудачных попыток: %failures%)",
        "level": "WARN",
        "context_schema": [
            {"name": "failures", "type": "int"}
        ]
    },

    # Задачи (коды 20-25)
    20: {
        "template": "Создана задача %task_id%: %title%",
        "level": "INFO",
        "context_schema": [
            {"name": "task_id", "type": "int"},
            {"name": "title", "type": "string"}
        ]
    },
    21: {
        "template": "Задача %task_id% обновлена: %title%",
        "level": "INFO",
        "context_schema": [
            {"name": "task_id", "type": "int"},
            {"name": "title", "type": "string"}
        ]
    },
    22: {
        "template": "Задача %task_id% выполнена: %title%",
        "level": "INFO",
        "context_schema": [
            {"name": "task_id", "type": "int"},
            {"name": "title", "type": "string"}
        ]
    },
    23: {
        "template": "Задача %task_id% удалена",
        "level": "INFO",
        "context_schema": [
            {"name": "task_id", "type": "int"}
        ]
    },
    24: {
        "template": "Выполнение задачи %task_id% отменено",
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
        "template": "Кэш обновлен после синхронизации: %tasks_count% задач, источник %source%, элементов в очереди %queue_items%",
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
}