package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeplanner.NetworkConfig
import com.homeplanner.SupportedApiVersions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsSection(
    networkConfig: NetworkConfig?,
    onEditClick: () -> Unit,
    onClearClick: () -> Unit,
    onQrScanClick: () -> Unit,
    onTestConnection: () -> Unit,
    isTesting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (networkConfig == null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Сетевые настройки",
                style = MaterialTheme.typography.titleMedium
            )

            val config = networkConfig
            if (config != null) {
                val apiUrl = config.toApiBaseUrl()
                val host = config.host
                val port = config.port
                val apiVersion = config.apiVersion
                val protocol = if (config.useHttps) "HTTPS" else "HTTP"
                Text(
                    text = "API URL: $apiUrl",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Хост: $host",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Порт: $port",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "API Версия: $apiVersion",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Протокол: $protocol",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                // Показывать дефолтные значения если настройки не заданы
                val defaultHost = "localhost"
                val defaultPort = 8000
                val defaultApiVersion = SupportedApiVersions.defaultVersion
                val defaultUseHttps = true
                val apiUrl = NetworkConfig(defaultHost, defaultPort, defaultApiVersion, defaultUseHttps).toApiBaseUrl()
                val protocol = if (defaultUseHttps) "HTTPS" else "HTTP"
                Text(
                    text = "⚠️ Подключение не настроено",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "API URL: $apiUrl (по умолчанию)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Хост: $defaultHost (по умолчанию)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Порт: $defaultPort (по умолчанию)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "API Версия: $defaultApiVersion (по умолчанию)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Протокол: $protocol (по умолчанию)",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (networkConfig == null) "Настроить" else "Изменить")
                }

                if (networkConfig != null) {
                    Button(
                        onClick = onClearClick,
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

            Button(
                onClick = onQrScanClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сканировать QR-код")
            }

            if (networkConfig != null) {
                Button(
                    onClick = onTestConnection,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting
                ) {
                    Text(if (isTesting) "Проверка..." else "Проверить подключение")
                }
            }
        }
    }
}