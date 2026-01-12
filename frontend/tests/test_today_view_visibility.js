/**
 * Tests for task visibility in 'today' view.
 * Run with: node frontend/tests/test_today_view_visibility.js
 */

const { shouldBeVisibleInTodayView } = require('../utils/todayViewFilter.js');

/**
 * Create a test task object.
 *
 * @param {string} taskType - Type of task ('one_time', 'recurring', 'interval')
 * @param {Date} reminderTime - Task reminder datetime
 * @param {boolean} completed - Whether task is completed for current day
 * @param {boolean} enabled - Whether task is enabled
 * @returns {Object} Task object
 */
function createTask(taskType, reminderTime, completed = false, enabled = true) {
    return {
        id: 1,
        title: `Test ${taskType} task`,
        task_type: taskType,
        reminder_time: reminderTime instanceof Date ? reminderTime : new Date(reminderTime),
        completed,
        enabled,
    };
}

function assert(condition, message) {
    if (!condition) {
        throw new Error(message);
    }
}

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

function testOneTimeTasksVisibility() {
    const currentDate = new Date(2025, 0, 2, 12, 0, 0);
    const overdue = new Date(2025, 0, 1, 9, 0, 0);
    const today = new Date(2025, 0, 2, 9, 0, 0);
    const tomorrow = new Date(2025, 0, 3, 9, 0, 0);

    const overdueTask = createTask('one_time', overdue);
    const todayTask = createTask('one_time', today);
    const tomorrowTask = createTask('one_time', tomorrow);
    const completedTask = createTask('one_time', today, true, true);

    assert(shouldBeVisibleInTodayView(overdueTask, currentDate), 'Overdue one_time task should be visible');
    assert(shouldBeVisibleInTodayView(todayTask, currentDate), 'Today one_time task should be visible');
    assert(!shouldBeVisibleInTodayView(tomorrowTask, currentDate), 'Tomorrow one_time task should not be visible');
    assert(shouldBeVisibleInTodayView(completedTask, currentDate), 'Completed one_time task for today should remain visible');
}

function testRecurringTasksVisibility() {
    const currentDate = new Date(2025, 0, 2, 12, 0, 0);
    const overdue = new Date(2025, 0, 1, 9, 0, 0);
    const today = new Date(2025, 0, 2, 9, 0, 0);
    const tomorrow = new Date(2025, 0, 3, 9, 0, 0);

    const overdueTask = createTask('recurring', overdue);
    const todayTask = createTask('recurring', today);
    const tomorrowTask = createTask('recurring', tomorrow);
    const disabledTask = createTask('recurring', today, false, false);

    assert(shouldBeVisibleInTodayView(overdueTask, currentDate), 'Overdue recurring task should be visible');
    assert(shouldBeVisibleInTodayView(todayTask, currentDate), 'Today recurring task should be visible');
    assert(!shouldBeVisibleInTodayView(tomorrowTask, currentDate), 'Tomorrow recurring task should not be visible if not completed');
    assert(!shouldBeVisibleInTodayView(disabledTask, currentDate), 'Disabled recurring task should not be visible');
}

function testCompletedRecurringTaskStaysVisible() {
    const currentDate = new Date(2025, 0, 2, 12, 0, 0);
    const nextIteration = new Date(2025, 0, 3, 9, 0, 0);

    const completedTask = createTask('interval', nextIteration, true, true);

    assert(shouldBeVisibleInTodayView(completedTask, currentDate), 'Completed recurring task should remain visible for confirmation');
}

console.log('Running tests for today view visibility...\n');

let passed = 0;
let failed = 0;

if (test('One-time tasks visibility rules', testOneTimeTasksVisibility)) {
    passed++;
} else {
    failed++;
}

if (test('Recurring tasks visibility rules', testRecurringTasksVisibility)) {
    passed++;
} else {
    failed++;
}

if (test('Completed recurring tasks stay visible', testCompletedRecurringTaskStaysVisible)) {
    passed++;
} else {
    failed++;
}

console.log(`\nTests: ${passed + failed} total, ${passed} passed, ${failed} failed`);

if (failed > 0) {
    process.exit(1);
}

