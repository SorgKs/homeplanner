package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.homeplanner.NetworkConfig
import com.homeplanner.SupportedApiVersions
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkEditDialogScreen(navController: NavHostController) {
    val viewModel: com.homeplanner.viewmodel.SettingsViewModel = koinViewModel()

    var host by remember { mutableStateOf("192.168.1.1") }
    var port by remember { mutableStateOf("8000") }
    var apiVersion by remember { mutableStateOf(SupportedApiVersions.defaultVersion) }
    var useHttps by remember { mutableStateOf(false) }

    // Load current config if exists
    LaunchedEffect(Unit) {
        val currentConfig = viewModel.state.value.networkConfig
        if (currentConfig != null) {
            host = currentConfig.host
            port = currentConfig.port.toString()
            apiVersion = currentConfig.apiVersion
            useHttps = currentConfig.useHttps
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Редактирование сетевых настроек",
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Хост") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Порт") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = apiVersion,
                onValueChange = {},
                readOnly = true,
                label = { Text("API Версия") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                SupportedApiVersions.versions.forEach { version ->
                    DropdownMenuItem(
                        text = { Text(version) },
                        onClick = {
                            apiVersion = version
                            expanded = false
                        }
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = useHttps,
                onCheckedChange = { useHttps = it }
            )
            Text("Использовать HTTPS")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    try {
                        val config = NetworkConfig(
                            host = host,
                            port = port.toInt(),
                            apiVersion = apiVersion,
                            useHttps = useHttps
                        )
                        viewModel.saveNetworkConfig(config)
                        navController.popBackStack()
                    } catch (e: Exception) {
                        // Handle error
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Сохранить")
            }

            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Отмена")
            }
        }
    }
}