// Main application entry point
console.log('main.js loading...');

import { init, initializeAppIfNeeded, setupEventListeners, updateGroupSelect, updateUserFilterOptions, updateAssigneeSelect, loadData } from './utils.js';
import { connectWebSocket } from './websocket.js';
import { showUserPickScreen } from './user_management.js';
import { openTaskModal, closeTaskModal, handleTaskSubmit } from './task_management.js';
import { openGroupModal, closeGroupModal, handleGroupSubmit, editGroup, deleteGroup } from './utils.js';
import { completeTask, toggleTaskComplete, editTask, deleteTask } from './utils.js';
import { toggleAdminMode } from './utils.js';
import { toggleTaskFilter } from './filters.js';
import { viewTaskHistory, closeHistoryModal, deleteHistoryEntry } from './history.js';
import { setQuickDate } from './utils.js';
import { handleUserSubmit } from './user_management.js';
import { resetUserForm } from './utils.js';

export async function initializeAppIfNeededWrapper() {
    await initializeAppIfNeeded();
}

export async function loadDataWrapper() {
    await loadData();
}

// Make functions global for HTML onclick handlers
window.openTaskModal = openTaskModal;
window.closeTaskModal = closeTaskModal;
window.openGroupModal = openGroupModal;
window.closeGroupModal = closeGroupModal;
window.editGroup = editGroup;
window.deleteGroup = deleteGroup;
window.completeTask = completeTask;
window.toggleTaskComplete = toggleTaskComplete;
window.editTask = editTask;
window.deleteTask = deleteTask;
window.toggleAdminMode = toggleAdminMode;
window.toggleTaskFilter = toggleTaskFilter;
window.viewTaskHistory = viewTaskHistory;
window.closeHistoryModal = closeHistoryModal;
window.deleteHistoryEntry = deleteHistoryEntry;
window.setQuickDate = setQuickDate;

// Initialize app on load
document.addEventListener('DOMContentLoaded', init);