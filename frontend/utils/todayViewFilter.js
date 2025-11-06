/**
 * Utility function for determining task visibility in 'today' view.
 * This function is extracted from app.js for easier testing.
 *
 * @param {Object} task - Task object
 * @param {Date} task.due_date - Task due date (ISO string or Date)
 * @param {boolean} task.is_completed - Whether task is completed
 * @param {string|Date|null} task.last_completed_at - When task was completed (ISO string or Date or null)
 * @param {string} task.task_type - Task type ('one_time', 'recurring', 'interval')
 * @param {boolean} task.is_active - Whether task is active
 * @param {Date} currentDate - Current date for comparison
 * @returns {boolean} True if task should be visible in 'today' view
 */
function shouldBeVisibleInTodayView(task, currentDate) {
    // Видимость НЕ зависит от due_date (next_due_date).
    // Get today (start of day)
    const today = new Date(
        currentDate.getFullYear(),
        currentDate.getMonth(),
        currentDate.getDate()
    );
    
    const taskType = task.task_type || 'one_time';
    
    if (taskType === 'one_time') {
        // For one_time tasks: check due date for completed tasks
        const taskDate = task.due_date instanceof Date 
            ? task.due_date 
            : new Date(task.due_date);
        const taskDateLocal = new Date(
            taskDate.getFullYear(),
            taskDate.getMonth(),
            taskDate.getDate()
        );
        const isDueToday = taskDateLocal.getTime() === today.getTime();
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);
        const isDueTodayOrOverdue = taskDateLocal < tomorrow;
        
        if (task.is_active === false) {
            // Completed one_time task - only show if due today
            return isDueToday;
        } else {
            // Uncompleted one_time task - show if due today or overdue
            return isDueTodayOrOverdue;
        }
    } else {
        // For recurring and interval tasks: visibility does NOT depend on due_date
        let completedToday = false;
        if (task.is_completed && task.last_completed_at) {
            const completedDate = task.last_completed_at instanceof Date
                ? task.last_completed_at
                : new Date(task.last_completed_at);
            
            const completedDateLocal = new Date(
                completedDate.getFullYear(),
                completedDate.getMonth(),
                completedDate.getDate()
            );
            completedToday = completedDateLocal.getTime() === today.getTime();
        }
        
        // For recurring/interval tasks:
        // - Visible if completed today (regardless of due_date), OR
        // - Visible if active (regardless of due_date)
        if (task.is_completed) {
            // Completed task - visible if completed today (regardless of due_date)
            return completedToday;
        } else {
            // Uncompleted task - visible if active (regardless of due_date)
            // Все активные повторяющиеся/интервальные задачи видны в "Сегодня"
            return task.is_active !== false;
        }
    }
}

// Export for use in Node.js tests
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { shouldBeVisibleInTodayView };
}

