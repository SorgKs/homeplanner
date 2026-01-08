// Task management functions

import { groups, users, showToast, loadData, setQuickDate, findNthWeekdayInMonth } from './utils.js';
import { updateGroupSelect, updateAssigneeSelect, setAssigneeSelection } from './utils.js';

export function updateIntervalFieldVisibility() {
    const taskType = document.getElementById('task-is-recurring').value;
    const recurrenceType = document.getElementById('task-recurrence').value;
    const intervalField = document.getElementById('task-interval').closest('.form-group');

    if (taskType === 'recurring') {
        // Показываем поле "Интервал" для всех типов повторения (включая будни и выходные)
        intervalField.style.display = 'block';
        document.getElementById('task-interval').required = true;
    }
}

export function updateMonthlyYearlyOptions() {
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

export function openTaskModal(taskId = null) {
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

export function closeTaskModal() {
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

// Import missing functions
import { allTasks, formatDatetimeLocal, parseDatetimeLocal } from './utils.js';