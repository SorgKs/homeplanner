package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeplanner.viewmodel.SettingsState
import com.homeplanner.debug.ChunkSender

@Composable
fun DebugPanelSection(state: SettingsState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Отладочная панель",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Пользователей: ${state.users.size}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Задач: ${state.tasksCount}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Отладочных сообщений: ${state.debugMessagesCount}",
                style = MaterialTheme.typography.bodySmall
            )
            val chunkSenderStatus = com.homeplanner.debug.ChunkSender.getStatus()
            Text(
                text = "ChunkSender: running=${chunkSenderStatus.first}, lastResult=${chunkSenderStatus.second}, lastChunk=${chunkSenderStatus.third}",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { ChunkSender.forceSendNextChunk() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Принудительно отправить чанк")
            }
        }
    }
}