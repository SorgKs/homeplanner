// History functions

import { allTasks, groups, adminMode, showToast } from './utils.js';
import { formatDateTime } from './utils.js';

export async function viewTaskHistory(taskId) {
    try {
        const task = allTasks.find(t => t.id === taskId);
        if (!task) {
            showToast('Задача не найдена', 'error');
            return;
        }

        const modal = document.getElementById('history-modal');
        const titleEl = document.getElementById('history-task-title');
        const contentEl = document.getElementById('history-content');

        titleEl.textContent = task.title;
        contentEl.innerHTML = '<p>Загрузка истории...</p>';

        modal.classList.add('show');
        modal.style.display = 'block';

        const history = await tasksAPI.getHistory(taskId);

        if (history.length === 0) {
            contentEl.innerHTML = '<p style="text-align: center; color: #666;">История пуста</p>';
            return;
        }

        const actionLabels = {
            'created': 'Создана',
            'first_shown': 'Первый показ',
            'confirmed': 'Подтверждена',
            'unconfirmed': 'Отмена подтверждения',
            'edited': 'Изменена',
            'deleted': 'Удалена',
            'activated': 'Активирована',
            'deactivated': 'Деактивирована'
        };

        let html = '<div style="display: flex; flex-direction: column; gap: 12px;">';
        history.forEach(entry => {
            const actionDate = new Date(entry.action_timestamp);
            const iterationDateStr = entry.iteration_date
                ? `(итерация: ${formatDateTime(entry.iteration_date)})`
                : '';

            let metadataHtml = '';
            if (entry.meta_data) {
                try {
                    const metadata = JSON.parse(entry.meta_data);
                    if (metadata.old && metadata.new) {
                        // Format changes
                        const changes = Object.keys(metadata.new).map(key => {
                            return `  • ${key}: "${metadata.old[key]}" → "${metadata.new[key]}"`;
                        }).join('<br>');
                        metadataHtml = `<div style="margin-top: 8px; padding: 8px; background: #f0f0f0; border-radius: 4px; font-size: 0.9em;">Изменения:<br>${changes}</div>`;
                    } else if (typeof metadata === 'object') {
                        metadataHtml = `<div style="margin-top: 8px; padding: 8px; background: #f0f0f0; border-radius: 4px; font-size: 0.9em;">${JSON.stringify(metadata, null, 2)}</div>`;
                    }
                } catch (e) {
                    // Metadata is not JSON
                    metadataHtml = '';
                }
            }

            html += `
                <div style="padding: 12px; border: 1px solid #ddd; border-radius: 8px;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <span style="font-weight: 600;">${actionLabels[entry.action] || entry.action}</span>
                        <span style="color: #666; font-size: 0.9em;">${formatDateTime(actionDate)}</span>
                    </div>
                    ${iterationDateStr ? `<div style="color: #888; font-size: 0.85em; margin-top: 4px;">${iterationDateStr}</div>` : ''}
                    ${metadataHtml}
                </div>
            `;
        });
        html += '</div>';
        contentEl.innerHTML = html;
    } catch (error) {
        console.error('Error loading history:', error);
        showToast('Ошибка загрузки истории: ' + error.message, 'error');
    }
}

export async function renderHistoryView() {
    try {
        const container = document.getElementById('tasks-list');
        container.innerHTML = '<p>Загрузка истории...</p>';

        // Get filter values
        const selectedGroupId = document.getElementById('history-group-filter').value;
        const selectedTaskId = document.getElementById('history-task-filter').value;
        const dateFrom = document.getElementById('history-date-from').value;
        const dateTo = document.getElementById('history-date-to').value;

        // Update filters with current data
        updateHistoryFilters();

        // Load all history (including deleted tasks)
        const allHistoryRaw = await tasksAPI.getAllHistory();

        // Process history entries and enrich with task/group info
        let allHistory = allHistoryRaw.map(entry => {
            let task_id = entry.task_id;
            let task_title = null;
            let group_id = null;
            let group_name = null;

            // For deleted tasks, task_id may be NULL, but it's preserved in meta_data
            if (!task_id && entry.meta_data) {
                try {
                    const metadata = JSON.parse(entry.meta_data);
                    task_id = metadata.task_id || null;
                    task_title = metadata.task_title || null;
                } catch (e) {
                    // Ignore JSON parse errors
                }
            }

            // Find task info if it exists
            if (task_id) {
                const task = allTasks.find(t => t.id === task_id);
                if (task) {
                    task_title = task.title;
                    group_id = task.group_id;
                    const taskGroup = groups.find(g => g.id === task.group_id);
                    group_name = taskGroup ? taskGroup.name : null;
                } else {
                    // Task was deleted, use metadata info
                    if (!task_title && entry.meta_data) {
                        try {
                            const metadata = JSON.parse(entry.meta_data);
                            task_title = metadata.task_title || `Удаленная задача #${task_id}`;
                        } catch (e) {
                            task_title = `Удаленная задача #${task_id}`;
                        }
                    }
                }
            } else if (entry.meta_data) {
                // Handle case where task_id is NULL but we have metadata
                try {
                    const metadata = JSON.parse(entry.meta_data);
                    if (metadata.task_title) {
                        task_title = metadata.task_title;
                    }
                    if (metadata.task_id) {
                        task_id = metadata.task_id;
                    }
                } catch (e) {
                    // Ignore JSON parse errors
                }
            }

            return {
                ...entry,
                task_title: task_title || 'Неизвестная задача',
                task_id: task_id,
                group_id: group_id,
                group_name: group_name
            };
        });

        // Apply filters
        if (selectedGroupId) {
            allHistory = allHistory.filter(entry => entry.group_id == selectedGroupId);
        }
        if (selectedTaskId) {
            allHistory = allHistory.filter(entry => entry.task_id == selectedTaskId);
        }
        if (dateFrom) {
            allHistory = allHistory.filter(entry => entry.action_timestamp >= dateFrom);
        }
        if (dateTo) {
            allHistory = allHistory.filter(entry => entry.action_timestamp <= dateTo + 'T23:59:59');
        }

        if (allHistory.length === 0) {
            container.innerHTML = '<p style="text-align: center; color: #666; padding: 40px;">История пуста</p>';
            return;
        }

        // Sort by timestamp desc
        allHistory.sort((a, b) => new Date(b.action_timestamp) - new Date(a.action_timestamp));

        const actionLabels = {
            'created': 'Создана',
            'first_shown': 'Показана',
            'confirmed': 'Подтверждена',
            'unconfirmed': 'Отмена',
            'edited': 'Изменена',
            'deleted': 'Удалена',
            'activated': 'Активирована',
            'deactivated': 'Деактивирована'
        };

        // Light pastel background/border colors for statuses (good contrast on blue theme)
        const actionBgColors = {
            created: '#E3F2FD',       // very light blue
            first_shown: '#E0F2FE',   // sky-100
            confirmed: '#DBEAFE',     // indigo-100
            unconfirmed: '#E0E7FF',   // indigo-100 slightly different
            edited: '#F0F9FF',        // lightest
            deleted: '#FFE4E6',       // light rose for emphasis but still light
            activated: '#E6F0FF',     // custom light blue
            deactivated: '#F1F5F9'    // slate-100 neutral
        };

        // Darker text colors for status labels to ensure readability on light bg
        const actionTextColors = {
            created: '#1E3A8A',       // blue-900
            first_shown: '#0F3D84',   // custom dark blue
            confirmed: '#1D4ED8',     // blue-700
            unconfirmed: '#1D4ED8',   // same family for consistency
            edited: '#0B4A6F',        // dark cyan/blue
            deleted: '#B91C1C',       // red-700 for clarity on light rose
            activated: '#1E40AF',     // blue-800
            deactivated: '#334155'    // slate-700
        };

        let html = '<div style="display: flex; flex-direction: column; gap: 4px;">';
        allHistory.forEach(entry => {
            const actionDate = new Date(entry.action_timestamp);
            const borderColor = actionTextColors[entry.action] || '#475569';
            const bgColor = actionBgColors[entry.action] || '#F1F5F9';

            let changesText = '';
            if (entry.comment) {
                changesText = entry.comment;
            }

            const fullTaskName = entry.group_name
                ? `${entry.group_name}: ${entry.task_title}`
                : entry.task_title;

            html += `
                <div class="history-entry" style="background: ${bgColor}; border-left-color: ${borderColor};">
                    <span class="history-entry-date">${formatDateTime(actionDate)}</span>
                    <span class="history-entry-task">${escapeHtml(fullTaskName)}</span>
                    <span class="history-entry-action" style="color: ${borderColor};">
                        ${actionLabels[entry.action] || entry.action}
                    </span>
                    <span class="history-entry-comment">${changesText ? escapeHtml(changesText) : ''}</span>
                    ${adminMode ? `<button class="btn btn-danger" onclick="deleteHistoryEntry(${entry.id}, ${entry.task_id})" title="Удалить запись">✕</button>` : ''}
                </div>
            `;
        });
        html += '</div>';
        container.innerHTML = html;
    } catch (error) {
        console.error('Error loading history:', error);
        showToast('Ошибка загрузки истории: ' + error.message, 'error');
    }
}

export function updateHistoryFilters() {
    // Update group filter
    const groupFilter = document.getElementById('history-group-filter');
    const currentGroupValue = groupFilter.value;

    // Keep "All groups" option
    groupFilter.innerHTML = '<option value="">Все группы</option>';
    groups.forEach(group => {
        const option = document.createElement('option');
        option.value = group.id;
        option.textContent = group.name;
        groupFilter.appendChild(option);
    });

    // Restore selection if still valid
    if (currentGroupValue) {
        groupFilter.value = currentGroupValue;
    }

    // Update task filter
    const taskFilter = document.getElementById('history-task-filter');
    const currentTaskValue = taskFilter.value;

    // Keep "All tasks" option
    taskFilter.innerHTML = '<option value="">Все задачи</option>';

    // Filter tasks by selected group if needed
    const tasksToShow = currentGroupValue
        ? allTasks.filter(task => task.group_id == currentGroupValue)
        : allTasks;

    tasksToShow.forEach(task => {
        const option = document.createElement('option');
        option.value = task.id;
        option.textContent = task.title;
        taskFilter.appendChild(option);
    });

    // Restore selection if still valid
    if (currentTaskValue) {
        taskFilter.value = currentTaskValue;
    }
}

export function closeHistoryModal() {
    const modal = document.getElementById('history-modal');
    modal.style.display = 'none';
    modal.classList.remove('show');
}

export async function deleteHistoryEntry(historyId, taskId) {
    if (!confirm('Вы уверены, что хотите удалить эту запись истории?')) {
        return;
    }

    try {
        await tasksAPI.deleteHistoryEntry(historyId);
        showToast('Запись истории удалена', 'success');
        // Reload history view
        if (currentView === 'history') {
            renderHistoryView();
        }
    } catch (error) {
        console.error('Failed to delete history entry:', error);
        showToast('Ошибка удаления записи истории: ' + error.message, 'error');
    }
}

// Import missing
import { escapeHtml } from './utils.js';