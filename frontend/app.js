/**
 * Main application logic for HomePlanner frontend.
 */

let allTasks = []; // Все задачи (для текущего вида)
let todayTasksCache = []; // Кэш задач для вида "Сегодня"
let allTasksCache = []; // Кэш всех задач
let groups = []; // Список групп
let filteredTasks = [];
let searchQuery = '';
let filterState = null;
let currentView = 'today'; // 'today', 'all', 'history', 'settings', 'users'
let adminMode = false; // Режим администратора
let ws = null; // WebSocket connection
let timeControlState = null; // Состояние панели управления временем
let users = []; // Пользователи для назначения
let selectedUserId = null; // ID выбранного пользователя для фильтра
let appInitialized = false; // Флаг инициализации интерфейса
const USER_ROLE_LABELS = {
    admin: 'Админ',
    regular: 'Обычный',
    guest: 'Гость',
};
const USER_STATUS_LABELS = {
    true: 'Активен',
    false: 'Неактивен',
};

// Cookie utilities to persist selected user
function setCookie(name, value, days = 180) {
    const date = new Date();
    date.setTime(date.getTime() + days * 24 * 60 * 60 * 1000);
    const expires = '; expires=' + date.toUTCString();
    const secure = window.location.protocol === 'https:' ? '; Secure' : '';
    document.cookie = `${encodeURIComponent(name)}=${encodeURIComponent(String(value))}${expires}; path=/; SameSite=Lax${secure}`;
}

function getCookie(name) {
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

function deleteCookie(name) {
    document.cookie = `${encodeURIComponent(name)}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; SameSite=Lax`;
}

function applySelectedUserFromCookie() {
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

function getWsUrl() {
    const host =
        (typeof window !== "undefined" && window.HP_BACKEND_HOST) || "192.168.1.2";
    const port =
        (typeof window !== "undefined" && window.HP_BACKEND_PORT) || 8000;
    const path =
        (typeof window !== "undefined" && window.HP_WS_PATH) ||
        "/api/v0.2/tasks/stream";
    return `ws://${host}:${port}${path}`;
}

function applyTaskEventFromWs(action, taskJson, taskId) {
    // For today view, reload from backend to ensure correct filtering
    // (backend filters tasks based on unified logic)
    if (currentView === 'today') {
        // Reload tasks from /today endpoint to ensure correct filtering
        loadData();
        return;
    }
    
    // For other views, update locally
    if (action === 'deleted' && taskId != null) {
        allTasks = allTasks.filter(t => t.id !== taskId);
        filterAndRenderTasks();
        return;
    }
    if (!taskJson) {
        // Без payload'a делаем полную перезагрузку
        loadData();
        return;
    }
    // Преобразуем TaskResponse -> внутреннюю структуру
    const t = taskJson;
    const activeFlag = t.active ?? true;
    const completedFlag = Boolean(t.completed);
    const reminderTime = t.reminder_time ?? null;
    
    // Определяем статус выполнения: задача выполнена если:
    // 1. Для разовых задач: неактивна (active = false)
    // 2. Для повторяющихся и интервальных: completed = true
    let isCompleted = false;
    if (t.task_type === 'one_time') {
        isCompleted = !activeFlag;
    } else {
        isCompleted = completedFlag;
    }
    
    const mapped = {
        ...t,
        type: 'task',
        is_recurring: t.task_type === 'recurring',
        task_type: t.task_type || 'one_time',
        due_date: t.reminder_time,  // reminder_time теперь хранит дату выполнения
        is_completed: isCompleted,
        is_active: activeFlag,  // active заменяет is_active
        assigned_user_ids: Array.isArray(t.assigned_user_ids) ? t.assigned_user_ids.map(Number) : [],
        assignees: Array.isArray(t.assignees) ? t.assignees : [],
        reminder_time: reminderTime,
    };
    const idx = allTasks.findIndex(x => x.id === mapped.id);
    if (idx >= 0) {
        allTasks[idx] = mapped;
    } else {
        allTasks.push(mapped);
    }
    filterAndRenderTasks();
}

function connectWebSocket() {
    try {
        const url = getWsUrl();
        if (ws) {
            try { ws.close(); } catch (_) {}
        }
        ws = new WebSocket(url);
        ws.onopen = () => {
            console.log('[WS] connection opened', url);
        };
        ws.onmessage = (ev) => {
            try {
                console.log('[WS<-]', ev.data);
                const msg = JSON.parse(ev.data);
                if (msg.type === 'task_update') {
                    applyTaskEventFromWs(msg.action, msg.task || null, msg.task_id || null);
                }
            } catch (e) {
                console.error('[WS] parse error', e);
                // Фоллбэк: полная перезагрузка
                loadData();
            }
        };
        ws.onclose = () => {
            console.log('[WS] connection closed, retry in 2s');
            setTimeout(connectWebSocket, 2000);
        };
        ws.onerror = (e) => {
            console.error('[WS] error', e);
        };
    } catch (e) {
        console.error('[WS] failed to connect', e);
    }
}

/**
 * Find the N-th occurrence of a weekday in a given month.
 * 
 * @param {number} year - Year
 * @param {number} month - Month (0-11, where 0=January)
 * @param {number} weekday - Day of week (0=Monday, 6=Sunday)
 * @param {number} n - Which occurrence (1=first, 2=second, 3=third, 4=fourth, -1=last)
 * @returns {Date} Date object for the N-th weekday in the month
 */
function findNthWeekdayInMonth(year, month, weekday, n) {
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
 * Initialize application.
 */
async function init() {
    const hasCookie = !!getCookie('hp.selectedUserId');
    if (!hasCookie) {
        await showUserPickScreen();
        return; // Не инициализируем интерфейс до выбора пользователя
    }
    await initializeAppIfNeeded();
}

async function initializeAppIfNeeded() {
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

async function showUserPickScreen() {
    const appLayout = document.getElementById('app-layout');
    const pickScreen = document.getElementById('user-pick-screen');
    if (appLayout) appLayout.style.display = 'none';
    if (pickScreen) pickScreen.style.display = 'flex';
    try {
        // Берём пользователей, если уже загружены — используем локально; иначе — грузим
        if (!Array.isArray(users) || users.length === 0) {
            users = await usersAPI.getAll();
        }
        renderUserPickButtons(users);
    } catch (e) {
        console.error('Failed to load users for pick screen', e);
        const list = document.getElementById('user-pick-list');
        if (list) {
            list.innerHTML = '<div style="color:#b91c1c;">Не удалось загрузить пользователей. Обновите страницу.</div>';
        }
    }
}

function renderUserPickButtons(userList) {
    const list = document.getElementById('user-pick-list');
    if (!list) return;
    list.innerHTML = (userList || []).map(u => {
        const name = (u && (u.name || u.display_name || `#${u.id}`)) || '—';
        return `<button class="btn btn-primary" data-user-id="${String(u.id)}" style="flex:1 1 auto; min-width:180px;">${name}</button>`;
    }).join('');
    list.querySelectorAll('button[data-user-id]').forEach(btn => {
        btn.addEventListener('click', async (e) => {
            const id = parseInt(e.currentTarget.getAttribute('data-user-id'), 10);
            if (!Number.isFinite(id)) return;
            setCookie('hp.selectedUserId', id);
            selectedUserId = id;
            await initializeAppIfNeeded();
            showToast('Пользователь выбран', 'success');
        });
    });
}

/**
 * Update interval field visibility based on task type.
 */
function updateIntervalFieldVisibility() {
    const taskType = document.getElementById('task-is-recurring').value;
    const recurrenceType = document.getElementById('task-recurrence').value;
    const intervalField = document.getElementById('task-interval').closest('.form-group');
    
    if (taskType === 'recurring') {
        // Показываем поле "Интервал" для всех типов повторения (включая будни и выходные)
        intervalField.style.display = 'block';
        document.getElementById('task-interval').required = true;
    }
}

/**
 * Update monthly/yearly options visibility.
 */
function updateMonthlyYearlyOptions() {
    const taskType = document.getElementById('task-is-recurring').value;
    const recurrenceType = document.getElementById('task-recurrence').value;
    const monthlyYearlyOptions = document.getElementById('monthly-yearly-options');
    const weekdayBindingFields = document.getElementById('weekday-binding-fields');
    const dueDateField = document.getElementById('due-date-field');
    const bindingType = document.querySelector('input[name="monthly-yearly-binding"]:checked');
    
    if (taskType === 'recurring' && (recurrenceType === 'monthly' || recurrenceType === 'yearly')) {
        monthlyYearlyOptions.style.display = 'block';
        // Show/hide fields based on binding type
        if (bindingType && bindingType.value === 'weekday') {
            weekdayBindingFields.style.display = 'block';
            // For yearly_weekday, we still need to show date field for month selection
            if (recurrenceType === 'yearly') {
                dueDateField.style.display = 'block';
                document.getElementById('date-label').textContent = 'Месяц (для выбора месяца года):';
                // Make date field required but only for month selection
                document.getElementById('task-due-date').required = true;
            } else {
                // For monthly_weekday, hide date field
                dueDateField.style.display = 'none';
                document.getElementById('task-due-date').required = false;
            }
            // Make weekday fields required
            document.getElementById('weekday-day').required = true;
            document.getElementById('weekday-number').required = true;
            document.getElementById('weekday-time').required = true;
        } else {
            weekdayBindingFields.style.display = 'none';
            dueDateField.style.display = 'block';
            document.getElementById('date-label').textContent = 'Начало:';
            // Make weekday fields not required
            document.getElementById('weekday-day').required = false;
            document.getElementById('weekday-number').required = false;
            document.getElementById('weekday-time').required = false;
            // Make regular date field required
            document.getElementById('task-due-date').required = true;
        }
    } else {
        monthlyYearlyOptions.style.display = 'none';
        weekdayBindingFields.style.display = 'none';
        dueDateField.style.display = 'block';
        document.getElementById('date-label').textContent = 'Начало:';
        // Make weekday fields not required when hidden
        document.getElementById('weekday-day').required = false;
        document.getElementById('weekday-number').required = false;
        document.getElementById('weekday-time').required = false;
        // Make regular date field required
        document.getElementById('task-due-date').required = true;
    }
}

/**
 * Setup event listeners.
 */
function setupEventListeners() {
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
            selectedUserId = value ? parseInt(value, 10) : null;
            if (value) {
                setCookie('hp.selectedUserId', selectedUserId);
            } else {
                deleteCookie('hp.selectedUserId');
            }
            filterAndRenderTasks();
        });
    }
    const resetUserBtn = document.getElementById('user-reset-btn');
    if (resetUserBtn) {
        resetUserBtn.addEventListener('click', () => {
            deleteCookie('hp.selectedUserId');
            const select = document.getElementById('user-filter');
            if (select) select.value = '';
            selectedUserId = null;
            showToast('Выбор пользователя сброшен', 'info');
            filterAndRenderTasks();
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
    document.getElementById('task-form').addEventListener('submit', handleTaskSubmit);
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

function mapTaskResponse(task) {
    const reminderTime = task.reminder_time ?? null;
    const activeFlag = task.active ?? true;
    const completedFlag = Boolean(task.completed);
    return {
        ...task,
        type: 'task',
        is_recurring: task.task_type === 'recurring',
        task_type: task.task_type || 'one_time',
        reminder_time: reminderTime,
        active: activeFlag,
        completed: completedFlag,
    };
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

/**
 * Load all data from API.
 */
async function loadData() {
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
        
        allTasks = tasks.map(t => {
            // Определяем статус выполнения: задача выполнена если:
            // 1. Для разовых задач: неактивна (active = false)
            // 2. Для повторяющихся и интервальных: completed = true
            // Галка подтверждения НЕ зависит от reminder_time
            let isCompleted = false;
            
            // Для разовых задач: выполнена если неактивна
            if (t.task_type === 'one_time') {
                isCompleted = !t.active;
            } else {
                // Для повторяющихся и интервальных задач: выполнена если completed = true
                isCompleted = t.completed === true;
            }
            
            return {
                ...t, 
                type: 'task', 
                is_recurring: t.task_type === 'recurring',
                task_type: t.task_type || 'one_time',
                due_date: t.reminder_time,  // reminder_time теперь хранит дату выполнения
                is_completed: isCompleted,
                is_active: t.active,  // active заменяет is_active
                assigned_user_ids: Array.isArray(t.assigned_user_ids) ? t.assigned_user_ids.map(Number) : [],
                assignees: Array.isArray(t.assignees) ? t.assignees : []
            };
        });
        
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

/**
 * Update group select in task modal.
 */
function updateGroupSelect() {
    const select = document.getElementById('task-group-id');
    select.innerHTML = '<option value="">Без группы</option>';
    groups.forEach(group => {
        const option = document.createElement('option');
        option.value = group.id;
        option.textContent = group.name;
        select.appendChild(option);
    });
}

function updateUserFilterOptions() {
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

function updateAssigneeSelect(selectedIds = []) {
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

function setAssigneeSelection(selectedIds) {
    updateAssigneeSelect(selectedIds);
}

function renderUsersList() {
    const container = document.getElementById('users-list');
    if (!container) return;
    const sortedUsers = [...users].sort((a, b) => {
        if (a.is_active !== b.is_active) {
            return a.is_active ? -1 : 1;
        }
        return a.name.localeCompare(b.name, 'ru');
    });
    if (!sortedUsers.length) {
        container.innerHTML = `
            <div class="empty-state" style="min-height: unset; padding: 12px;">
                <div class="empty-state-icon">👥</div>
                <div class="empty-state-text">Нет пользователей</div>
                <div class="empty-state-hint">Добавьте хотя бы одного пользователя, чтобы назначать задачи</div>
            </div>
        `;
        return;
    }
    container.innerHTML = sortedUsers.map(user => `
        <div class="user-row">
            <div class="user-info">
                <span class="user-name">${escapeHtml(user.name)}</span>
                ${user.email ? `<span class="user-email">${escapeHtml(user.email)}</span>` : '<span class="user-email">Без email</span>'}
                <div class="user-meta">
                    <span class="user-chip">${USER_ROLE_LABELS[user.role] || user.role}</span>
                    <span class="user-chip ${user.is_active ? 'user-chip-active' : 'user-chip-inactive'}">${USER_STATUS_LABELS[user.is_active] || ''}</span>
                </div>
            </div>
            <div class="user-actions">
                <button class="btn btn-secondary btn-sm" onclick="editUser(${user.id})" title="Редактировать">✎</button>
                <button class="btn btn-danger btn-sm" onclick="deleteUser(${user.id})" title="Удалить">✕</button>
            </div>
        </div>
    `).join('');
}

function renderUsersView() {
    renderUsersList();
}

function resetUserForm() {
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

async function handleUserSubmit(e) {
    e.preventDefault();
    const id = document.getElementById('user-id').value;
    const name = document.getElementById('user-name').value.trim();
    const email = document.getElementById('user-email').value.trim();
    if (!name) {
        showToast('Имя пользователя обязательно', 'warning');
        return;
    }
    const role = document.getElementById('user-role').value;
    const isActive = document.getElementById('user-active').checked;
    const payload = { name, email: email || null, role, is_active: isActive };
    try {
        if (id) {
            await usersAPI.update(parseInt(id, 10), payload);
            showToast('Пользователь обновлён', 'success');
        } else {
            await usersAPI.create(payload);
            showToast('Пользователь создан', 'success');
        }
        resetUserForm();
        await loadData();
    } catch (error) {
        console.error('Failed to save user', error);
        showToast(error.message || 'Не удалось сохранить пользователя', 'error');
    }
}

function editUser(id) {
    const user = users.find(u => u.id === id);
    if (!user) return;
    const title = document.getElementById('user-form-title');
    if (title) title.textContent = 'Редактировать пользователя';
    document.getElementById('user-id').value = user.id;
    document.getElementById('user-name').value = user.name;
    document.getElementById('user-email').value = user.email || '';
    const roleSelect = document.getElementById('user-role');
    if (roleSelect) roleSelect.value = user.role || 'regular';
    const activeCheckbox = document.getElementById('user-active');
    if (activeCheckbox) activeCheckbox.checked = !!user.is_active;
    const saveBtn = document.getElementById('user-save');
    if (saveBtn) saveBtn.textContent = 'Сохранить';
    switchView('users');
}

async function deleteUser(id) {
    if (!confirm('Удалить пользователя? Назначенные задачи потеряют связь с ним.')) return;
    try {
        await usersAPI.delete(id);
        showToast('Пользователь удалён', 'success');
        resetUserForm();
        await loadData();
    } catch (error) {
        console.error('Failed to delete user', error);
        showToast(error.message || 'Не удалось удалить пользователя', 'error');
    }
}

/**
 * Switch between views.
 */
function switchView(view) {
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
        applyCurrentViewData();
        return;
    } else if (view === 'all') {
        allBtn.classList.add('active');
        historyFilters.style.display = 'none';
        tasksFilters.style.display = 'block';
        // показать элементы фильтра пользователя только в этом виде
        toggleUserFilterControls(true);
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

function toggleUserFilterControls(visible) {
    const select = document.getElementById('user-filter');
    const resetBtn = document.getElementById('user-reset-btn');
    if (select) select.parentElement.style.display = visible ? 'block' : 'none';
    if (resetBtn) resetBtn.style.display = visible ? 'block' : 'none';
}
/**
 * Toggle admin mode.
 */
function toggleAdminMode() {
    adminMode = !adminMode;
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

function updateTimePanelVisibility() {
    const panel = document.getElementById('time-controls');
    if (!panel) return;
    panel.style.display = adminMode ? 'block' : 'none';
}

function updateAdminNavigation() {
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

async function fetchAndRenderTimeState(showErrors = true) {
    try {
        const state = await timeAPI.getState();
        renderTimeState(state);
    } catch (error) {
        console.error('Failed to fetch time state', error);
        if (showErrors) showToast('Не удалось получить время с сервера', 'error');
    }
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

/**
 * Filter and render tasks.
 */
function filterAndRenderTasks() {
    filteredTasks = allTasks.filter(task => {
        // Ограничиваем задачи для вида "Сегодня" списком, полученным с бэкенда
        if (currentView === 'today' && !todayTaskIds.has(task.id)) {
            return false;
        }
        
        const matchesSearch = !searchQuery || 
            task.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
            (task.description && task.description.toLowerCase().includes(searchQuery.toLowerCase()));
        
        const matchesFilter = filterState === null || 
            (filterState === 'completed' && (task.is_completed || !task.is_active)) ||
            (filterState === 'active' && !task.is_completed && task.is_active);

        const matchesUser = !selectedUserId || (task.assigned_user_ids || []).includes(selectedUserId);
        
        return matchesSearch && matchesFilter && matchesUser;
    });

    // Sort by due date
    filteredTasks.sort((a, b) => {
        const aTime = a.due_date ? new Date(a.due_date).getTime() : Number.MAX_SAFE_INTEGER;
        const bTime = b.due_date ? new Date(b.due_date).getTime() : Number.MAX_SAFE_INTEGER;
        return aTime - bTime;
    });
    
    renderTasks();
}

/**
 * Render tasks list grouped by groups.
 */
function renderTasks() {
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

/**
 * Render today view - simple list with checkboxes.
 */
function renderTodayView() {
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

function categorizeTasksByTime(tasks, referenceDate) {
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

function getTaskTimeCategory(task, referenceDate, todayStart, yesterdayStart) {
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

function getReferenceDate() {
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

function getTaskTimestamp(task) {
    const timeSource = task.reminder_time || task.due_date;
    if (!timeSource) {
        return Number.POSITIVE_INFINITY;
    }
    const timestamp = new Date(timeSource).getTime();
    return Number.isNaN(timestamp) ? Number.POSITIVE_INFINITY : timestamp;
}

function sortTasksByReminderTime(tasks) {
    return [...tasks].sort((a, b) => getTaskTimestamp(a) - getTaskTimestamp(b));
}

function renderTodayTasksCollection(tasks, referenceDate) {
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

/**
 * Render single task item for today view.
 */
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

/**
 * Render all tasks view - grouped with details.
 */
function renderAllTasksView() {
    const container = document.getElementById('tasks-list');

    // Разделяем активные и неактивные, далее группируем каждый набор по группам
    const activeTasks = filteredTasks.filter(t => t.is_active);
    const inactiveTasks = filteredTasks.filter(t => !t.is_active);
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
                        <span class="task-group-title-text">Неактивные</span>
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

/**
 * Render header row for all tasks table layout.
 */
function renderAllTasksHeader() {
    return `
        <div class="task-table-header">
            <div class="task-row-cell task-row-title">Задача</div>
            <div class="task-row-cell task-row-config">Конфигурация</div>
            <div class="task-row-cell task-row-users">Пользователи</div>
            <div class="task-row-cell task-row-date">Следующая дата</div>
            <div class="task-row-cell task-row-status">Статус</div>
            <div class="task-row-cell task-row-actions">Действия</div>
        </div>
    `;
}

/**
 * Render task card for all tasks view with details.
 */
function renderAllTasksCard(task, now) {
    const isCompleted = Boolean(task.is_completed);
    const isActive = Boolean(task.is_active);
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

    const statusText = isActive
        ? (isCompleted ? 'Выполнена' : 'Активна')
        : 'Выключена';

    const configText = task.readable_config || 'Не указано';
    const dueDateText = task.due_date ? formatDateTime(task.due_date) : '—';
    const assigneesList = Array.isArray(task.assignees) ? task.assignees : [];
    const assigneesHtml = assigneesList.length
        ? assigneesList.map(user => `<span class="user-chip">${escapeHtml(user.name)}</span>`).join('')
        : '<span style="color: var(--text-secondary); font-size: 0.85rem;">Не назначено</span>';
    const rowClasses = [
        'task-row',
        isCompleted ? 'completed' : '',
        !isCompleted && isActive && category === 'overdue' ? 'overdue' : '',
        !isCompleted && isActive && category === 'current' ? 'current' : '',
        !isCompleted && isActive && category === 'planned' ? 'planned' : '',
        !isActive ? 'inactive' : '',
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
            <div class="task-row-cell task-row-users">${assigneesHtml}</div>
            <div class="task-row-cell task-row-date">${dueDateText}</div>
            <div class="task-row-cell task-row-status">
                <span class="status-indicator ${isCompleted ? 'status-completed' : isActive ? 'status-active' : 'status-inactive'}"></span>
                <span>${statusText}</span>
            </div>
            <div class="task-row-cell task-row-actions">
                <button class="btn btn-secondary btn-icon" onclick="editTask(${task.id})" title="Редактировать">✎</button>
                <button class="btn btn-danger btn-icon" onclick="deleteTask(${task.id})" title="Удалить">✕</button>
            </div>
        </div>
    `;
}

/**
 * Render single task card (legacy, kept for compatibility).
 */
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

/**
 * Open task modal for creating new task.
 */
function openTaskModal(taskId = null) {
    const modal = document.getElementById('task-modal');
    const form = document.getElementById('task-form');
    const title = document.getElementById('task-modal-title');
    const dateInput = document.getElementById('task-due-date');

    updateGroupSelect();
    updateAssigneeSelect();
    
    // Убираем ограничение min для даты (чтобы можно было ставить даты в прошлом)
    // Это нужно делать каждый раз при открытии модального окна
    dateInput.removeAttribute('min');

    if (taskId) {
        const task = allTasks.find(t => t.id === taskId);
        if (task) {
            title.textContent = 'Редактировать задачу';
            document.getElementById('task-id').value = task.id;
            document.getElementById('task-type').value = 'task';
            document.getElementById('task-revision').value = task.revision ?? '';
            document.getElementById('task-title').value = task.title;
            document.getElementById('task-description').value = task.description || '';
            document.getElementById('task-group-id').value = task.group_id || '';
            setAssigneeSelection(task.assigned_user_ids || []);
            // Store original value in data attribute for comparison
            dateInput.dataset.originalValue = task.reminder_time || '';
            dateInput.value = task.reminder_time ? formatDatetimeLocal(task.reminder_time) : '';
            
            // Определяем тип задачи
            const taskSchedulingType = task.task_type || 'one_time';
            document.getElementById('task-is-recurring').value = taskSchedulingType;
            
            if (taskSchedulingType === 'one_time') {
                document.getElementById('recurring-fields').style.display = 'none';
                document.getElementById('interval-fields').style.display = 'none';
                document.getElementById('date-label').textContent = 'Начало:';
                // Hide weekday fields and remove required
                document.getElementById('weekday-binding-fields').style.display = 'none';
                document.getElementById('weekday-day').required = false;
                document.getElementById('weekday-number').required = false;
                document.getElementById('weekday-time').required = false;
            } else if (taskSchedulingType === 'recurring') {
                let recurrenceType = task.recurrence_type || 'daily';
                
                // Determine binding type for monthly/yearly and set recurrence type
                let bindingType = 'date'; // default
                if (recurrenceType === 'monthly_weekday') {
                    recurrenceType = 'monthly';
                    bindingType = 'weekday';
                } else if (recurrenceType === 'yearly_weekday') {
                    recurrenceType = 'yearly';
                    bindingType = 'weekday';
                }
                
                document.getElementById('task-recurrence').value = recurrenceType;
                document.getElementById('task-interval').value = task.recurrence_interval || 1;
                document.getElementById('recurring-fields').style.display = 'block';
                document.getElementById('interval-fields').style.display = 'none';
                document.getElementById('date-label').textContent = 'Начало:';
                
                // Set binding type for monthly/yearly
                if (recurrenceType === 'monthly' || recurrenceType === 'yearly') {
                    document.getElementById(bindingType === 'weekday' ? 'binding-weekday' : 'binding-date').checked = true;
                    
                    // If weekday binding, fill weekday fields from reminder_time
                    if (bindingType === 'weekday' && task.reminder_time) {
                        const reminderDate = new Date(task.reminder_time);
                        // Get day of week (0=Monday, 6=Sunday)
                        const dayOfWeek = (reminderDate.getDay() + 6) % 7;
                        // Get time
                        const hours = String(reminderDate.getHours()).padStart(2, '0');
                        const minutes = String(reminderDate.getMinutes()).padStart(2, '0');
                        
                        // Determine which occurrence (1-4 or -1 for last)
                        // Use backend logic: check if this is the last occurrence
                        const year = reminderDate.getFullYear();
                        const month = reminderDate.getMonth();
                        const dayOfMonth = reminderDate.getDate();
                        
                        // Find last occurrence
                        const lastDay = new Date(year, month + 1, 0).getDate();
                        const lastDate = new Date(year, month, lastDay);
                        const lastWeekday = lastDate.getDay();
                        const lastWeekdayNormalized = (lastWeekday + 6) % 7;
                        const daysFromLast = (lastWeekdayNormalized - dayOfWeek + 7) % 7;
                        const lastOccurrence = new Date(year, month, lastDay - daysFromLast);
                        if (lastOccurrence.getMonth() !== month) {
                            lastOccurrence.setDate(lastOccurrence.getDate() - 7);
                        }
                        
                        let weekdayNumber = 1;
                        if (lastOccurrence.getDate() === dayOfMonth) {
                            weekdayNumber = -1; // Last occurrence
                        } else {
                            // Calculate which occurrence (1-4)
                            const firstDay = new Date(year, month, 1);
                            const firstWeekday = firstDay.getDay();
                            const firstWeekdayNormalized = (firstWeekday + 6) % 7;
                            const daysToFirst = (dayOfWeek - firstWeekdayNormalized + 7) % 7;
                            const firstOccurrence = new Date(year, month, 1 + daysToFirst);
                            const daysDiff = dayOfMonth - firstOccurrence.getDate();
                            weekdayNumber = Math.floor(daysDiff / 7) + 1;
                            weekdayNumber = Math.min(weekdayNumber, 4);
                        }
                        
                        document.getElementById('weekday-day').value = dayOfWeek;
                        document.getElementById('weekday-number').value = weekdayNumber;
                        document.getElementById('weekday-time').value = `${hours}:${minutes}`;
                        
                        // For yearly_weekday, also set date input to show month
                        if (recurrenceType === 'yearly' && task.reminder_time) {
                            const reminderDate = new Date(task.reminder_time);
                            // Set date input to a date in the correct month (first day of month)
                            const month = reminderDate.getMonth() + 1;
                            const year = reminderDate.getFullYear();
                            const monthStr = String(month).padStart(2, '0');
                            const dateStr = `${year}-${monthStr}-01T00:00`;
                            dateInput.value = dateStr;
                            dateInput.dataset.originalValue = task.reminder_time;
                        }
                    } else if (task.reminder_time) {
                        // For date binding, use reminder_time for date input
                        dateInput.value = formatDatetimeLocal(task.reminder_time);
                        dateInput.dataset.originalValue = task.reminder_time;
                    }
                }
                
                // Update interval field visibility based on recurrence type
                updateIntervalFieldVisibility();
                updateMonthlyYearlyOptions();
            } else if (taskSchedulingType === 'interval') {
                document.getElementById('task-interval-days').value = task.interval_days || 7;
                document.getElementById('recurring-fields').style.display = 'none';
                document.getElementById('interval-fields').style.display = 'block';
                document.getElementById('date-label').textContent = 'Начало:';
                // Hide weekday fields and remove required
                document.getElementById('weekday-binding-fields').style.display = 'none';
                document.getElementById('weekday-day').required = false;
                document.getElementById('weekday-number').required = false;
                document.getElementById('weekday-time').required = false;
            } else {
                // Если тип не определен, считаем разовой
                document.getElementById('task-is-recurring').value = 'one_time';
                document.getElementById('recurring-fields').style.display = 'none';
                document.getElementById('interval-fields').style.display = 'none';
                document.getElementById('date-label').textContent = 'Начало:';
                // Hide weekday fields and remove required
                document.getElementById('weekday-binding-fields').style.display = 'none';
                document.getElementById('weekday-day').required = false;
                document.getElementById('weekday-number').required = false;
                document.getElementById('weekday-time').required = false;
            }
        }
    } else {
        title.textContent = 'Добавить задачу';
        form.reset();
        document.getElementById('task-id').value = '';
        document.getElementById('task-type').value = '';
        document.getElementById('task-revision').value = '';
        document.getElementById('task-group-id').value = '';
        setAssigneeSelection([]);
        document.getElementById('task-is-recurring').value = 'one_time';
        document.getElementById('task-interval').value = '1';
        document.getElementById('task-interval-days').value = '7';
        document.getElementById('recurring-fields').style.display = 'none';
        document.getElementById('interval-fields').style.display = 'none';
        document.getElementById('date-label').textContent = 'Начало:';
        // Clear original value for new tasks
        dateInput.removeAttribute('data-original-value');
        // Reset binding type to default (date)
        document.getElementById('binding-date').checked = true;
        // Hide monthly/yearly options
        document.getElementById('monthly-yearly-options').style.display = 'none';
        // Hide weekday fields and remove required
        document.getElementById('weekday-binding-fields').style.display = 'none';
        document.getElementById('weekday-day').required = false;
        document.getElementById('weekday-number').required = false;
        document.getElementById('weekday-time').required = false;
        // Reset weekday fields
        document.getElementById('weekday-day').value = '0';
        document.getElementById('weekday-number').value = '1';
        document.getElementById('weekday-time').value = '09:00';
        setQuickDate('today');
    }

    modal.classList.add('show');
    modal.style.display = 'block';
}

/**
 * Close task modal.
 */
function closeTaskModal() {
    const modal = document.getElementById('task-modal');
    modal.style.display = 'none';
    modal.classList.remove('show');
    document.getElementById('task-form').reset();
    document.getElementById('recurring-fields').style.display = 'none';
    document.getElementById('interval-fields').style.display = 'none';
    // Hide weekday fields and remove required
    document.getElementById('weekday-binding-fields').style.display = 'none';
    document.getElementById('weekday-day').required = false;
    document.getElementById('weekday-number').required = false;
    document.getElementById('weekday-time').required = false;
}

/**
 * Open group modal.
 */
function openGroupModal(groupId = null) {
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

/**
 * Close group modal.
 */
function closeGroupModal() {
    const modal = document.getElementById('group-modal');
    modal.style.display = 'none';
    modal.classList.remove('show');
    document.getElementById('group-form').reset();
}

/**
 * Handle task form submission.
 */
async function handleTaskSubmit(e) {
    e.preventDefault();
    const id = document.getElementById('task-id').value;
    const taskType = document.getElementById('task-type').value;
    const taskSchedulingType = document.getElementById('task-is-recurring').value;
    const revisionInput = document.getElementById('task-revision');
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
            const existingTask = allTasks.find(t => t.id === numericId);
            if (existingTask) {
                const revisionValue = revisionInput && revisionInput.value !== ''
                    ? parseInt(revisionInput.value, 10)
                    : existingTask.revision ?? 0;
                taskData.revision = revisionValue;
            }
            // Редактирование существующей задачи
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

        if (error && error.code === 'conflict_revision') {
            errorMessage = errorMessage || 'Конфликт версий. Обновляем данные задачи.';
            await loadData();
            if (id) {
                openTaskModal(parseInt(id, 10));
            }
        }

        console.error('Error message:', errorMessage);
        showToast(errorMessage, 'error');
    }
}

/**
 * Handle group form submission.
 */
async function handleGroupSubmit(e) {
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
 * Edit group.
 */
function editGroup(id) {
    openGroupModal(id);
}

/**
 * Delete group.
 */
async function deleteGroup(id) {
    if (!confirm('Удалить группу? Все задачи в группе останутся, но будут без группы.')) return;
    try {
        await groupsAPI.delete(id);
        showToast('Группа удалена', 'success');
        await loadData();
    } catch (error) {
        showToast('Ошибка удаления группы: ' + error.message, 'error');
    }
}

/**
 * Complete task.
 */
async function completeTask(id) {
    try {
        await tasksAPI.complete(id);
        showToast('Задача отмечена как выполненная', 'success');
        await loadData();
    } catch (error) {
        showToast('Ошибка выполнения задачи: ' + error.message, 'error');
    }
}

/**
 * Toggle task complete status (for today view and all tasks view).
 */
async function toggleTaskComplete(id, completed) {
    try {
        const task = allTasks.find(t => t.id === id);
        if (!task) {
            console.error('Task not found:', id);
            await loadData();
            return;
        }
        
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
            // Сбрасываем статус подтверждения через API /uncomplete
            await tasksAPI.uncomplete(id);
            showToast('Статус подтверждения сброшен', 'success');
        }
        
        // Отладка: проверяем задачу после обновления
        await loadData();
        const updatedTask = allTasks.find(t => t.id === id);
        if (updatedTask) {
            console.log('Задача после подтверждения:', {
                id: updatedTask.id,
                title: updatedTask.title,
                task_type: updatedTask.task_type,
                completed: updatedTask.completed,
                active: updatedTask.active,
                reminder_time: updatedTask.reminder_time,
            });
        }
    } catch (error) {
        console.error('Error toggling task complete:', error);
        showToast('Ошибка обновления задачи: ' + error.message, 'error');
        // Откатываем изменение чекбокса при ошибке
        await loadData();
    }
}

/**
 * Edit task.
 */
function editTask(id) {
    openTaskModal(id);
}

/**
 * Delete task.
 */
async function deleteTask(id) {
    if (!confirm('Удалить задачу?')) return;
    try {
        await tasksAPI.delete(id);
        showToast('Задача удалена', 'success');
        await loadData();
    } catch (error) {
        showToast('Ошибка удаления задачи: ' + error.message, 'error');
    }
}

/**
 * Utility functions.
 */
function formatDateTime(isoString) {
    const date = new Date(isoString);
    return date.toLocaleString('ru-RU', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    });
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Show toast notification.
 */
function showToast(message, type = 'info') {
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

/**
 * Show loading state.
 */
function showLoading(containerId) {
    const container = document.getElementById(containerId);
    container.innerHTML = '<div class="loading"><span class="spinner"></span> Загрузка...</div>';
}

/**
 * Toggle task filter.
 */
function toggleTaskFilter() {
    if (filterState === null) {
        filterState = 'active';
    } else if (filterState === 'active') {
        filterState = 'completed';
    } else {
        filterState = null;
    }
    
    const btn = document.getElementById('tasks-filter-btn');
    const labels = { null: 'Фильтры', active: 'Только активные', completed: 'Только выполненные' };
    btn.textContent = labels[filterState];
    
    filterAndRenderTasks();
}

/**
 * Set quick date for task form.
 */
function setQuickDate(type) {
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

/**
 * View task history.
 */
async function viewTaskHistory(taskId) {
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

/**
 * Render history view with filters.
 */
async function renderHistoryView() {
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

/**
 * Update history filter dropdowns with current data.
 */
function updateHistoryFilters() {
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

/**
 * Close history modal.
 */
function closeHistoryModal() {
    const modal = document.getElementById('history-modal');
    modal.style.display = 'none';
    modal.classList.remove('show');
}

/**
 * Delete history entry.
 */
async function deleteHistoryEntry(historyId, taskId) {
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

// Initialize app on load
document.addEventListener('DOMContentLoaded', init);
