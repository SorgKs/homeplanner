// Filters and view switching functions

import { allTasks, todayTasksCache, allTasksCache, currentView, filterState, adminMode, timeControlState, selectedUserId, allTasksUserFilterId, setFilteredTasks, setAllTasks } from './utils.js';
import { disconnectWebSocket, connectWebSocket } from './websocket.js';
import { renderTasks, renderTodayView, renderAllTasksView } from './rendering.js';
import { renderUsersView } from './user_management.js';
import { showToast, deleteCookie, showLoading } from './utils.js';

export function filterAndRenderTasks() {
    setFilteredTasks(allTasks.filter(task => {
        // Ограничиваем задачи для вида "Сегодня" списком, полученным с бэкенда
        if (currentView === 'today' && !todayTaskIds.has(task.id)) {
            return false;
        }

        const matchesSearch = !searchQuery ||
            task.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
            (task.description && task.description.toLowerCase().includes(searchQuery.toLowerCase()));

        const matchesFilter = filterState === null ||
            (filterState === 'completed' && (task.is_completed || !task.is_enabled)) ||
            (filterState === 'active' && !task.is_completed && task.is_enabled);

        // Для "Сегодня" используем selectedUserId (из cookie), для "Все задачи" - allTasksUserFilterId (из UI, опционально)
        let userIdToFilter = null;
        if (currentView === 'today') {
            userIdToFilter = selectedUserId; // Обязательный фильтр для "Сегодня"
        } else if (currentView === 'all') {
            userIdToFilter = allTasksUserFilterId; // Опциональный фильтр для "Все задачи"
        }
        // Показываем задачи, если:
        // 1. Фильтр не установлен (показываем все)
        // 2. Задача назначена выбранному пользователю
        // 3. Задача не назначена никому (пустой массив assigned_user_ids)
        const taskAssignedIds = task.assigned_user_ids || [];
        const matchesUser = !userIdToFilter ||
            taskAssignedIds.includes(userIdToFilter) ||
            taskAssignedIds.length === 0;

        return matchesSearch && matchesFilter && matchesUser;
    }));

    // Sort by due date
    setFilteredTasks(filteredTasks.sort((a, b) => {
        const aTime = a.due_date ? new Date(a.due_date).getTime() : Number.MAX_SAFE_INTEGER;
        const bTime = b.due_date ? new Date(b.due_date).getTime() : Number.MAX_SAFE_INTEGER;
        return aTime - bTime;
    }));

    renderTasks();
}

export function applyCurrentViewData() {
    if (currentView === 'today') {
        setAllTasks([...todayTasksCache]);
    } else if (currentView === 'all') {
        setAllTasks([...allTasksCache]);
    } else {
        setAllTasks([...allTasksCache]);
    }
    filterAndRenderTasks();
}

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

export function toggleUserFilterControls(visible) {
    const select = document.getElementById('user-filter');
    const resetBtn = document.getElementById('user-reset-btn');
    if (select) select.parentElement.style.display = visible ? 'block' : 'none';
    if (resetBtn) resetBtn.style.display = visible ? 'block' : 'none';
}

export function toggleTaskFilter() {
    if (filterState === null) {
        filterState = 'active';
    } else if (filterState === 'active') {
        filterState = 'completed';
    } else {
        filterState = null;
    }

    const btn = document.getElementById('tasks-filter-btn');
    const labels = { null: 'Фильтры', active: 'Только включенные', completed: 'Только выполненные' };
    btn.textContent = labels[filterState];

    filterAndRenderTasks();
}



export function updateTimePanelVisibility() {
    const panel = document.getElementById('time-controls');
    if (!panel) return;
    panel.style.display = adminMode ? 'block' : 'none';
}

export function updateAdminNavigation() {
    const usersBtn = document.getElementById('view-users-btn');
    if (!usersBtn) return;
    if (adminMode) {
        usersBtn.style.display = 'block';
    } else {
        usersBtn.style.display = 'none';
        if (currentView === 'users') {
            switchView('today');
        }
    }
}

export function setupTimeControlButtons() {
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

export function formatTimeDisplay(isoString) {
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

export function renderTimeState(state) {
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

export async function handleTimeShift({ days = 0, hours = 0, minutes = 0 }) {
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

export async function handleTimeSet() {
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

export async function handleTimeReset() {
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

// Import missing
import { filteredTasks, searchQuery, todayTaskIds, loadData, formatDatetimeLocal } from './utils.js';
import { renderHistoryView } from './history.js';