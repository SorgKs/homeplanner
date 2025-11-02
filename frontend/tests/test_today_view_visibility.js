/**
 * Tests for task visibility in 'today' view.
 * Run with: node frontend/tests/test_today_view_visibility.js
 */

const { shouldBeVisibleInTodayView } = require('../utils/todayViewFilter.js');

/**
 * Create a test task object.
 *
 * @param {string} taskType - Type of task ('one_time', 'recurring', 'interval')
 * @param {Date} dueDate - Task due date
 * @param {boolean} isCompleted - Whether task is completed
 * @param {Date|null} lastCompletedAt - When task was completed (if completed)
 * @param {boolean} isActive - Whether task is active
 * @returns {Object} Task object
 */
function createTask(taskType, dueDate, isCompleted = false, lastCompletedAt = null, isActive = true) {
    return {
        id: 1,
        title: `Test ${taskType} task`,
        task_type: taskType,
        due_date: dueDate instanceof Date ? dueDate : new Date(dueDate),
        is_completed: isCompleted,
        is_active: isActive,
        last_completed_at: lastCompletedAt ? (lastCompletedAt instanceof Date ? lastCompletedAt : new Date(lastCompletedAt)) : null,
    };
}

/**
 * Assert helper function.
 */
function assert(condition, message) {
    if (!condition) {
        throw new Error(message);
    }
}

/**
 * Test helper function.
 */
function test(name, fn) {
    try {
        fn();
        console.log(`✓ ${name}`);
        return true;
    } catch (error) {
        console.error(`✗ ${name}: ${error.message}`);
        return false;
    }
}

// Test 1: Uncompleted tasks visible for today and overdue
function testUncompletedTasksVisibleForTodayAndOverdue() {
    // Current date: 2.01.2025
    const currentDate = new Date(2025, 0, 2, 12, 0, 0);
    
    // Create tasks for each type with different dates
    const dates = [
        new Date(2025, 0, 1, 9, 0, 0),  // 1.01.2025
        new Date(2025, 0, 2, 9, 0, 0),  // 2.01.2025
        new Date(2025, 0, 3, 9, 0, 0),  // 3.01.2025
    ];
    
    const taskTypes = ['one_time', 'recurring', 'interval'];
    
    for (const taskType of taskTypes) {
        for (let i = 0; i < dates.length; i++) {
            const date = dates[i];
            const task = createTask(taskType, date, false);
            const isVisible = shouldBeVisibleInTodayView(task, currentDate);
            
            if (i <= 1) {  // Tasks for 1.01 and 2.01
                assert(isVisible, 
                    `${taskType} task for ${date.toISOString().split('T')[0]} should be visible on 2.01.2025`);
            } else {  // Task for 3.01
                assert(!isVisible, 
                    `${taskType} task for ${date.toISOString().split('T')[0]} should NOT be visible on 2.01.2025`);
            }
        }
    }
}

// Test 2: Completed tasks on previous day not visible
function testCompletedTasksOnPreviousDayNotVisible() {
    // Current date: 2.01.2025
    const currentDate = new Date(2025, 0, 2, 12, 0, 0);
    
    // Create tasks
    const dates = [
        new Date(2025, 0, 1, 9, 0, 0),  // 1.01.2025
        new Date(2025, 0, 2, 9, 0, 0),  // 2.01.2025
        new Date(2025, 0, 3, 9, 0, 0),  // 3.01.2025
    ];
    
    const taskTypes = ['one_time', 'recurring', 'interval'];
    
    for (const taskType of taskTypes) {
        // Task for 1.01, completed on 1.01
        let task_1_01;
        if (taskType === 'one_time') {
            task_1_01 = createTask(
                taskType,
                dates[0],
                true,
                null,
                false  // one_time tasks are inactive when completed
            );
        } else {
            task_1_01 = createTask(
                taskType,
                dates[0],
                true,
                new Date(2025, 0, 1, 21, 0, 0)
            );
        }
        
        // Task for 2.01, not completed
        const task_2_01 = createTask(taskType, dates[1], false);
        
        // Task for 3.01, not completed
        const task_3_01 = createTask(taskType, dates[2], false);
        
        // Task for 1.01 should NOT be visible (completed on previous day)
        assert(!shouldBeVisibleInTodayView(task_1_01, currentDate),
            `${taskType} task for 1.01 completed on 1.01 should NOT be visible on 2.01`);
        
        // Task for 2.01 should be visible (due today)
        assert(shouldBeVisibleInTodayView(task_2_01, currentDate),
            `${taskType} task for 2.01 should be visible on 2.01`);
        
        // Task for 3.01 should NOT be visible (due tomorrow)
        assert(!shouldBeVisibleInTodayView(task_3_01, currentDate),
            `${taskType} task for 3.01 should NOT be visible on 2.01`);
    }
}

// Test 3: Completed tasks on current day visible
function testCompletedTasksOnCurrentDayVisible() {
    // Current date: 2.01.2025
    const currentDate = new Date(2025, 0, 2, 12, 0, 0);
    
    // Create tasks
    const dates = [
        new Date(2025, 0, 1, 9, 0, 0),  // 1.01.2025
        new Date(2025, 0, 2, 9, 0, 0),  // 2.01.2025
        new Date(2025, 0, 3, 9, 0, 0),  // 3.01.2025
    ];
    
    const taskTypes = ['one_time', 'recurring', 'interval'];
    
    for (const taskType of taskTypes) {
        // Task for 1.01, completed on 2.01
        const task_1_01 = createTask(
            taskType,
            dates[0],
            true,
            new Date(2025, 0, 2, 10, 0, 0)  // Completed on 2.01
        );
        
        // Task for 2.01, completed on 2.01
        const task_2_01 = createTask(
            taskType,
            dates[1],
            true,
            new Date(2025, 0, 2, 11, 0, 0)  // Completed on 2.01
        );
        
        // Task for 3.01, not completed
        const task_3_01 = createTask(taskType, dates[2], false);
        
        // Task for 1.01 should be visible (completed today)
        assert(shouldBeVisibleInTodayView(task_1_01, currentDate),
            `${taskType} task for 1.01 completed on 2.01 should be visible on 2.01`);
        
        // Task for 2.01 should be visible (completed today)
        assert(shouldBeVisibleInTodayView(task_2_01, currentDate),
            `${taskType} task for 2.01 completed on 2.01 should be visible on 2.01`);
        
        // Task for 3.01 should NOT be visible (due tomorrow)
        assert(!shouldBeVisibleInTodayView(task_3_01, currentDate),
            `${taskType} task for 3.01 should NOT be visible on 2.01`);
    }
}

// Run all tests
console.log('Running tests for today view visibility...\n');

let passed = 0;
let failed = 0;

if (test('Uncompleted tasks visible for today and overdue', testUncompletedTasksVisibleForTodayAndOverdue)) {
    passed++;
} else {
    failed++;
}

if (test('Completed tasks on previous day not visible', testCompletedTasksOnPreviousDayNotVisible)) {
    passed++;
} else {
    failed++;
}

if (test('Completed tasks on current day visible', testCompletedTasksOnCurrentDayVisible)) {
    passed++;
} else {
    failed++;
}

console.log(`\nTests: ${passed + failed} total, ${passed} passed, ${failed} failed`);

if (failed > 0) {
    process.exit(1);
}

