// Time control utilities

import { timeControlState } from './utils.js';

// Time control functions
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

export function updateTimePanelVisibility() {
    const panel = document.getElementById('time-controls');
    if (!panel) return;
    panel.style.display = adminMode ? 'block' : 'none';
}

// Helper functions for tasks
export function getReferenceDate() {
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

export function getTaskTimestamp(task) {
    const timeSource = task.reminder_time || task.due_date;
    if (!timeSource) {
        return Number.POSITIVE_INFINITY;
    }
    const timestamp = new Date(timeSource).getTime();
    return Number.isNaN(timestamp) ? Number.POSITIVE_INFINITY : timestamp;
}

export function sortTasksByReminderTime(tasks) {
    return [...tasks].sort((a, b) => getTaskTimestamp(a) - getTaskTimestamp(b));
}

export function getTaskTimeCategory(task, referenceDate, todayStart, yesterdayStart) {
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

// Import missing dependencies
import { adminMode, loadData, showToast } from './utils.js';
import { formatDatetimeLocal } from './utils.js';