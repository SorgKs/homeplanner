/**
 * Utility function for determining task visibility in 'today' view.
 * This function is extracted from app.js for easier testing.
 *
 * @param {Object} task - Task object
 * @param {Date|string|null} task.reminder_time - Task reminder datetime
 * @param {boolean} task.completed - Whether task is completed for current day
 * @param {string} task.task_type - Task type ('one_time', 'recurring', 'interval')
 * @param {boolean} task.enabled - Whether task is enabled
 * @param {Date} currentDate - Current date for comparison
 * @returns {boolean} True if task should be visible in 'today' view
 */
function shouldBeVisibleInTodayView(task, currentDate) {
    // Get today (start of day)
    const today = new Date(
        currentDate.getFullYear(),
        currentDate.getMonth(),
        currentDate.getDate()
    );
    
    const taskType = task.task_type || 'one_time';
    
    const reminderDate = task.reminder_time
        ? task.reminder_time instanceof Date
            ? task.reminder_time
            : new Date(task.reminder_time)
        : null;

    if (!reminderDate) {
        return false;
    }

    const taskDateLocal = new Date(
        reminderDate.getFullYear(),
        reminderDate.getMonth(),
        reminderDate.getDate()
    );
    const isDueToday = taskDateLocal.getTime() === today.getTime();
    const isDueTodayOrOverdue = taskDateLocal.getTime() <= today.getTime();

    if (taskType === 'one_time') {
        if (task.enabled === false) {
            return isDueToday;
        }
        return isDueTodayOrOverdue;
    }

    if (task.completed) {
        return true;
    }

    return task.enabled !== false && isDueTodayOrOverdue;
}

// Export for use in Node.js tests
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { shouldBeVisibleInTodayView };
}

