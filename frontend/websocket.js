// WebSocket functions

import { allTasks, todayTasksCache, allTasksCache, currentView, showToast, loadData, ws, wsReconnectTimer, setWs, setWsReconnectTimer, todayTaskIds } from './utils.js';
import { filterAndRenderTasks } from './filters.js';
import { groups } from './utils.js';

export function getWsUrl() {
    // Use server-provided config if available, fallback to old logic
    if (window.HP_CONFIG && window.HP_CONFIG.websocketUrl) {
        return window.HP_CONFIG.websocketUrl;
    }

    // Fallback to old logic for backward compatibility
    const host =
        (typeof window !== "undefined" && window.HP_BACKEND_HOST) || "localhost";
    const port =
        (typeof window !== "undefined" && window.HP_BACKEND_PORT) || 8000;
    const path =
        (typeof window !== "undefined" && window.HP_WS_PATH) ||
        "/api/v0.2/tasks/stream";
    return `ws://${host}:${port}${path}`;
}

export function applyTaskEventFromWs(action, taskJson, taskId) {
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
        // Обновляем кэши
        allTasksCache = [...allTasks];
        todayTasksCache = todayTasksCache.filter(t => t.id !== taskId);
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
    const enabledFlag = t.enabled ?? true;
    const completedFlag = Boolean(t.completed);
    const reminderTime = t.reminder_time ?? null;

    // Определяем статус выполнения: задача выполнена если:
    // 1. Для разовых задач: не включена (enabled = false)
    // 2. Для повторяющихся и интервальных: completed = true
    let isCompleted = false;
    if (t.task_type === 'one_time') {
        isCompleted = !enabledFlag;
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
        is_enabled: enabledFlag,  // enabled заменяет is_enabled
        assigned_user_ids: Array.isArray(t.assigned_user_ids) ? t.assigned_user_ids.map(Number) : [],
        assignees: Array.isArray(t.assignees) ? t.assignees : [],
        reminder_time: reminderTime,
    };
    // Обновляем кэши напрямую (это источник истины)
    const cacheIdx = allTasksCache.findIndex(x => x.id === mapped.id);
    if (cacheIdx >= 0) {
        allTasksCache[cacheIdx] = mapped;
    } else {
        allTasksCache.push(mapped);
    }

    // Обновляем кэш для "Сегодня" если задача входит в список todayTaskIds
    if (todayTaskIds.has(mapped.id)) {
        const todayIdx = todayTasksCache.findIndex(x => x.id === mapped.id);
        if (todayIdx >= 0) {
            todayTasksCache[todayIdx] = mapped;
        } else {
            todayTasksCache.push(mapped);
        }
    } else {
        // Удаляем из кэша "Сегодня" если задача больше не должна там быть
        todayTasksCache = todayTasksCache.filter(t => t.id !== mapped.id);
    }

    // Синхронизируем allTasks с кэшем в зависимости от текущего вида
    if (currentView === 'today') {
        allTasks = [...todayTasksCache];
    } else {
        allTasks = [...allTasksCache];
    }

    filterAndRenderTasks();
}

export function connectWebSocket() {
    try {
        const url = getWsUrl();
        if (ws) {
            try { ws.close(); } catch (_) {}
        }
        // Отменяем предыдущий таймер переподключения, если есть
        if (wsReconnectTimer) {
            clearTimeout(wsReconnectTimer);
            setWsReconnectTimer(null);
        }
        setWs(new WebSocket(url));
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
            // Сохраняем таймер, чтобы можно было его отменить
            setWsReconnectTimer(setTimeout(connectWebSocket, 2000));
        };
        ws.onerror = (e) => {
            console.error('[WS] error', e);
        };
    } catch (e) {
        console.error('[WS] failed to connect', e);
    }
}

export function disconnectWebSocket() {
    // Отменяем таймер переподключения
    if (wsReconnectTimer) {
        clearTimeout(wsReconnectTimer);
        setWsReconnectTimer(null);
    }
    // Закрываем соединение
    if (ws) {
        try {
            ws.close();
        } catch (e) {
            console.error('Error closing WebSocket:', e);
        }
        setWs(null);
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
export function findNthWeekdayInMonth(year, month, weekday, n) {
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