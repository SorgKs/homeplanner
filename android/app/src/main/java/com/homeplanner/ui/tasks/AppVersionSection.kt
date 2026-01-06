package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeplanner.BuildConfig

@Composable
fun AppVersionSection() {
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
                text = "Версия приложения",
                style = MaterialTheme.typography.titleSmall
            )
            val debugStatus = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"
            Text(text = "Версия: ${BuildConfig.VERSION_NAME} ($debugStatus)")
        }
    }
}