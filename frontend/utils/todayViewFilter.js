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
    // Parse task due date
    const taskDate = task.due_date instanceof Date 
        ? task.due_date 
        : new Date(task.due_date);
    
    // Get today (start of day)
    const today = new Date(
        currentDate.getFullYear(),
        currentDate.getMonth(),
        currentDate.getDate()
    );
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);
    
    // Check if task is due today or overdue
    const taskDateLocal = new Date(
        taskDate.getFullYear(),
        taskDate.getMonth(),
        taskDate.getDate()
    );
    const isDueTodayOrOverdue = taskDateLocal < tomorrow;
    
    // Check if task was completed today
    // For one_time tasks, completion is indicated by is_active=False
    // For recurring and interval tasks, completion is indicated by last_completed_at
    let completedToday = false;
    const taskType = task.task_type || 'one_time';
    
    if (taskType === 'one_time') {
        // For one_time tasks, if they're inactive, they're completed
        // But we only show them if they were due today
        // If overdue and completed - don't show
        if (task.is_active === false) {
            // Completed one_time task - only show if due today
            return taskDateLocal.getTime() === today.getTime();
        } else {
            // Uncompleted one_time task - show if due today or overdue
            return isDueTodayOrOverdue;
        }
    } else {
        // For recurring and interval tasks
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
        // - Visible if due today or overdue AND not completed, OR
        // - Visible if completed TODAY (regardless of due date)
        if (task.is_completed) {
            // Completed task - only visible if completed today
            return completedToday;
        } else {
            // Uncompleted task - visible if due today or overdue
            return isDueTodayOrOverdue;
        }
    }
}

// Export for use in Node.js tests
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { shouldBeVisibleInTodayView };
}

