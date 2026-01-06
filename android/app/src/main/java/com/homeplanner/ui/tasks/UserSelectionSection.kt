package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeplanner.model.User
import com.homeplanner.NetworkConfig
import com.homeplanner.SelectedUser

@Composable
fun UserSelectionSection(
    users: List<User>,
    selectedUser: SelectedUser?,
    isLoading: Boolean,
    networkConfig: NetworkConfig?,
    onSelectUser: () -> Unit,
    onClearUser: () -> Unit,
    onRefreshUsers: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Выбор пользователя",
                style = MaterialTheme.typography.titleMedium
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (users.isEmpty()) {
                Text(
                    text = "Пользователи не найдены",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val userOptions = listOf("Не выбран") + users.map { it.name }
                val currentSelection = selectedUser?.name ?: "Не выбран"

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Текущий пользователь: $currentSelection",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSelectUser,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Выбрать")
                        }

                        if (selectedUser != null) {
                            Button(
                                onClick = onClearUser,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("Сбросить")
                            }
                        }
                    }

                    // Refresh users button
                    Button(
                        onClick = onRefreshUsers,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && networkConfig != null
                    ) {
                        Text(if (isLoading) "Обновление..." else "Обновить список пользователей")
                    }
                }
            }
        }
    }
}