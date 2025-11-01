/**
 * Main application logic for HomePlanner frontend.
 */

let allTasks = []; // Все задачи
let groups = []; // Список групп
let filteredTasks = [];
let searchQuery = '';
let filterState = null;
let currentView = 'today'; // 'today' или 'all'

/**
 * Initialize application.
 */
async function init() {
    setupEventListeners();
    await loadData();
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

    // Filter button
    document.getElementById('tasks-filter-btn').addEventListener('click', () => toggleTaskFilter());

    // View toggle buttons
    document.getElementById('view-today-btn').addEventListener('click', () => switchView('today'));
    document.getElementById('view-all-btn').addEventListener('click', () => switchView('all'));

    // Form submissions
    document.getElementById('task-form').addEventListener('submit', handleTaskSubmit);
    document.getElementById('group-form').addEventListener('submit', handleGroupSubmit);

    // Cancel buttons
    document.getElementById('task-cancel').addEventListener('click', closeTaskModal);
    document.getElementById('group-cancel').addEventListener('click', closeGroupModal);

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

    // Modal close buttons
    document.querySelectorAll('.close').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const modal = e.target.closest('.modal');
            if (modal.id === 'task-modal') closeTaskModal();
            else if (modal.id === 'group-modal') closeGroupModal();
        });
    });

    // Close modals when clicking outside
    window.addEventListener('click', (e) => {
        if (e.target.classList.contains('modal')) {
            e.target.classList.remove('show');
            e.target.style.display = 'none';
        }
    });
}

/**
 * Load all data from API.
 */
async function loadData() {
    try {
        showLoading('tasks-list');
        const [tasks, groupsData] = await Promise.all([
            tasksAPI.getAll(true),
            groupsAPI.getAll()
        ]);
        
        groups = groupsData;
        
        // Все теперь задачи
        const now = new Date();
        const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        
        allTasks = tasks.map(t => {
            // Определяем статус выполнения: задача выполнена если:
            // 1. Она неактивна (is_active = false) - для разовых задач
            // 2. Или она была выполнена сегодня и дата сегодняшняя - для повторяющихся и интервальных
            let isCompleted = !t.is_active;
            
            // Для повторяющихся и интервальных задач проверяем last_completed_at
            if (!isCompleted && t.last_completed_at && (t.task_type === 'recurring' || t.task_type === 'interval')) {
                const completedDate = new Date(t.last_completed_at);
                const taskDate = new Date(t.next_due_date);
                
                // Проверяем, выполнена ли задача сегодня
                const completedToday = completedDate.getFullYear() === today.getFullYear() &&
                                       completedDate.getMonth() === today.getMonth() &&
                                       completedDate.getDate() === today.getDate();
                
                // Проверяем, назначена ли задача на сегодня
                const taskDueToday = taskDate.getFullYear() === today.getFullYear() &&
                                     taskDate.getMonth() === today.getMonth() &&
                                     taskDate.getDate() === today.getDate();
                
                // Если задача была выполнена сегодня и её дата сегодняшняя - она выполнена
                if (completedToday && taskDueToday) {
                    isCompleted = true;
                }
            }
            
            return {
                ...t, 
                type: 'task', 
                is_recurring: t.task_type === 'recurring',
                task_type: t.task_type || 'one_time',
                due_date: t.next_due_date, 
                is_completed: isCompleted,
                is_active: t.is_active,
                last_completed_at: t.last_completed_at
            };
        });
        
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

/**
 * Switch between views.
 */
function switchView(view) {
    currentView = view;
    const todayBtn = document.getElementById('view-today-btn');
    const allBtn = document.getElementById('view-all-btn');
    
    if (view === 'today') {
        todayBtn.classList.add('active');
        allBtn.classList.remove('active');
    } else {
        todayBtn.classList.remove('active');
        allBtn.classList.add('active');
    }
    
    filterAndRenderTasks();
}

/**
 * Filter and render tasks.
 */
function filterAndRenderTasks() {
    filteredTasks = allTasks.filter(task => {
        // Применяем фильтр по виду
        if (currentView === 'today') {
            // Для вида "Сегодня" показываем только задачи на сегодня
            const taskDate = new Date(task.due_date);
            const today = new Date();
            today.setHours(0, 0, 0, 0);
            const tomorrow = new Date(today);
            tomorrow.setDate(tomorrow.getDate() + 1);
            
            if (taskDate < today || taskDate >= tomorrow) {
                return false;
            }
            
            // Показываем все задачи на сегодня, включая выполненные
            // Не фильтруем по is_active - показываем и выполненные задачи
        }
        
        const matchesSearch = !searchQuery || 
            task.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
            (task.description && task.description.toLowerCase().includes(searchQuery.toLowerCase()));
        
        const matchesFilter = filterState === null || 
            (filterState === 'completed' && (task.is_completed || !task.is_active)) ||
            (filterState === 'active' && !task.is_completed && task.is_active);
        
        return matchesSearch && matchesFilter;
    });

    // Sort by due date
    filteredTasks.sort((a, b) => new Date(a.due_date) - new Date(b.due_date));
    
    renderTasks();
}

/**
 * Render tasks list grouped by groups.
 */
function renderTasks() {
    const container = document.getElementById('tasks-list');
    
    if (filteredTasks.length === 0) {
        const emptyMsg = currentView === 'today'
            ? 'Нет задач на сегодня'
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
    } else {
        renderAllTasksView();
    }
}

/**
 * Render today view - simple list with checkboxes.
 */
function renderTodayView() {
    const container = document.getElementById('tasks-list');
    const now = new Date();
    
    // Группируем задачи по группам
    const tasksByGroup = {};
    const tasksWithoutGroup = [];
    
    filteredTasks.forEach(task => {
        const groupId = task.group_id;
        if (groupId) {
            if (!tasksByGroup[groupId]) {
                tasksByGroup[groupId] = [];
            }
            tasksByGroup[groupId].push(task);
        } else {
            tasksWithoutGroup.push(task);
        }
    });

    let html = '<div class="today-tasks-list">';
    
    // Отображаем задачи по группам
    groups.forEach(group => {
        if (tasksByGroup[group.id] && tasksByGroup[group.id].length > 0) {
            tasksByGroup[group.id].forEach(task => {
                html += renderTodayTaskItem(task, group);
            });
        }
    });

    // Отображаем задачи без группы
    tasksWithoutGroup.forEach(task => {
        html += renderTodayTaskItem(task, null);
    });
    
    html += '</div>';
    container.innerHTML = html;
}

/**
 * Render single task item for today view.
 */
function renderTodayTaskItem(task, group) {
    const isCompleted = task.is_completed || !task.is_active;
    const fullTitle = group ? `${group.name}: ${task.title}` : task.title;
    
    return `
        <div class="today-task-item ${isCompleted ? 'completed' : ''}">
            <label class="today-task-checkbox">
                <input type="checkbox" ${isCompleted ? 'checked' : ''} 
                       onchange="toggleTaskComplete(${task.id}, this.checked)"
                       class="task-checkbox">
                <span class="task-title">${escapeHtml(fullTitle)}</span>
            </label>
        </div>
    `;
}

/**
 * Render all tasks view - grouped with details.
 */
function renderAllTasksView() {
    const container = document.getElementById('tasks-list');

    // Группируем задачи по группам
    const tasksByGroup = {};
    const tasksWithoutGroup = [];
    
    filteredTasks.forEach(task => {
        const groupId = task.group_id;
        if (groupId) {
            if (!tasksByGroup[groupId]) {
                tasksByGroup[groupId] = [];
            }
            tasksByGroup[groupId].push(task);
        } else {
            tasksWithoutGroup.push(task);
        }
    });

    const now = new Date();
    let html = '';

    // Отображаем задачи по группам
    groups.forEach(group => {
        if (tasksByGroup[group.id] && tasksByGroup[group.id].length > 0) {
            html += `
                <div class="task-group">
                    <div class="task-group-header">
                        <div class="task-group-header-info">
                            <h3 class="task-group-title">${escapeHtml(group.name)}</h3>
                            ${group.description ? `<p class="task-group-description">${escapeHtml(group.description)}</p>` : ''}
                        </div>
                        <div class="task-group-header-actions">
                            <button class="btn btn-secondary btn-sm" onclick="editGroup(${group.id})" title="Редактировать">✎</button>
                            <button class="btn btn-danger btn-sm" onclick="deleteGroup(${group.id})" title="Удалить">✕</button>
                        </div>
                    </div>
                    <div class="task-group-items">
                        ${tasksByGroup[group.id].map(task => renderAllTasksCard(task, now)).join('')}
                    </div>
                </div>
            `;
        }
    });

    // Отображаем задачи без группы
    if (tasksWithoutGroup.length > 0) {
        html += `
            <div class="task-group">
                <div class="task-group-header">
                    <h3 class="task-group-title">Без группы</h3>
                </div>
                <div class="task-group-items">
                    ${tasksWithoutGroup.map(task => renderAllTasksCard(task, now)).join('')}
                </div>
            </div>
        `;
    }

    container.innerHTML = html;
}

/**
 * Render task card for all tasks view with details.
 */
function renderAllTasksCard(task, now) {
    const taskDate = new Date(task.due_date);
    const isUrgent = taskDate <= new Date(now.getTime() + 24 * 60 * 60 * 1000) && 
                    !task.is_completed && 
                    task.is_active;
    const isPast = taskDate < now && !task.is_completed && 
                  task.is_active;
    
    // Определяем тип задачи
    const taskType = task.task_type || 'one_time';
    let typeLabel = '';
    if (taskType === 'one_time') {
        typeLabel = '📌 Разовое';
    } else if (taskType === 'recurring') {
        const recurrenceText = {
            daily: 'Ежедневно',
            weekly: 'Еженедельно',
            monthly: 'Ежемесячно',
            yearly: 'Ежегодно',
        }[task.recurrence_type] || task.recurrence_type || 'Повторяющаяся';
        typeLabel = `🔄 ${recurrenceText} (каждые ${task.recurrence_interval || 1})`;
    } else if (taskType === 'interval') {
        typeLabel = `⏱️ Интервал (${task.interval_days || 7} дней)`;
    }
    
    // Статус активности
    const activeStatus = task.is_active ? '✅ Активна' : '❌ Неактивна';
    
    const isCompleted = task.is_completed || !task.is_active;
    
    return `
        <div class="item-card ${isCompleted ? 'completed' : ''} ${isUrgent ? 'urgent' : ''}">
            <div class="item-info" style="flex: 1; display: flex; flex-direction: column; gap: 8px;">
                <div style="display: flex; align-items: center; gap: 12px;">
                    <label class="task-checkbox-label" style="cursor: pointer; display: flex; align-items: center;">
                        <input type="checkbox" ${isCompleted ? 'checked' : ''} 
                               onchange="toggleTaskComplete(${task.id}, this.checked)"
                               class="task-checkbox"
                               title="${isCompleted ? 'Отметить как невыполненную' : 'Отметить как выполненную'}">
                        <span class="task-title" style="font-size: 18px; font-weight: 600;">${escapeHtml(task.title)}</span>
                    </label>
                </div>
                ${task.description ? `<div class="item-description">${escapeHtml(task.description)}</div>` : ''}
                <div class="item-meta" style="display: flex; flex-direction: column; gap: 8px; margin-top: 8px;">
                    <div style="display: flex; gap: 16px; flex-wrap: wrap;">
                        <span><strong>Тип:</strong> ${typeLabel}</span>
                        <span><strong>Статус:</strong> ${activeStatus}</span>
                    </div>
                    <div>
                        <span><strong>Дата и время:</strong> 📅 ${formatDateTime(task.due_date)}</span>
                        ${isPast ? '<span style="color: var(--danger-color); margin-left: 12px;">⚠️ Просрочено</span>' : ''}
                    </div>
                </div>
            </div>
            <div class="item-actions">
                <button class="btn btn-secondary" onclick="editTask(${task.id})" title="Редактировать">✎</button>
                <button class="btn btn-danger" onclick="deleteTask(${task.id})" title="Удалить">✕</button>
            </div>
        </div>
    `;
}

/**
 * Render single task card (legacy, kept for compatibility).
 */
function renderTaskCard(task, now) {
    const taskDate = new Date(task.due_date);
    const isUrgent = taskDate <= new Date(now.getTime() + 24 * 60 * 60 * 1000) && 
                    !task.is_completed && 
                    task.is_active;
    const isPast = taskDate < now && !task.is_completed && 
                  task.is_active;
    
    let metaInfo = '';
    const taskType = task.task_type || 'one_time';
    if (taskType === 'interval' && task.interval_days) {
        metaInfo = `<span>⏱️ Через ${task.interval_days} ${task.interval_days === 1 ? 'день' : task.interval_days < 5 ? 'дня' : 'дней'} после подтверждения</span>`;
    } else if (taskType === 'recurring' && task.recurrence_type) {
        const recurrenceText = {
            daily: 'Ежедневно',
            weekly: 'Еженедельно',
            monthly: 'Ежемесячно',
            yearly: 'Ежегодно',
        }[task.recurrence_type] || task.recurrence_type;
        metaInfo = `<span>🔄 ${recurrenceText} (каждые ${task.recurrence_interval})</span>`;
    } else if (taskType === 'one_time') {
        metaInfo = `<span>📌 Разовое</span>`;
    }
    
    return `
        <div class="item-card ${task.is_completed || !task.is_active ? 'completed' : ''} ${isUrgent ? 'urgent' : ''}">
            <div class="item-info">
                <div class="item-title">
                    ${escapeHtml(task.title)}
                    ${task.task_type === 'interval' ? '<span style="font-size: 12px; color: var(--text-secondary); margin-left: 8px;">(интервал)</span>' : ''}
                </div>
                ${task.description ? `<div class="item-description">${escapeHtml(task.description)}</div>` : ''}
                <div class="item-meta">
                    <span>📅 ${formatDateTime(task.due_date)}</span>
                    ${metaInfo}
                    ${isPast ? '<span style="color: var(--danger-color);">⚠️ Просрочено</span>' : ''}
                </div>
            </div>
            <div class="item-actions">
                ${!task.is_completed && task.is_active ? `<button class="btn btn-success" onclick="completeTask(${task.id})" title="Подтвердить выполнение">✓</button>` : ''}
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

    updateGroupSelect();

    if (taskId) {
        const task = allTasks.find(t => t.id === taskId);
        if (task) {
            title.textContent = 'Редактировать задачу';
            document.getElementById('task-id').value = task.id;
            document.getElementById('task-type').value = 'task';
            document.getElementById('task-title').value = task.title;
            document.getElementById('task-description').value = task.description || '';
            document.getElementById('task-group-id').value = task.group_id || '';
            document.getElementById('task-due-date').value = formatDatetimeLocal(task.due_date);
            
            // Определяем тип задачи
            const taskSchedulingType = task.task_type || 'one_time';
            document.getElementById('task-is-recurring').value = taskSchedulingType;
            
            if (taskSchedulingType === 'one_time') {
                document.getElementById('recurring-fields').style.display = 'none';
                document.getElementById('interval-fields').style.display = 'none';
                document.getElementById('date-label').textContent = 'Начало:';
            } else if (taskSchedulingType === 'recurring') {
                document.getElementById('task-recurrence').value = task.recurrence_type || 'daily';
                document.getElementById('task-interval').value = task.recurrence_interval || 1;
                document.getElementById('recurring-fields').style.display = 'block';
                document.getElementById('interval-fields').style.display = 'none';
                document.getElementById('date-label').textContent = 'Начало:';
            } else if (taskSchedulingType === 'interval') {
                document.getElementById('task-interval-days').value = task.interval_days || 7;
                document.getElementById('recurring-fields').style.display = 'none';
                document.getElementById('interval-fields').style.display = 'block';
                document.getElementById('date-label').textContent = 'Начало:';
            } else {
                // Если тип не определен, считаем разовой
                document.getElementById('task-is-recurring').value = 'one_time';
                document.getElementById('recurring-fields').style.display = 'none';
                document.getElementById('interval-fields').style.display = 'none';
                document.getElementById('date-label').textContent = 'Начало:';
            }
        }
    } else {
        title.textContent = 'Добавить задачу';
        form.reset();
        document.getElementById('task-id').value = '';
        document.getElementById('task-type').value = '';
        document.getElementById('task-group-id').value = '';
        document.getElementById('task-is-recurring').value = 'one_time';
        document.getElementById('task-interval').value = '1';
        document.getElementById('task-interval-days').value = '7';
        document.getElementById('recurring-fields').style.display = 'none';
        document.getElementById('interval-fields').style.display = 'none';
        document.getElementById('date-label').textContent = 'Начало:';
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
    const groupId = document.getElementById('task-group-id').value;

    const submitBtn = e.target.querySelector('button[type="submit"]');
    const originalText = submitBtn.textContent;
    
    try {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner"></span> Сохранение...';
        
        const groupIdValue = groupId ? parseInt(groupId) : null;
        
        const taskData = {};
        
        // Всегда обновляем базовые поля
        taskData.title = document.getElementById('task-title').value;
        if (document.getElementById('task-description').value) {
            taskData.description = document.getElementById('task-description').value;
        }
        taskData.task_type = taskSchedulingType;
        if (groupIdValue !== null) {
            taskData.group_id = groupIdValue;
        }
        taskData.next_due_date = parseDatetimeLocal(document.getElementById('task-due-date').value);
        
        if (taskSchedulingType === 'one_time') {
            // Для разовых задач очищаем все поля повторения
            taskData.recurrence_type = null;
            taskData.recurrence_interval = null;
            taskData.interval_days = null;
        } else if (taskSchedulingType === 'recurring') {
            taskData.recurrence_type = document.getElementById('task-recurrence').value;
            taskData.recurrence_interval = parseInt(document.getElementById('task-interval').value);
            // Явно очищаем interval_days для recurring задач
            taskData.interval_days = null;
        } else if (taskSchedulingType === 'interval') {
            taskData.interval_days = parseInt(document.getElementById('task-interval-days').value);
            // Явно очищаем recurrence_type и recurrence_interval для interval задач
            taskData.recurrence_type = null;
            taskData.recurrence_interval = null;
        }
        
        console.log('Saving task with data:', taskData);
        
        if (id && taskType) {
            // Редактирование существующей задачи
            await tasksAPI.update(parseInt(id), taskData);
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
            // Отмечаем задачу как выполненную
            await tasksAPI.complete(id);
            showToast('Задача отмечена как выполненная', 'success');
        } else {
            // Сбрасываем статус подтверждения
            // Для разовых задач - активируем обратно
            // Для повторяющихся и интервальных - сбрасываем last_completed_at если было выполнено сегодня
            const updateData = {};
            
            if (task.task_type === 'one_time') {
                // Для разовых задач просто активируем
                updateData.is_active = true;
            } else {
                // Для повторяющихся и интервальных задач
                // Проверяем, была ли задача выполнена сегодня
                const now = new Date();
                const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
                
                if (task.last_completed_at) {
                    const completedDate = new Date(task.last_completed_at);
                    const completedToday = completedDate.getFullYear() === today.getFullYear() &&
                                          completedDate.getMonth() === today.getMonth() &&
                                          completedDate.getDate() === today.getDate();
                    
                    if (completedToday) {
                        // Если была выполнена сегодня - сбрасываем last_completed_at
                        updateData.last_completed_at = null;
                    }
                }
            }
            
            await tasksAPI.update(id, updateData);
            showToast('Статус подтверждения сброшен', 'success');
        }
        await loadData();
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

// Initialize app on load
document.addEventListener('DOMContentLoaded', init);
