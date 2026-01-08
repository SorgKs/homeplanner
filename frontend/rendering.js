// Rendering functions for tasks

import { filteredTasks, currentView, groups, timeControlState, allTasks, todayTasksCache, allTasksCache, todayTaskIds, users } from './utils.js';
import { getReferenceDate, getTaskTimeCategory, sortTasksByReminderTime, getTaskTimestamp } from './utils.js';

// Helper functions
export function categorizeTasksByTime(tasks, referenceDate) {
    const overdue = [];
    const current = [];
    const planned = [];

    // Получаем начало сегодняшнего дня
    const todayStart = new Date(
        referenceDate.getFullYear(),
        referenceDate.getMonth(),
        referenceDate.getDate()
    );
    // Получаем начало вчерашнего дня
    const yesterdayStart = new Date(todayStart);
    yesterdayStart.setDate(yesterdayStart.getDate() - 1);

    tasks.forEach(task => {
        const category = getTaskTimeCategory(task, referenceDate, todayStart, yesterdayStart);
        if (category === 'overdue') {
            overdue.push(task);
        } else if (category === 'current') {
            current.push(task);
        } else {
            planned.push(task);
        }
    });

    return {
        overdue: sortTasksByReminderTime(overdue),
        current: sortTasksByReminderTime(current),
        planned: sortTasksByReminderTime(planned),
    };
}

export function renderTodayTasksCollection(tasks, referenceDate) {
    if (!tasks.length) {
        return '';
    }

    const tasksByGroup = {};
    const tasksWithoutGroup = [];
    const knownGroupIds = new Set(groups.map(group => group.id));

    // Получаем границы для определения категорий
    const todayStart = new Date(
        referenceDate.getFullYear(),
        referenceDate.getMonth(),
        referenceDate.getDate()
    );
    const yesterdayStart = new Date(todayStart);
    yesterdayStart.setDate(yesterdayStart.getDate() - 1);

    tasks.forEach(task => {
        const groupId = task.group_id;
        if (groupId && knownGroupIds.has(groupId)) {
            if (!tasksByGroup[groupId]) {
                tasksByGroup[groupId] = [];
            }
            tasksByGroup[groupId].push(task);
        } else {
            tasksWithoutGroup.push(task);
        }
    });

    let html = '';

    groups.forEach(group => {
        if (tasksByGroup[group.id] && tasksByGroup[group.id].length > 0) {
            tasksByGroup[group.id].forEach(task => {
                const category = getTaskTimeCategory(task, referenceDate, todayStart, yesterdayStart);
                html += renderTodayTaskItem(task, group, category);
            });
        }
    });

    tasksWithoutGroup.forEach(task => {
        const category = getTaskTimeCategory(task, referenceDate, todayStart, yesterdayStart);
        html += renderTodayTaskItem(task, null, category);
    });

    return html;
}

function renderTodayTaskItem(task, group, category) {
    // Используем is_completed из данных задачи (уже правильно вычислен в loadData)
    const isCompleted = task.is_completed;
    const fullTitle = group ? `${group.name}: ${task.title}` : task.title;

    // Форматируем время из reminder_time
    const timeSource = task.reminder_time;
    const timeStr = timeSource ? new Date(timeSource).toLocaleTimeString('ru-RU', {
        hour: '2-digit',
        minute: '2-digit'
    }) : '';

    // Определяем класс стиля в зависимости от категории
    const categoryClass = category === 'overdue' ? 'task-overdue' :
                          category === 'current' ? 'task-current' :
                          'task-planned';

    return `
        <div class="today-task-item ${categoryClass} ${isCompleted ? 'completed' : ''}">
            <div style="display: flex; align-items: center; gap: 12px; width: 100%;">
                <span style="min-width: 60px; text-align: left; font-weight: 600; color: var(--text-secondary);">${timeStr}</span>
                <span class="task-title" style="flex: 1;">${escapeHtml(fullTitle)}</span>
                <label class="today-task-checkbox" style="margin: 0;">
                    <input type="checkbox" ${isCompleted ? 'checked' : ''}
                           onchange="toggleTaskComplete(${task.id}, this.checked)"
                           class="task-checkbox">
                </label>
            </div>
        </div>
    `;
}

function renderAllTasksHeader() {
    return `
        <div class="task-table-header">
            <div class="task-row-cell task-row-date">Время</div>
            <div class="task-row-cell task-row-title">Задача</div>
            <div class="task-row-cell task-row-config">Формула</div>
            <div class="task-row-cell task-row-actions">Действия</div>
        </div>
    `;
}

function renderAllTasksCard(task, now) {
    const isCompleted = Boolean(task.is_completed);
    const isEnabled = Boolean(task.is_enabled);
    // Категоризация по тем же правилам, что и в Today view:
    // - overdue: reminder_time в предыдущий день (учитывая начало дня)
    // - current: сегодня (до текущего момента — current; после — planned, но по полосе — текущий день)
    // - planned: за пределами текущего дня
    const referenceDate = getReferenceDate();
    const todayStart = new Date(
        referenceDate.getFullYear(),
        referenceDate.getMonth(),
        referenceDate.getDate()
    );
    const yesterdayStart = new Date(todayStart);
    yesterdayStart.setDate(yesterdayStart.getDate() - 1);
    const category = getTaskTimeCategory(task, referenceDate, todayStart, yesterdayStart);

    const configText = task.readable_config || 'Не указано';
    const dueDateText = task.due_date ? formatDateTime(task.due_date) : '—';
    const fullTitle = task.group_id ? `${escapeHtml(groups.find(g => g.id === task.group_id)?.name || 'Без группы')}: ${escapeHtml(task.title)}` : escapeHtml(task.title);

    // Generate assignees HTML
    const assignedUsers = [];
    if (task.assigned_user_ids && task.assigned_user_ids.length > 0) {
        task.assigned_user_ids.forEach(userId => {
            const user = users.find(u => u.id === userId);
            if (user) {
                assignedUsers.push(user.name);
            }
        });
    }
    const assigneesHtml = assignedUsers.length > 0 ? assignedUsers.join(', ') : '—';

    // Generate status text
    let statusText = '';
    if (isCompleted) {
        statusText = 'Выполнена';
    } else if (!isEnabled) {
        statusText = 'Отключена';
    } else {
        statusText = 'Активна';
    }

    const rowClasses = [
        'task-row',
        isCompleted ? 'completed' : '',
        !isCompleted && isEnabled && category === 'overdue' ? 'overdue' : '',
        !isCompleted && isEnabled && category === 'current' ? 'current' : '',
        !isCompleted && isEnabled && category === 'planned' ? 'planned' : '',
        !isEnabled ? 'inactive' : '',
    ].filter(Boolean).join(' ');

    return `
        <div class="${rowClasses}">
            <label class="task-row-cell task-row-title">
                <input type="checkbox"
                       ${isCompleted ? 'checked' : ''}
                       onchange="toggleTaskComplete(${task.id}, this.checked)"
                       class="task-row-checkbox"
                       title="${isCompleted ? 'Отметить как невыполненную' : 'Отметить как выполненную'}">
                <span class="task-row-title-text">${escapeHtml(task.title)}</span>
            </label>
            <div class="task-row-cell task-row-config">${escapeHtml(configText)}</div>
            <div class="task-row-cell task-row-users">${escapeHtml(assigneesHtml)}</div>
            <div class="task-row-cell task-row-date">${dueDateText}</div>
            <div class="task-row-cell task-row-status">
                <span class="status-indicator ${isCompleted ? 'status-completed' : isEnabled ? 'status-active' : 'status-inactive'}"></span>
                <span>${statusText}</span>
            </div>
            <div class="task-row-cell task-row-actions">
                <button class="btn btn-secondary btn-icon" onclick="editTask(${task.id})" title="Редактировать">✎</button>
                <button class="btn btn-danger btn-icon" onclick="deleteTask(${task.id})" title="Удалить">✕</button>
            </div>
        </div>
    `;
}

function renderTaskCard(task, now) {
    const reminderDate = task.reminder_time ? new Date(task.reminder_time) : null;
    const isCompleted = Boolean(task.completed);
    const isActive = task.active !== false;
    const isUrgent =
        reminderDate !== null &&
        reminderDate <= new Date(now.getTime() + 24 * 60 * 60 * 1000) &&
        !isCompleted &&
        isActive;
    const isPast =
        reminderDate !== null &&
        reminderDate < now &&
        !isCompleted &&
        isActive;

    let metaInfo = '';
    const taskType = task.task_type || 'one_time';
    if (taskType === 'interval' && task.interval_days) {
        metaInfo = `<span>⏱️ Через ${task.interval_days} ${task.interval_days === 1 ? 'день' : task.interval_days < 5 ? 'дня' : 'дней'} после подтверждения</span>`;
    } else if (taskType === 'recurring' && task.recurrence_type) {
        const recurrenceText = {
            daily: 'Ежедневно',
            weekdays: 'По будням',
            weekends: 'По выходным',
            weekly: 'Еженедельно',
            monthly: 'Ежемесячно',
            monthly_weekday: 'Ежемесячно (по дню недели)',
            yearly: 'Ежегодно',
            yearly_weekday: 'Ежегодно (по дню недели)',
        }[task.recurrence_type] || task.recurrence_type;
        if (task.recurrence_interval && task.recurrence_interval > 1) {
            metaInfo = `<span>🔄 ${recurrenceText} (каждые ${task.recurrence_interval})</span>`;
        } else {
            metaInfo = `<span>🔄 ${recurrenceText}</span>`;
        }
    } else if (taskType === 'one_time') {
        metaInfo = `<span>📌 Разовое</span>`;
    }

    const reminderInfo = reminderDate ? formatDateTime(task.reminder_time) : 'Не задано';

    return `
        <div class="item-card ${isCompleted || !isActive ? 'completed' : ''} ${isUrgent ? 'urgent' : ''}">
            <div class="item-info">
                <div class="item-title">
                    ${escapeHtml(task.title)}
                    ${task.task_type === 'interval' ? '<span style="font-size: 12px; color: var(--text-secondary); margin-left: 8px;">(интервал)</span>' : ''}
                </div>
                ${task.description ? `<div class="item-description">${escapeHtml(task.description)}</div>` : ''}
                <div class="item-meta">
                    <span>📅 ${reminderInfo}</span>
                    ${metaInfo}
                    ${isPast ? '<span style="color: var(--danger-color);">⚠️ Просрочено</span>' : ''}
                </div>
            </div>
            <div class="item-actions">
                ${!isCompleted && isActive ? `<button class="btn btn-success" onclick="completeTask(${task.id})" title="Подтвердить выполнение">✓</button>` : ''}
                <button class="btn btn-secondary" onclick="editTask(${task.id})" title="Редактировать">✎</button>
                <button class="btn btn-danger" onclick="deleteTask(${task.id})" title="Удалить">✕</button>
            </div>
        </div>
    `;
}

// Main render functions
export function renderTodayView() {
    const container = document.getElementById('tasks-list');
    const referenceDate = getReferenceDate();
    const categorizedTasks = categorizeTasksByTime(filteredTasks, referenceDate);

    // Объединяем все задачи в правильном порядке: просроченные -> текущие -> планируемые
    const allTasks = [
        ...categorizedTasks.overdue,
        ...categorizedTasks.current,
        ...categorizedTasks.planned
    ];

    if (allTasks.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">📋</div>
                <div class="empty-state-text">Нет задач на сегодня и просроченных задач</div>
                <div class="empty-state-hint">Добавьте первую задачу</div>
            </div>
        `;
        return;
    }

    const html = `
        <div class="today-tasks-list">
            ${renderTodayTasksCollection(allTasks, referenceDate)}
        </div>
    `;

    container.innerHTML = html;
}

export function renderAllTasksView() {
    const container = document.getElementById('tasks-list');

    // Разделяем включенные и отключенные, далее группируем каждый набор по группам
    const activeTasks = filteredTasks.filter(t => t.is_enabled);
    const inactiveTasks = filteredTasks.filter(t => !t.is_enabled);
    const headerRow = renderAllTasksHeader();

    const activeByGroup = {};
    const activeWithoutGroup = [];
    activeTasks.forEach(task => {
        const groupId = task.group_id;
        if (groupId) {
            if (!activeByGroup[groupId]) activeByGroup[groupId] = [];
            activeByGroup[groupId].push(task);
        } else {
            activeWithoutGroup.push(task);
        }
    });

    const inactiveByGroup = {};
    const inactiveWithoutGroup = [];
    inactiveTasks.forEach(task => {
        const groupId = task.group_id;
        if (groupId) {
            if (!inactiveByGroup[groupId]) inactiveByGroup[groupId] = [];
            inactiveByGroup[groupId].push(task);
        } else {
            inactiveWithoutGroup.push(task);
        }
    });

    const now = new Date();
    let html = '';

    // Сначала активные задачи по группам
    groups.forEach(group => {
        if (activeByGroup[group.id] && activeByGroup[group.id].length > 0) {
            html += `
                <div class="task-group">
                    <div class="task-group-bar">
                        <div class="task-group-caption">
                            <span class="task-group-title-text">${escapeHtml(group.name)}</span>
                            ${group.description ? `<span class="task-group-desc">${escapeHtml(group.description)}</span>` : ''}
                        </div>
                        <div class="task-group-actions">
                            <button class="btn btn-secondary btn-sm" onclick="editGroup(${group.id})" title="Редактировать">✎</button>
                            <button class="btn btn-danger btn-sm" onclick="deleteGroup(${group.id})" title="Удалить">✕</button>
                        </div>
                    </div>
                <div class="task-group-items task-table">
                    ${headerRow}
                        ${activeByGroup[group.id].map(task => renderAllTasksCard(task, now)).join('')}
                    </div>
                </div>
            `;
        }
    });

    // Активные задачи без группы
    if (activeWithoutGroup.length > 0) {
        html += `
            <div class="task-group">
                <div class="task-group-bar">
                    <div class="task-group-caption">
                        <span class="task-group-title-text">Без группы</span>
                    </div>
                </div>
                <div class="task-group-items task-table">
                    ${headerRow}
                    ${activeWithoutGroup.map(task => renderAllTasksCard(task, now)).join('')}
                </div>
            </div>
        `;
    }

    // Блок неактивных задач в конце
    if (inactiveTasks.length > 0) {
        html += `
            <div class="task-group">
                <div class="task-group-bar">
                    <div class="task-group-caption">
                        <span class="task-group-title-text">Отключенные</span>
                    </div>
                </div>
                <div class="task-group-items task-table">
                    ${headerRow}
        `;

        // Неактивные по группам
        groups.forEach(group => {
            if (inactiveByGroup[group.id] && inactiveByGroup[group.id].length > 0) {
                html += `
                    <div class="task-subgroup">
                        <div class="task-subgroup-title" style="margin: 8px 0; color: var(--text-secondary); font-weight: 600;">${escapeHtml(group.name)}</div>
                        ${inactiveByGroup[group.id].map(task => renderAllTasksCard(task, now)).join('')}
                    </div>
                `;
            }
        });

        // Неактивные без группы
        if (inactiveWithoutGroup.length > 0) {
            html += `
                <div class="task-subgroup">
                    <div class="task-subgroup-title" style="margin: 8px 0; color: var(--text-secondary); font-weight: 600;">Без группы</div>
                    ${inactiveWithoutGroup.map(task => renderAllTasksCard(task, now)).join('')}
                </div>
            `;
        }

        html += `
                </div>
            </div>
        `;
    }

    container.innerHTML = html;
}

export function renderTasks() {
    const container = document.getElementById('tasks-list');

    if (filteredTasks.length === 0) {
        const emptyMsg = currentView === 'today'
            ? 'Нет задач на сегодня и просроченных задач'
            : (searchQuery || filterState ? 'Задачи не найдены' : 'Нет задач');
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">📋</div>
                <div class="empty-state-text">${emptyMsg}</div>
                <div class="empty-state-hint">${searchQuery || filterState ? 'Попробуйте изменить поиск или фильтры' : 'Добавьте первую задачу'}</div>
            </div>
        `;
        return;
    }

    // Выбираем способ отображения в зависимости от вида
    if (currentView === 'today') {
        renderTodayView();
    } else if (currentView === 'history') {
        renderHistoryView();
    } else {
        renderAllTasksView();
    }
}

// Import missing
import { escapeHtml, formatDateTime, searchQuery, filterState } from './utils.js';
import { renderHistoryView } from './history.js';