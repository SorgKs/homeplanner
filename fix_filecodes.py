#!/usr/bin/env python3
"""Fix fileCodes in params to separate arguments."""

import os
import re
from pathlib import Path

# Mapping from filename to fileCode
FILE_CODES = {
    "AllTasksScreen.kt": 1,
    "AppDatabase.kt": 2,
    "AppState.kt": 3,
    "AppVersionSection.kt": 4,
    "Application.kt": 5,
    "BinaryLogEncoder.kt": 6,
    "BinaryLogEntry.kt": 7,
    "BinaryLogStorage.kt": 8,
    "BinaryLogger.kt": 9,
    "CacheSyncService.kt": 10,
    "ChunkSender.kt": 11,
    "ConnectionMonitor.kt": 12,
    "ConnectionStatus.kt": 13,
    "ConnectionStatusManager.kt": 14,
    "ContextSchema.kt": 15,
    "DebugPanelSection.kt": 16,
    "DeviceIdHelper.kt": 17,
    "DictionaryRevision.kt": 18,
    "Group.kt": 19,
    "GroupsAndUsersCacheRepository.kt": 20,
    "GroupsApi.kt": 21,
    "GroupsLocalApi.kt": 22,
    "LocalApi.kt": 23,
    "LogCleanupManager.kt": 24,
    "LogLevel.kt": 25,
    "MainActivity.kt": 26,
    "MainScreen.kt": 27,
    "Metadata.kt": 28,
    "MetadataDao.kt": 29,
    "NetworkConfig.kt": 30,
    "NetworkSettings.kt": 31,
    "NetworkSettingsSection.kt": 32,
    "NotificationHelper.kt": 33,
    "OfflineRepository.kt": 34,
    "QrCodeScanner.kt": 35,
    "QueueSyncService.kt": 36,
    "RecurringTaskUpdater.kt": 37,
    "ReminderActivity.kt": 38,
    "ReminderReceiver.kt": 39,
    "ReminderScheduler.kt": 40,
    "Screen.kt": 41,
    "ServerApi.kt": 42,
    "ServerApiBase.kt": 43,
    "ServerSyncApi.kt": 44,
    "ServerTaskApi.kt": 45,
    "SettingsScreen.kt": 46,
    "SettingsViewModel.kt": 47,
    "StatusBar.kt": 48,
    "StorageMetadataManager.kt": 49,
    "SyncModels.kt": 50,
    "SyncQueueDao.kt": 51,
    "SyncQueueItem.kt": 53,
    "SyncQueueRepository.kt": 54,
    "SyncService.kt": 55,
    "Task.kt": 56,
    "TaskCache.kt": 57,
    "TaskCacheDao.kt": 58,
    "TaskCacheRepository.kt": 59,
    "TaskDateCalculator.kt": 60,
    "TaskDialogs.kt": 61,
    "TaskFilter.kt": 62,
    "TaskFilterType.kt": 63,
    "TaskItem.kt": 64,
    "TaskListContent.kt": 65,
    "TaskSyncManager.kt": 66,
    "TaskUtils.kt": 67,
    "TaskValidationService.kt": 68,
    "TaskViewModel.kt": 69,
    "TodayScreen.kt": 70,
    "TodayTaskFilter.kt": 71,
    "User.kt": 72,
    "UserSelectionDialogScreen.kt": 73,
    "UserSelectionSection.kt": 74,
    "UserSettings.kt": 75,
    "UserSummary.kt": 77,
    "UsersApi.kt": 78,
    "UsersLocalApi.kt": 79,
    "WebSocketService.kt": 80,
}

def fix_file(file_path: Path):
    filename = file_path.name
    if filename not in FILE_CODES:
        return

    file_code = FILE_CODES[filename]
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Replace ,fileCode) with ), fileCode
    pattern = rf',{file_code}\)'
    replacement = f'), {file_code}'
    
    new_content = re.sub(pattern, replacement, content)
    
    if new_content != content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Fixed {file_path}")

def main():
    project_root = Path(__file__).parent
    android_src = project_root / "android" / "app" / "src" / "main" / "java"
    
    for root, dirs, files in os.walk(android_src):
        for file in files:
            if file.endswith('.kt'):
                file_path = Path(root) / file
                fix_file(file_path)
    
    print("Done")

if __name__ == "__main__":
    main()