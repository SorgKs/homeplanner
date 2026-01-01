package com.homeplanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Строка состояния приложения с индикаторами сети и хранилища.
 */
@Composable
fun AppStatusBar(
    appIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    screenTitle: String = "HomePlanner",
    isOnline: Boolean,
    connectionStatus: com.homeplanner.ConnectionStatus? = null,
    isSyncing: Boolean,
    storagePercentage: Float,
    pendingOperations: Int,
    compactMode: Boolean = false,
    onNetworkClick: () -> Unit = {},
    onStorageClick: () -> Unit = {},
    onSyncClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка приложения (если предоставлена)
            if (appIcon != null) {
                Icon(
                    imageVector = appIcon,
                    contentDescription = "HomePlanner",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // Название экрана
            Text(
                text = screenTitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            
            // Индикаторы состояния
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Индикатор сети
                NetworkIndicator(
                    isOnline = isOnline,
                    connectionStatus = connectionStatus,
                    isSyncing = isSyncing,
                    compactMode = compactMode,
                    onClick = onNetworkClick
                )
                
                if (pendingOperations > 0) {
                    TextButton(
                        onClick = { onSyncClick?.invoke() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (compactMode) "⟳$pendingOperations" else "Очередь: $pendingOperations",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Индикатор хранилища
                StorageIndicator(
                    storagePercentage = storagePercentage,
                    compactMode = compactMode,
                    onIndicatorClick = onStorageClick
                )
            }
        }
    }
}

@Composable
fun NetworkIndicator(
    isOnline: Boolean,
    connectionStatus: com.homeplanner.ConnectionStatus? = null,
    isSyncing: Boolean,
    compactMode: Boolean = false,
    onClick: () -> Unit
) {
    // Используем connectionStatus если доступен, иначе fallback на isOnline
    val status = connectionStatus ?: if (isOnline) com.homeplanner.ConnectionStatus.ONLINE else com.homeplanner.ConnectionStatus.OFFLINE
    
    val icon = when {
        isSyncing -> Icons.Outlined.WifiTethering
        status == com.homeplanner.ConnectionStatus.UNKNOWN -> Icons.Outlined.WifiOff
        status == com.homeplanner.ConnectionStatus.OFFLINE -> Icons.Outlined.WifiOff
        status == com.homeplanner.ConnectionStatus.DEGRADED -> Icons.Outlined.WifiTethering
        else -> Icons.Outlined.Wifi
    }
    
    val color = when (status) {
        com.homeplanner.ConnectionStatus.UNKNOWN -> Color.Gray
        com.homeplanner.ConnectionStatus.ONLINE -> Color.Green
        com.homeplanner.ConnectionStatus.DEGRADED -> Color(0xFFFFA500) // Orange/Yellow
        com.homeplanner.ConnectionStatus.OFFLINE -> Color.Red
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = when (status) {
                com.homeplanner.ConnectionStatus.UNKNOWN -> "Статус неизвестен"
                com.homeplanner.ConnectionStatus.ONLINE -> if (isSyncing) "Синхронизация..." else "Онлайн"
                com.homeplanner.ConnectionStatus.DEGRADED -> "Проблемы с соединением"
                com.homeplanner.ConnectionStatus.OFFLINE -> "Оффлайн"
            },
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        if (!compactMode) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = when (status) {
                    com.homeplanner.ConnectionStatus.UNKNOWN -> "Неизвестно"
                    com.homeplanner.ConnectionStatus.ONLINE -> if (isSyncing) "Синхронизация..." else "Онлайн"
                    com.homeplanner.ConnectionStatus.DEGRADED -> "Проблемы"
                    com.homeplanner.ConnectionStatus.OFFLINE -> "Оффлайн"
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun StorageIndicator(
    storagePercentage: Float,
    compactMode: Boolean = false,
    onIndicatorClick: () -> Unit
) {
    val color = when {
        storagePercentage >= 90f -> Color.Red
        storagePercentage >= 70f -> Color(0xFFFFA500) // Orange/Yellow
        else -> Color.Green
    }
    
    val icon = when {
        storagePercentage >= 90f -> Icons.Outlined.Report
        storagePercentage >= 70f -> Icons.Outlined.SdCard
        else -> Icons.Outlined.Storage
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onIndicatorClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Заполненность хранилища: ${storagePercentage.toInt()}%",
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        if (!compactMode) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${storagePercentage.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

