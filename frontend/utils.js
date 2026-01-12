// Utility functions and global variables
console.log('utils.js loading...');

import { timeAPI, tasksAPI, groupsAPI, usersAPI } from './api.js';
import { handleUserSubmit, renderUsersList, showUserPickScreen } from './user_management.js';
import { updateAdminNavigation, updateTimePanelVisibility, filterAndRenderTasks } from './filters.js';
import { connectWebSocket } from './websocket.js';
import { toggleUserFilterControls } from './app.js';
import { renderHistoryView } from './history.js';

// Global variables
export let allTasks = []; // Все задачи (для текущего вида)
export function setAllTasks(tasks) {
    allTasks = tasks;
}
export let todayTasksCache = []; // Кэш задач для вида "Сегодня"
export function setTodayTasksCache(cache) {
    todayTasksCache = cache;
}
export let allTasksCache = []; // Кэш всех задач
export function setAllTasksCache(cache) {
    allTasksCache = cache;
}
export let todayTaskIds = new Set(); // IDs задач для вида "Сегодня"
export let groups = []; // Список групп
export let filteredTasks = [];
export function setFilteredTasks(tasks) {
    filteredTasks = tasks;
}
export let searchQuery = '';
export let filterState = null;
export let currentView = 'today'; // 'today', 'all', 'history', 'settings', 'users'
export let adminMode = false; // Режим администратора

export function setAdminMode(mode) {
    adminMode = mode;
}
export let ws = null; // WebSocket connection
export function setWs(newWs) {
    ws = newWs;
}
export let wsReconnectTimer = null; // Таймер переподключения WebSocket
export function setWsReconnectTimer(timer) {
    wsReconnectTimer = timer;
}
export let timeControlState = null; // Состояние панели управления временем
export let users = []; // Пользователи для назначения
export function setUsers(newUsers) {
    users = newUsers;
}
export let selectedUserId = null; // ID выбранного пользователя для фильтра (для "Сегодня" - из cookie, для "Все задачи" - из UI)
export function setSelectedUserId(id) {
    selectedUserId = id;
}
export let allTasksUserFilterId = null; // ID пользователя для фильтра во вкладке "Все задачи" (опционально)
export let appInitialized = false; // Флаг инициализации интерфейса

export const USER_ROLE_LABELS = {
    admin: 'Админ',
    regular: 'Обычный',
    guest: 'Гость',
};
export const USER_STATUS_LABELS = {
    true: 'Активен',
    false: 'Неактивен',
};

// Helper functions for tasks
export function getReferenceDate() {
    const virtualNow = timeControlState?.virtual_now;
    const realNow = timeControlState?.real_now;
    const useVirtual = timeControlState?.override_enabled && virtualNow;

    const source = useVirtual ? virtualNow : (realNow || virtualNow);

    if (source) {
        const date = new Date(source);
        if (!Number.isNaN(date.getTime())) {
            return date;
        }
    }

    return new Date();
}

export function getTaskTimestamp(task) {
    const timeSource = task.reminder_time || task.due_date;
    if (!timeSource) {
        return Number.POSITIVE_INFINITY;
    }
    const timestamp = new Date(timeSource).getTime();
    return Number.isNaN(timestamp) ? Number.POSITIVE_INFINITY : timestamp;
}

export function sortTasksByReminderTime(tasks) {
    return [...tasks].sort((a, b) => getTaskTimestamp(a) - getTaskTimestamp(b));
}

export function getTaskTimeCategory(task, referenceDate, todayStart, yesterdayStart) {
    const timeSource = task.reminder_time || task.due_date;

    if (!timeSource) {
        // Если нет времени, считаем просроченной
        return 'overdue';
    }

    const taskTime = new Date(timeSource);
    if (Number.isNaN(taskTime.getTime())) {
        return 'overdue';
    }

    // Если задача раньше начала вчерашнего дня - просрочена
    if (taskTime < yesterdayStart) {
        return 'overdue';
    }

    // Если задача между началом вчера и началом сегодня - просрочена (вчера)
    if (taskTime < todayStart) {
        return 'overdue';
    }

    // Если задача сегодня, но время уже прошло - текущая
    if (taskTime <= referenceDate) {
        return 'current';
    }

    // Если задача сегодня, но время еще не наступило - планируемая
    return 'planned';
}

// Cookie utilities
export function setCookie(name, value, days = 180) {
    const date = new Date();
    date.setTime(date.getTime() + days * 24 * 60 * 60 * 1000);
    const expires = '; expires=' + date.toUTCString();
    const secure = window.location.protocol === 'https:' ? '; Secure' : '';
    document.cookie = `${encodeURIComponent(name)}=${encodeURIComponent(String(value))}${expires}; path=/; SameSite=Lax${secure}`;
}

export function getCookie(name) {
    const nameEQ = encodeURIComponent(name) + '=';
    const parts = document.cookie.split(';');
    for (let i = 0; i < parts.length; i++) {
        let c = parts[i];
        while (c.charAt(0) === ' ') c = c.substring(1);
        if (c.indexOf(nameEQ) === 0) {
            return decodeURIComponent(c.substring(nameEQ.length));
        }
    }
    return null;
}

export function deleteCookie(name) {
    document.cookie = `${encodeURIComponent(name)}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; SameSite=Lax`;
}

export function applySelectedUserFromCookie() {
    const select = document.getElementById('user-filter');
    if (!select) return;
    const cookieVal = getCookie('hp.selectedUserId');
    if (!cookieVal) {
        showToast('Выберите пользователя в левом меню (фильтр «Пользователь»).', 'info');
        return;
    }
    const idNum = parseInt(cookieVal, 10);
    if (!Number.isFinite(idNum)) {
        deleteCookie('hp.selectedUserId');
        return;
    }
    const exists = Array.isArray(users) && users.some(u => Number(u.id) === idNum);
    if (!exists) {
        deleteCookie('hp.selectedUserId');
        showToast('Ранее выбранный пользователь не найден. Выберите другого.', 'warning');
        return;
    }
    select.value = String(idNum);
    selectedUserId = idNum;
}

// Utility functions
export function formatDateTime(isoString) {
    const date = new Date(isoString);
    return date.toLocaleString('ru-RU', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    });
}

export function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Show toast notification
export function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    const icons = {
        success: '✓',
        error: '✕',
        warning: '⚠',
        info: 'ℹ'
    };

    toast.innerHTML = `
        <span class="toast-icon">${icons[type] || icons.info}</span>
        <span class="toast-message">${escapeHtml(message)}</span>
        <button class="toast-close" onclick="this.parentElement.remove()">×</button>
    `;

    container.appendChild(toast);

    // Auto remove after 5 seconds
    setTimeout(() => {
        if (toast.parentElement) {
            toast.style.animation = 'slideInRight 0.3s ease-out reverse';
            setTimeout(() => toast.remove(), 300);
        }
    }, 5000);
}

// Show loading state
export function showLoading(containerId) {
    const container = document.getElementById(containerId);
    container.innerHTML = '<div class="loading"><span class="spinner"></span> Загрузка...</div>';
}

// Group management
export function openGroupModal(groupId = null) {
    const modal = document.getElementById('group-modal');
    const form = document.getElementById('group-form');
    const title = document.getElementById('group-modal-title');

    if (groupId) {
        const group = groups.find(g => g.id === groupId);
        if (group) {
            title.textContent = 'Редактировать группу';
            document.getElementById('group-id').value = group.id;
            document.getElementById('group-name').value = group.name;
            document.getElementById('group-description').value = group.description || '';
        }
    } else {
        title.textContent = 'Создать группу';
        form.reset();
        document.getElementById('group-id').value = '';
    }

    modal.classList.add('show');
    modal.style.display = 'block';
}

export function closeGroupModal() {
    const modal = document.getElementById('group-modal');
    modal.style.display = 'none';
    modal.classList.remove('show');
    document.getElementById('group-form').reset();
}

export async function handleGroupSubmit(e) {
    e.preventDefault();
    const id = document.getElementById('group-id').value;
    const groupData = {
        name: document.getElementById('group-name').value,
        description: document.getElementById('group-description').value || null,
    };

    try {
        const submitBtn = e.target.querySelector('button[type="submit"]');
        const originalText = submitBtn.textContent;
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner"></span> Сохранение...';

        if (id) {
            await groupsAPI.update(parseInt(id), groupData);
            showToast('Группа обновлена', 'success');
        } else {
            await groupsAPI.create(groupData);
            showToast('Группа создана', 'success');
        }

        closeGroupModal();
        await loadData();

        submitBtn.disabled = false;
        submitBtn.textContent = originalText;
    } catch (error) {
        showToast('Ошибка сохранения группы: ' + error.message, 'error');
        const submitBtn = e.target.querySelector('button[type="submit"]');
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Сохранить';
    }
}

/**
 * Handle task form submission.
 */
export async function handleTaskSubmit(e) {
    e.preventDefault();
    const id = document.getElementById('task-id').value;
    const taskType = document.getElementById('task-type').value;
    const taskSchedulingType = document.getElementById('task-is-recurring').value;
    const groupId = document.getElementById('task-group-id').value;

    const submitBtn = e.target.querySelector('button[type="submit"]');
    const originalText = submitBtn.textContent;

    try {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner"></span> Сохранение...';

        const groupIdValue = groupId ? parseInt(groupId) : null;

        const taskData = {};
        const assigneeSelect = document.getElementById('task-assignees');
        const assignedIds = assigneeSelect && !assigneeSelect.disabled
            ? Array.from(assigneeSelect.selectedOptions)
                .map(opt => parseInt(opt.value, 10))
                .filter(id => !Number.isNaN(id))
            : [];
        taskData.assigned_user_ids = assignedIds;

        // Всегда обновляем базовые поля
        taskData.title = document.getElementById('task-title').value;
        if (document.getElementById('task-description').value) {
            taskData.description = document.getElementById('task-description').value;
        }
        taskData.task_type = taskSchedulingType;
        if (groupIdValue !== null) {
            taskData.group_id = groupIdValue;
        }
        // Get date value, preserving original value if unchanged
        // Check if we're using weekday binding for monthly/yearly
        const recurrenceType = document.getElementById('task-recurrence').value;
        const bindingType = document.querySelector('input[name="monthly-yearly-binding"]:checked');
        const useWeekdayBinding = bindingType && bindingType.value === 'weekday' && (recurrenceType === 'monthly' || recurrenceType === 'yearly');

        let reminderTimeValue = null;

        if (useWeekdayBinding) {
            // Calculate date from weekday fields
            const weekdayDay = parseInt(document.getElementById('weekday-day').value); // 0-6 (Monday-Sunday)
            const weekdayNumber = parseInt(document.getElementById('weekday-number').value); // 1-4 or -1
            const weekdayTime = document.getElementById('weekday-time').value; // HH:MM

            // Calculate target date
            const now = new Date();
            let targetYear = now.getFullYear();
            let targetMonth = now.getMonth(); // 0-11

            if (recurrenceType === 'yearly') {
                // For yearly_weekday, we need to use month from date input
                // When editing, use existing reminder_time month
                // When creating new, use month from date input (user selects month)
                const dateInput = document.getElementById('task-due-date');
                const originalValue = dateInput.dataset.originalValue;
                const currentValue = dateInput.value;

                if (originalValue && id) {
                    // Use existing month from reminder_time
                    const existingDate = new Date(originalValue);
                    targetMonth = existingDate.getMonth();
                } else if (currentValue) {
                    // Use month from date input (user selects month)
                    const referenceDate = new Date(currentValue);
                    targetMonth = referenceDate.getMonth();
                } else {
                    // Default to current month
                    targetMonth = now.getMonth();
                }
            }

            // Find the N-th weekday in the target month
            const targetDate = findNthWeekdayInMonth(targetYear, targetMonth, weekdayDay, weekdayNumber);

            // Set time
            const [hours, minutes] = weekdayTime.split(':').map(Number);
            targetDate.setHours(hours, minutes, 0, 0);

            // Format as ISO string (local time, no timezone)
            const year = targetDate.getFullYear();
            const month = String(targetDate.getMonth() + 1).padStart(2, '0');
            const day = String(targetDate.getDate()).padStart(2, '0');
            const hoursStr = String(hours).padStart(2, '0');
            const minutesStr = String(minutes).padStart(2, '0');

            reminderTimeValue = `${year}-${month}-${day}T${hoursStr}:${minutesStr}:00`;
        } else {
            // Use regular date input
            const dateInput = document.getElementById('task-due-date');
            const originalValue = dateInput.dataset.originalValue;
            const currentValue = dateInput.value;

            if (originalValue && id && taskType) {
                // For editing: check if date actually changed
                const originalLocal = formatDatetimeLocal(originalValue);
                if (currentValue === originalLocal) {
                    // Date hasn't changed, use original value
                    reminderTimeValue = originalValue;
                } else {
                    // Date changed, convert new local time
                    reminderTimeValue = parseDatetimeLocal(currentValue);
                }
            } else {
                // For new tasks or if no original value, convert local time
                reminderTimeValue = parseDatetimeLocal(currentValue);
            }
        }

        if (reminderTimeValue) {
            taskData.reminder_time = reminderTimeValue;
        }

        if (taskSchedulingType === 'one_time') {
            // Для разовых задач очищаем все поля повторения, но reminder_time обязателен
            taskData.recurrence_type = null;
            taskData.recurrence_interval = null;
            taskData.interval_days = null;
            taskData.reminder_time = reminderTimeValue;
        } else if (taskSchedulingType === 'recurring') {
            // Universal function for saving recurring task configuration
            // Simply save interval and datetime for any interval type
            let recurrenceType = document.getElementById('task-recurrence').value;

            // For monthly and yearly, determine recurrence_type based on binding option
            if (recurrenceType === 'monthly' || recurrenceType === 'yearly') {
                const bindingType = document.querySelector('input[name="monthly-yearly-binding"]:checked').value;
                if (bindingType === 'weekday') {
                    recurrenceType = recurrenceType === 'monthly' ? 'monthly_weekday' : 'yearly_weekday';
                }
            }

            taskData.recurrence_type = recurrenceType;
            // For all recurrence types, use the interval value from input
            taskData.recurrence_interval = parseInt(document.getElementById('task-interval').value);
            // Явно очищаем interval_days для recurring задач
            taskData.interval_days = null;

            // Save reminder_time as passed (no normalization, no special handling)
            // Normalization is only used for calculating next date and formatting comments on backend
            // For weekday binding, reminder_time is already set above
            taskData.reminder_time = reminderTimeValue;
        } else if (taskSchedulingType === 'interval') {
            taskData.interval_days = parseInt(document.getElementById('task-interval-days').value);
            // Явно очищаем recurrence_type и recurrence_interval для interval задач
            taskData.recurrence_type = null;
            taskData.recurrence_interval = null;
            taskData.reminder_time = reminderTimeValue;
        }

        // Финальная проверка: reminder_time должен быть всегда установлен
        if (!taskData.reminder_time && reminderTimeValue) {
            taskData.reminder_time = reminderTimeValue;
        }

        console.log('Saving task with data:', taskData);

        if (id && taskType) {
            const numericId = parseInt(id);
            // Редактирование существующей задачи
            // Конфликты обрабатываются только на сервере по времени обновления
            await tasksAPI.update(numericId, taskData);
            showToast('Задача обновлена', 'success');
        } else {
            // Создание новой задачи
            await tasksAPI.create(taskData);
            const typeNames = {
                'one_time': 'Разовая задача создана',
                'recurring': 'Повторяющаяся задача создана',
                'interval': 'Интервальная задача создана'
            };
            showToast(typeNames[taskSchedulingType] || 'Задача создана', 'success');
        }

        closeTaskModal();
        await loadData();

        submitBtn.disabled = false;
        submitBtn.innerHTML = originalText;
    } catch (error) {
        console.error('Error saving task:', error);

        // Восстанавливаем кнопку в любом случае
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalText;

        // Показываем ошибку
        let errorMessage = 'Ошибка сохранения задачи';
        if (error && error.message) {
            errorMessage = error.message;
        }

        // Конфликты обрабатываются только на сервере
        // Сервер - источник истины, данные обновляются автоматически при синхронизации

        console.error('Error message:', errorMessage);
        showToast(errorMessage, 'error');
    }
}

export function editGroup(id) {
    openGroupModal(id);
}

export async function deleteGroup(id) {
    if (!confirm('Удалить группу? Все задачи в группе останутся, но будут без группы.')) return;
    try {
        await groupsAPI.delete(id);
        showToast('Группа удалена', 'success');
        await loadData();
    } catch (error) {
        showToast('Ошибка удаления группы: ' + error.message, 'error');
    }
}

// Task CRUD
export async function completeTask(id) {
    try {
        await tasksAPI.complete(id);
        showToast('Задача отмечена как выполненная', 'success');
        await loadData();
    } catch (error) {
        showToast('Ошибка выполнения задачи: ' + error.message, 'error');
    }
}

export async function toggleTaskComplete(id, completed) {
    try {
        const task = allTasks.find(t => t.id === id);
        if (!task) {
            console.error('Task not found:', id);
            await loadData();
            return;
        }

        console.log('Начинаем toggleTaskComplete:', {
            id,
            completed,
            task_type: task.task_type,
            is_completed: task.is_completed,
            completed_field: task.completed,
            enabled: task.enabled,
        });

        if (completed) {
            console.log('Подтверждаем задачу:', {
                id,
                task_type: task.task_type,
                reminder_time: task.reminder_time,
                active: task.active,
            });
            await tasksAPI.complete(id);
            showToast('Задача отмечена как выполненная', 'success');
        } else {
            console.log('Отменяем подтверждение задачи:', {
                id,
                task_type: task.task_type,
                reminder_time: task.reminder_time,
                completed_field: task.completed,
                enabled: task.enabled,
            });
            // Сбрасываем статус подтверждения через API /uncomplete
            await tasksAPI.uncomplete(id);
            showToast('Статус подтверждения сброшен', 'success');
        }

        // Отладка: проверяем задачу после обновления
        await loadData();
        const updatedTask = allTasks.find(t => t.id === id);
        if (updatedTask) {
            console.log('Задача после обновления:', {
                id: updatedTask.id,
                title: updatedTask.title,
                task_type: updatedTask.task_type,
                completed: updatedTask.completed,
                enabled: updatedTask.enabled,
                is_completed: updatedTask.is_completed,
                reminder_time: updatedTask.reminder_time,
            });
        } else {
            console.error('Задача не найдена после обновления:', id);
        }
    } catch (error) {
        console.error('Error toggling task complete:', error);
        showToast('Ошибка обновления задачи: ' + error.message, 'error');
        // Откатываем изменение чекбокса при ошибке
        await loadData();
    }
}

export function editTask(id) {
    openTaskModal(id);
}

export async function deleteTask(id) {
    if (!confirm('Удалить задачу?')) return;
    try {
        await tasksAPI.delete(id);
        showToast('Задача удалена', 'success');
        await loadData();
    } catch (error) {
        showToast('Ошибка удаления задачи: ' + error.message, 'error');
    }
}

// Data loading
export async function loadData() {
    try {
        showLoading('tasks-list');
        // Загружаем полный список задач и отдельный список для вида "Сегодня"
        const [tasks, todayTaskIdsList, groupsData, usersData] = await Promise.all([
            tasksAPI.getAll(),
            tasksAPI.getTodayIds(),
            groupsAPI.getAll(),
            usersAPI.getAll()
        ]);

        todayTaskIds = new Set(todayTaskIdsList || []);
        groups = groupsData;
        users = usersData;
        updateUserFilterOptions();
        updateAssigneeSelect();
        // Apply selected user from cookie (if present)
        applySelectedUserFromCookie();
        renderUsersList();
        if (currentView === 'users') {
            renderUsersView();
        }

        // Все теперь задачи
        const now = new Date();
        const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

        // Map tasks and remove duplicates by ID (keep first occurrence)
        const tasksMap = new Map();
        tasks.forEach(t => {
            // Определяем статус выполнения: задача выполнена если completed = true
            // Галка подтверждения НЕ зависит от reminder_time
            let isCompleted = t.completed === true;

            const mappedTask = {
                ...t,
                type: 'task',
                is_recurring: t.task_type === 'recurring',
                task_type: t.task_type || 'one_time',
                due_date: t.reminder_time,  // reminder_time теперь хранит дату выполнения
                is_completed: isCompleted,
                is_enabled: t.enabled,  // enabled заменяет is_enabled
                assigned_user_ids: Array.isArray(t.assigned_user_ids) ? t.assigned_user_ids.map(Number) : [],
                assignees: Array.isArray(t.assignees) ? t.assignees : []
            };

            // Keep first occurrence if duplicate
            if (!tasksMap.has(mappedTask.id)) {
                tasksMap.set(mappedTask.id, mappedTask);
            }
        });

        allTasks = Array.from(tasksMap.values());

        if (allTasks.length !== tasks.length) {
            console.warn(`Found ${tasks.length - allTasks.length} duplicate tasks, removed`);
        }

        // Сохраняем задачи в кэш для разных видов
        allTasksCache = [...allTasks];

        // Фильтруем задачи для вида "Сегодня" по ID, полученным с бэкенда
        todayTasksCache = allTasks.filter(t => todayTaskIds.has(t.id));

        // Устанавливаем активный вид по умолчанию только при первой загрузке
        if (!document.getElementById('view-today-btn').classList.contains('active') &&
            !document.getElementById('view-all-btn').classList.contains('active')) {
            switchView('today');
        }
        filterAndRenderTasks();
        updateGroupSelect();
    } catch (error) {
        console.error('Failed to load data:', error);
        showToast('Ошибка загрузки данных. Убедитесь, что backend запущен.', 'error');
        document.getElementById('tasks-list').innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">⚠️</div>
                <div class="empty-state-text">Ошибка загрузки</div>
            </div>
        `;
    }
}

// Update functions
export function updateGroupSelect() {
    const select = document.getElementById('task-group-id');
    select.innerHTML = '<option value="">Без группы</option>';
    groups.forEach(group => {
        const option = document.createElement('option');
        option.value = group.id;
        option.textContent = group.name;
        select.appendChild(option);
    });
}

export function updateUserFilterOptions() {
    const select = document.getElementById('user-filter');
    if (!select) return;
    const previousValue = select.value;
    select.innerHTML = '<option value="">Все пользователи</option>';
    users.forEach(user => {
        const option = document.createElement('option');
        option.value = user.id;
        option.textContent = user.is_active ? user.name : `${user.name} (неактивен)`;
        select.appendChild(option);
    });
    if (previousValue && Array.from(select.options).some(opt => opt.value === previousValue)) {
        select.value = previousValue;
    }
    const newValue = select.value;
    selectedUserId = newValue ? parseInt(newValue, 10) : null;
}

export function updateAssigneeSelect(selectedIds = []) {
    const select = document.getElementById('task-assignees');
    if (!select) return;
    const selectedSet = new Set((selectedIds || []).map(Number));
    select.innerHTML = '';
    const activeUsers = users.filter(user => user.is_active);
    const forcedUsers = [];
    selectedSet.forEach(id => {
        if (!activeUsers.some(user => user.id === id)) {
            const found = users.find(user => user.id === id);
            if (found) {
                forcedUsers.push(found);
            }
        }
    });
    const optionsList = [...activeUsers, ...forcedUsers];
    if (!optionsList.length) {
        const option = document.createElement('option');
        option.value = '';
        option.textContent = 'Нет доступных пользователей';
        option.disabled = true;
        select.appendChild(option);
        select.disabled = true;
        return;
    }
    select.disabled = false;
    optionsList.forEach(user => {
        const option = document.createElement('option');
        option.value = user.id;
        option.textContent = user.is_active ? user.name : `${user.name} (неактивен)`;
        if (selectedSet.has(user.id)) {
            option.selected = true;
        }
        select.appendChild(option);
    });
}

export function setAssigneeSelection(selectedIds) {
    updateAssigneeSelect(selectedIds);
}

function applyCurrentViewData() {
    if (currentView === 'today') {
        allTasks = [...todayTasksCache];
    } else if (currentView === 'all') {
        allTasks = [...allTasksCache];
    } else {
        allTasks = [...allTasksCache];
    }
    filterAndRenderTasks();
}

export function resetUserForm() {
    const form = document.getElementById('user-form');
    if (!form) return;
    form.reset();
    const idInput = document.getElementById('user-id');
    if (idInput) idInput.value = '';
    const title = document.getElementById('user-form-title');
    if (title) title.textContent = 'Добавить пользователя';
    const saveBtn = document.getElementById('user-save');
    if (saveBtn) saveBtn.textContent = 'Добавить';
    const roleSelect = document.getElementById('user-role');
    if (roleSelect) roleSelect.value = 'regular';
    const activeCheckbox = document.getElementById('user-active');
    if (activeCheckbox) activeCheckbox.checked = true;
}

// Initialize application
export async function init() {
    const hasCookie = !!getCookie('hp.selectedUserId');
    if (!hasCookie) {
        await showUserPickScreen();
        return; // Не инициализируем интерфейс до выбора пользователя
    }
    await initializeAppIfNeeded();
}

export async function initializeAppIfNeeded() {
    const appLayout = document.getElementById('app-layout');
    const pickScreen = document.getElementById('user-pick-screen');
    if (pickScreen) pickScreen.style.display = 'none';
    if (appLayout) appLayout.style.display = 'block';
    if (!appInitialized) {
        setupEventListeners();
        updateTimePanelVisibility();
        appInitialized = true;
    }
    toggleUserFilterControls(currentView === 'all');
    await loadData();
    connectWebSocket();
}

async function handleTimeShift({ days = 0, hours = 0, minutes = 0 }) {
    if (!adminMode) return;
    try {
        const state = await timeAPI.shift({ days, hours, minutes });
        renderTimeState(state);
        const deltaText =
            days !== 0 ? `${days > 0 ? '+' : ''}${days}д` :
            hours !== 0 ? `${hours > 0 ? '+' : ''}${hours}ч` :
            `${minutes > 0 ? '+' : ''}${minutes}м`;
        showToast(`Текущее время сдвинуто (${deltaText})`, 'success');
        loadData();
    } catch (error) {
        console.error('Failed to shift time', error);
        showToast(error.message || 'Не удалось сдвинуть время', 'error');
    }
}

async function handleTimeSet() {
    if (!adminMode) return;
    const input = document.getElementById('time-set-input');
    if (!input || !input.value) {
        showToast('Выберите дату и время', 'warning');
        return;
    }
    try {
        const state = await timeAPI.set(input.value);
        renderTimeState(state);
        showToast('Текущее время обновлено', 'success');
        loadData();
    } catch (error) {
        console.error('Failed to set time', error);
        showToast(error.message || 'Не удалось установить время', 'error');
    }
}

async function handleTimeReset() {
    if (!adminMode) return;
    try {
        const state = await timeAPI.reset();
        renderTimeState(state);
        showToast('Возврат к системному времени', 'info');
        loadData();
    } catch (error) {
        console.error('Failed to reset time', error);
        showToast('Не удалось сбросить время', 'error');
    }
}

function formatTimeDisplay(isoString) {
    if (!isoString) return '—';
    try {
        const date = new Date(isoString);
        return date.toLocaleString('ru-RU', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
        });
    } catch (e) {
        return isoString;
    }
}

function renderTimeState(state) {
    timeControlState = state;
    const panel = document.getElementById('time-controls');
    if (!panel) return;

    const virtualEl = document.getElementById('time-virtual-value');
    const realEl = document.getElementById('time-real-value');
    const statusEl = document.getElementById('time-override-status');
    const input = document.getElementById('time-set-input');

    if (virtualEl) virtualEl.textContent = formatTimeDisplay(state?.virtual_now);
    if (realEl) realEl.textContent = formatTimeDisplay(state?.real_now);
    if (statusEl) {
        const isOverride = !!state?.override_enabled;
        statusEl.textContent = isOverride ? 'Переопределено' : 'Системное время';
        statusEl.classList.toggle('override-on', isOverride);
    }
    if (input && state?.virtual_now && typeof formatDatetimeLocal === 'function') {
        input.value = formatDatetimeLocal(state.virtual_now);
    }
}

export async function fetchAndRenderTimeState(showErrors = true) {
    try {
        const state = await timeAPI.getState();
        renderTimeState(state);
    } catch (error) {
        console.error('Failed to fetch time state', error);
        if (showErrors) showToast('Не удалось получить время с сервера', 'error');
    }
}

function setupTimeControlButtons() {
    const panel = document.getElementById('time-controls');
    if (!panel) return;

    panel.querySelectorAll('[data-time-shift-days]').forEach(btn => {
        btn.addEventListener('click', () => {
            const days = Number(btn.getAttribute('data-time-shift-days')) || 0;
            handleTimeShift({ days });
        });
    });
    panel.querySelectorAll('[data-time-shift-hours]').forEach(btn => {
        btn.addEventListener('click', () => {
            const hours = Number(btn.getAttribute('data-time-shift-hours')) || 0;
            handleTimeShift({ hours });
        });
    });

    const setBtn = document.getElementById('time-set-btn');
    if (setBtn) {
        setBtn.addEventListener('click', handleTimeSet);
    }

    const resetBtn = document.getElementById('time-reset-btn');
    if (resetBtn) {
        resetBtn.addEventListener('click', handleTimeReset);
    }
}



// Set quick date for task form
export function setQuickDate(type) {
    const dateInput = document.getElementById('task-due-date');
    if (!dateInput) return;

    const now = new Date();
    let date = new Date();

    switch(type) {
        case 'today':
            date = now;
            break;
        case 'tomorrow':
            date = new Date(now.getTime() + 24 * 60 * 60 * 1000);
            break;
        case 'monday':
            // Находим ближайший понедельник
            const dayOfWeek = now.getDay(); // 0 = воскресенье, 1 = понедельник
            let daysUntilMonday;
            if (dayOfWeek === 0) {
                // Если сегодня воскресенье, понедельник завтра
                daysUntilMonday = 1;
            } else if (dayOfWeek === 1) {
                // Если сегодня понедельник, следующий понедельник через 7 дней
                daysUntilMonday = 7;
            } else {
                // Иначе через (8 - dayOfWeek) дней
                daysUntilMonday = 8 - dayOfWeek;
            }
            date = new Date(now.getTime() + daysUntilMonday * 24 * 60 * 60 * 1000);
            break;
    }

    // Устанавливаем время на 9:00 утра
    date.setHours(9, 0, 0, 0);

    // Форматируем для input type="datetime-local"
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');

    dateInput.value = `${year}-${month}-${day}T${hours}:${minutes}`;
}

export function renderUsersView() {
    renderUsersList();
}



export function formatDatetimeLocal(isoString) {
    if (!isoString) return '';
    try {
        const date = new Date(isoString);
        if (Number.isNaN(date.getTime())) return '';
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    } catch (e) {
        return '';
    }
}

export function parseDatetimeLocal(value) {
    if (!value) return null;
    try {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return null;
        return date.toISOString();
    } catch (e) {
        return null;
    }
}

export function findNthWeekdayInMonth(year, month, weekday, n) {
    // Get first day of month
    const firstDay = new Date(year, month, 1);
    // Find first occurrence of weekday in month
    const firstWeekday = firstDay.getDay(); // 0=Sunday, 6=Saturday
    // Convert to Monday=0, Sunday=6
    const firstWeekdayNormalized = (firstWeekday + 6) % 7;

    let daysToFirst = (weekday - firstWeekdayNormalized + 7) % 7;

    if (n === -1) {
        // Find last occurrence: go to last day and work backwards
        const lastDay = new Date(year, month + 1, 0).getDate();
        const lastDate = new Date(year, month, lastDay);
        const lastWeekday = lastDate.getDay();
        const lastWeekdayNormalized = (lastWeekday + 6) % 7;
        const daysFromLast = (lastWeekdayNormalized - weekday + 7) % 7;
        const result = new Date(year, month, lastDay - daysFromLast);
        // Ensure result is still in the same month
        if (result.getMonth() !== month) {
            result.setDate(result.getDate() - 7);
        }
        return result;
    } else {
        // Find N-th occurrence
        const result = new Date(year, month, 1 + daysToFirst + (n - 1) * 7);
        // Check if date is still in the same month
        if (result.getMonth() !== month) {
            // This means we're trying to get a 5th occurrence, which doesn't exist
            // Fall back to last occurrence
            const lastDay = new Date(year, month + 1, 0).getDate();
            const lastDate = new Date(year, month, lastDay);
            const lastWeekday = lastDate.getDay();
            const lastWeekdayNormalized = (lastWeekday + 6) % 7;
            const daysFromLast = (lastWeekdayNormalized - weekday + 7) % 7;
            const resultLast = new Date(year, month, lastDay - daysFromLast);
            if (resultLast.getMonth() !== month) {
                resultLast.setDate(resultLast.getDate() - 7);
            }
            return resultLast;
        }
        return result;
    }
}

/**
 * Switch between views.
 */
export async function switchView(view) {
    if (view === 'users' && !adminMode) {
        view = 'today';
    }
    currentView = view;
    const todayBtn = document.getElementById('view-today-btn');
    const allBtn = document.getElementById('view-all-btn');
    const historyBtn = document.getElementById('view-history-btn');
    const settingsBtn = document.getElementById('view-settings-btn');
    const usersBtn = document.getElementById('view-users-btn');
    const historyFilters = document.getElementById('history-filters-section');
    const tasksFilters = document.getElementById('tasks-filters-section');
    const settingsView = document.getElementById('settings-view');
    const tasksList = document.getElementById('tasks-list');
    const usersView = document.getElementById('users-view');

    // Update button states
    todayBtn.classList.remove('active');
    allBtn.classList.remove('active');
    historyBtn.classList.remove('active');
    if (settingsBtn) settingsBtn.classList.remove('active');
    if (usersBtn) usersBtn.classList.remove('active');
    if (usersView) usersView.style.display = 'none';

    if (view === 'today') {
        todayBtn.classList.add('active');
        historyFilters.style.display = 'none';
        tasksFilters.style.display = 'block';
        // скрыть элементы фильтра пользователя в этом виде
        toggleUserFilterControls(false);
        if (settingsView) settingsView.style.display = 'none';
        if (tasksList) tasksList.style.display = 'block';
        await loadData();
        return;
    } else if (view === 'all') {
        allBtn.classList.add('active');
        historyFilters.style.display = 'none';
        tasksFilters.style.display = 'block';
        // показать элементы фильтра пользователя только в этом виде
        toggleUserFilterControls(true);
        // Сбрасываем фильтр пользователя для "Все задачи" при переключении (показываем все задачи)
        const userFilterSelect = document.getElementById('user-filter');
        if (userFilterSelect) {
            userFilterSelect.value = '';
            allTasksUserFilterId = null;
        }
        if (settingsView) settingsView.style.display = 'none';
        if (tasksList) tasksList.style.display = 'block';
        applyCurrentViewData();
        return;
    } else if (view === 'history') {
        historyBtn.classList.add('active');
        historyFilters.style.display = 'block';
        tasksFilters.style.display = 'none';
        toggleUserFilterControls(false);
        if (settingsView) settingsView.style.display = 'none';
        if (tasksList) tasksList.style.display = 'block';
        renderHistoryView();
        return;
    } else if (view === 'settings') {
        if (settingsBtn) settingsBtn.classList.add('active');
        historyFilters.style.display = 'none';
        tasksFilters.style.display = 'none';
        toggleUserFilterControls(false);
        if (tasksList) tasksList.style.display = 'none';
        if (settingsView) settingsView.style.display = 'block';
        return;
    } else if (view === 'users') {
        if (usersBtn) usersBtn.classList.add('active');
        historyFilters.style.display = 'none';
        tasksFilters.style.display = 'none';
        toggleUserFilterControls(false);
        if (tasksList) tasksList.style.display = 'none';
        if (settingsView) settingsView.style.display = 'none';
        if (usersView) usersView.style.display = 'block';
        renderUsersView();
        return;
    }

    filterAndRenderTasks();
}

// Setup event listeners
export function setupEventListeners() {
    // Add buttons
    document.getElementById('add-task-btn').addEventListener('click', () => openTaskModal());
    document.getElementById('add-group-btn').addEventListener('click', () => openGroupModal());

    // Search input
    document.getElementById('tasks-search').addEventListener('input', (e) => {
        searchQuery = e.target.value;
        filterAndRenderTasks();
    });
    const userFilter = document.getElementById('user-filter');
    if (userFilter) {
        userFilter.addEventListener('change', (e) => {
            const value = e.target.value;
            const userId = value ? parseInt(value, 10) : null;

            // Для вкладки "Все задачи" используем отдельный фильтр (не сохраняем в cookie)
            if (currentView === 'all') {
                allTasksUserFilterId = userId;
            } else {
                // Для других вкладок (включая "Сегодня") используем selectedUserId и сохраняем в cookie
                selectedUserId = userId;
                if (value) {
                    setCookie('hp.selectedUserId', selectedUserId);
                } else {
                    deleteCookie('hp.selectedUserId');
                }
            }
            filterAndRenderTasks();
        });
    }
    const resetUserBtn = document.getElementById('user-reset-btn');
    if (resetUserBtn) {
        resetUserBtn.addEventListener('click', () => {
            if (currentView === 'all') {
                // Для "Все задачи" просто сбрасываем фильтр
                const select = document.getElementById('user-filter');
                if (select) select.value = '';
                allTasksUserFilterId = null;
                showToast('Фильтр по пользователю сброшен', 'info');
            } else {
                // Для других вкладок (включая "Сегодня") сбрасываем selectedUserId и cookie
                deleteCookie('hp.selectedUserId');
                const select = document.getElementById('user-filter');
                if (select) select.value = '';
                selectedUserId = null;
                showToast('Выбор пользователя сброшен', 'info');
            }
            filterAndRenderTasks();
        });
    }

    // Logout button
    const logoutBtn = document.getElementById('logout-btn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async () => {
            // Закрываем WebSocket соединение и отменяем переподключение
            disconnectWebSocket();

            // Удаляем cookie с выбранным пользователем
            deleteCookie('hp.selectedUserId');
            selectedUserId = null;

            // Сбрасываем состояние приложения
            appInitialized = false;

            // Показываем экран выбора пользователя
            await showUserPickScreen();

            showToast('Вы вышли из системы', 'info');
        });
    }

    // Filter button
    document.getElementById('tasks-filter-btn').addEventListener('click', () => toggleTaskFilter());

    // View toggle buttons
    document.getElementById('view-today-btn').addEventListener('click', () => switchView('today'));
    document.getElementById('view-all-btn').addEventListener('click', () => switchView('all'));
    document.getElementById('view-history-btn').addEventListener('click', () => switchView('history'));
    const settingsBtn = document.getElementById('view-settings-btn');
    if (settingsBtn) settingsBtn.addEventListener('click', () => switchView('settings'));
    const usersBtn = document.getElementById('view-users-btn');
    if (usersBtn) usersBtn.addEventListener('click', () => switchView('users'));

    // Admin mode toggle
    document.getElementById('toggle-admin-btn').addEventListener('click', toggleAdminMode);
    setupTimeControlButtons();

    // History filters
    document.getElementById('history-group-filter').addEventListener('change', () => {
        updateHistoryFilters(); // Update task list based on group
        renderHistoryView();
    });
    document.getElementById('history-task-filter').addEventListener('change', renderHistoryView);
    document.getElementById('history-date-from').addEventListener('change', renderHistoryView);
    document.getElementById('history-date-to').addEventListener('change', renderHistoryView);

    // Form submissions
    document.getElementById('task-form').addEventListener('submit', (e) => handleTaskSubmit(e));
    document.getElementById('group-form').addEventListener('submit', handleGroupSubmit);
    const userForm = document.getElementById('user-form');
    if (userForm) {
        userForm.addEventListener('submit', handleUserSubmit);
    }

    // Cancel buttons
    document.getElementById('task-cancel').addEventListener('click', closeTaskModal);
    document.getElementById('group-cancel').addEventListener('click', closeGroupModal);
    const userCancelBtn = document.getElementById('user-cancel');
    if (userCancelBtn) {
        userCancelBtn.addEventListener('click', resetUserForm);
    }

    // Task type toggle
    document.getElementById('task-is-recurring').addEventListener('change', (e) => {
        const recurringFields = document.getElementById('recurring-fields');
        const intervalFields = document.getElementById('interval-fields');
        const dateLabel = document.getElementById('date-label');
        const taskType = e.target.value;

        if (taskType === 'one_time') {
            recurringFields.style.display = 'none';
            intervalFields.style.display = 'none';
            dateLabel.textContent = 'Начало:';
            document.getElementById('task-recurrence').required = false;
            document.getElementById('task-interval').required = false;
            document.getElementById('task-interval-days').required = false;
        } else if (taskType === 'recurring') {
            recurringFields.style.display = 'block';
            intervalFields.style.display = 'none';
            dateLabel.textContent = 'Начало:';
            document.getElementById('task-recurrence').required = true;
            document.getElementById('task-interval').required = true;
            document.getElementById('task-interval-days').required = false;
            // Update interval field visibility based on recurrence type
            updateIntervalFieldVisibility();
            updateMonthlyYearlyOptions();
        } else if (taskType === 'interval') {
            recurringFields.style.display = 'none';
            intervalFields.style.display = 'block';
            dateLabel.textContent = 'Начало:';
            document.getElementById('task-recurrence').required = false;
            document.getElementById('task-interval').required = false;
            document.getElementById('task-interval-days').required = true;
        }

        // Устанавливаем дефолт на сегодня при изменении типа задачи (только для новых задач)
        const taskId = document.getElementById('task-id').value;
        if (!taskId) {
            setQuickDate('today');
        }
    });

    // Recurrence type toggle - show/hide interval field and monthly/yearly options
    document.getElementById('task-recurrence').addEventListener('change', () => {
        updateIntervalFieldVisibility();
        updateMonthlyYearlyOptions();
    });

    // Binding type toggle - show/hide weekday fields
    document.querySelectorAll('input[name="monthly-yearly-binding"]').forEach(radio => {
        radio.addEventListener('change', () => {
            updateMonthlyYearlyOptions();
        });
    });

    // Modal close buttons
    document.querySelectorAll('.close').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const modal = e.target.closest('.modal');
            if (modal.id === 'task-modal') closeTaskModal();
            else if (modal.id === 'group-modal') closeGroupModal();
            else if (modal.id === 'history-modal') closeHistoryModal();
        });
    });

    // Close modals when clicking outside
    window.addEventListener('click', (e) => {
        if (e.target.classList.contains('modal')) {
            e.target.classList.remove('show');
            e.target.style.display = 'none';
        }
    });

    resetUserForm();
    updateAdminNavigation();
}

export function toggleAdminMode() {
    setAdminMode(!adminMode);
    const adminBtn = document.getElementById('toggle-admin-btn');
    const adminText = document.getElementById('admin-mode-text');

    if (adminMode) {
        adminBtn.classList.add('active');
        adminText.textContent = 'Выйти из админ';
    } else {
        adminBtn.classList.remove('active');
        adminText.textContent = 'Админ режим';
    }

    // Re-render current view to show/hide admin features
    if (currentView === 'history') {
        renderHistoryView();
    } else {
        filterAndRenderTasks();
    }

    updateTimePanelVisibility();
    updateAdminNavigation();
    if (adminMode) {
        fetchAndRenderTimeState(false);
    }
}

// Make it global for onclick
window.toggleAdminMode = toggleAdminMode;