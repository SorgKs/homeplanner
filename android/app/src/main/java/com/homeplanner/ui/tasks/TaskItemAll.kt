package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.homeplanner.model.Task
import com.homeplanner.model.Group
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun TaskItemAll(
    task: Task,
    groups: List<Group>,
    onComplete: (Int, Boolean) -> Unit,
    onClick: (Task) -> Unit,
    onDelete: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(56.dp),
        onClick = { onClick(task) },
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(end = 24.dp) // Space for checkbox
            ) {
                // First line: full time and formula
                Text(
                    text = "${formatFullTime(task.reminderTime)} ${formatTaskFormula(task)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                // Second line: group + title
                val groupName = if (task.groupId != null) {
                    groups.find { it.id == task.groupId }?.name ?: ""
                } else ""
                val secondLine = if (groupName.isNotEmpty()) "$groupName ${task.title}" else task.title
                Text(
                    text = secondLine,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Checkbox(
                checked = task.completed,
                onCheckedChange = { checked -> onComplete(task.id, checked) },
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

private fun formatFullTime(reminderTime: String): String {
    // Parse ISO datetime and format as dd.MM HH:mm
    return try {
        val dt = LocalDateTime.parse(reminderTime)
        dt.format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
    } catch (e: Exception) {
        reminderTime.substringAfter('T').take(5) // fallback to HH:MM
    }
}

private fun formatTaskFormula(task: Task): String {
    return when (task.taskType) {
        "one_time" -> "разовая"
        "recurring" -> formatRecurringFormula(task)
        "interval" -> formatIntervalFormula(task)
        else -> ""
    }
}

private fun formatRecurringFormula(task: Task): String {
    val recurrenceType = task.recurrenceType ?: return ""
    val interval = task.recurrenceInterval ?: 1

    return when (recurrenceType) {
        "daily" -> if (interval == 1) "ежедневно" else "каждые $interval дней"
        "weekdays" -> if (interval == 1) "по будням" else "каждые $interval будних дня"
        "weekends" -> if (interval == 1) "по выходным" else "каждые $interval выходных дня"
        "weekly" -> formatWeeklyFormula(task, interval)
        "monthly" -> formatMonthlyFormula(task, interval)
        "monthly_weekday" -> formatMonthlyWeekdayFormula(task, interval)
        "yearly" -> formatYearlyFormula(task, interval)
        "yearly_weekday" -> formatYearlyWeekdayFormula(task, interval)
        else -> "расписание"
    }
}

private fun formatWeeklyFormula(task: Task, interval: Int): String {
    val weekday = try {
        val dt = LocalDateTime.parse(task.reminderTime)
        val weekdays = arrayOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
        weekdays[dt.dayOfWeek.value - 1]
    } catch (e: Exception) {
        return if (interval == 1) "еженедельно" else "каждые $interval недели"
    }
    return if (interval == 1) "каждый $weekday" else "каждые $interval недели по $weekday"
}

private fun formatMonthlyFormula(task: Task, interval: Int): String {
    val day = try {
        val dt = LocalDateTime.parse(task.reminderTime)
        dt.dayOfMonth
    } catch (e: Exception) {
        return if (interval == 1) "ежемесячно" else "каждые $interval месяца"
    }
    return if (interval == 1) "$day числа ежемесячно" else "$day числа каждые $interval месяца"
}

private fun formatMonthlyWeekdayFormula(task: Task, interval: Int): String {
    // Simplified - just return basic description
    return if (interval == 1) "ежемесячно (по дню недели)" else "каждые $interval месяца (по дню недели)"
}

private fun formatYearlyFormula(task: Task, interval: Int): String {
    val months = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )
    return try {
        val dt = LocalDateTime.parse(task.reminderTime)
        val day = dt.dayOfMonth
        val month = months[dt.monthValue - 1]
        if (interval == 1) "$day $month ежегодно" else "$day $month каждые $interval года"
    } catch (e: Exception) {
        if (interval == 1) "ежегодно" else "каждые $interval года"
    }
}

private fun formatYearlyWeekdayFormula(task: Task, interval: Int): String {
    // Simplified
    return if (interval == 1) "ежегодно (по дню недели)" else "каждые $interval года (по дню недели)"
}

private fun formatIntervalFormula(task: Task): String {
    val intervalDays = task.intervalDays ?: return "интервальная"
    return when {
        intervalDays == 1 -> "раз в день"
        intervalDays == 7 -> "раз в неделю"
        intervalDays < 7 -> "раз в $intervalDays дня"
        intervalDays % 7 == 0 -> {
            val weeks = intervalDays / 7
            if (weeks == 1) "раз в неделю" else "раз в $weeks недели"
        }
        intervalDays % 30 == 0 -> {
            val months = intervalDays / 30
            if (months == 1) "раз в месяц" else "раз в $months месяца"
        }
        else -> "раз в $intervalDays дней"
    }
}