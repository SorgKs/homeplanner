/**
 * API client for HomePlanner backend.
 */

// API base URL configuration
const CONFIGURED_API_BASE_URL =
    (typeof window !== "undefined" && window.HP_API_BASE_URL) || null;

if (!CONFIGURED_API_BASE_URL) {
    throw new Error("HP_API_BASE_URL is not defined. Проверьте frontend/config.js");
}

let API_BASE_URL = CONFIGURED_API_BASE_URL;

if (typeof localStorage !== "undefined") {
    const storedApiUrl = localStorage.getItem("apiBaseUrl");
    if (storedApiUrl) {
        API_BASE_URL = storedApiUrl;
    }
}

// Экспортируем актуальное значение для других скриптов
if (typeof window !== "undefined") {
    window.API_BASE_URL = API_BASE_URL;
}

/**
 * Format datetime-local input value from ISO string.
 */
function formatDatetimeLocal(isoString) {
    if (!isoString) return '';
    const date = new Date(isoString);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
}

/**
 * Convert datetime-local input value to ISO string.
 * 
 * datetime-local values are in local time, and we store them as-is (no UTC conversion).
 * The datetime-local format is "YYYY-MM-DDTHH:mm", which we convert to ISO format
 * while preserving local time.
 */
function parseDatetimeLocal(datetimeLocal) {
    if (!datetimeLocal) return null;
    
    // datetime-local format is "YYYY-MM-DDTHH:mm" (local time, no timezone)
    // Parse the string and create ISO string in local time
    const [datePart, timePart] = datetimeLocal.split('T');
    const [year, month, day] = datePart.split('-').map(Number);
    const [hours, minutes] = timePart.split(':').map(Number);
    
    // Format as ISO string (YYYY-MM-DDTHH:mm:00) without timezone
    // This preserves local time without UTC conversion
    const monthStr = String(month).padStart(2, '0');
    const dayStr = String(day).padStart(2, '0');
    const hoursStr = String(hours).padStart(2, '0');
    const minutesStr = String(minutes).padStart(2, '0');
    
    return `${year}-${monthStr}-${dayStr}T${hoursStr}:${minutesStr}:00`;
}

/**
 * Events API
 */
const eventsAPI = {
    async getAll(completed = null) {
        const params = completed !== null ? `?completed=${completed}` : '';
        const response = await fetch(`${API_BASE_URL}/events/${params}`);
        if (!response.ok) throw new Error('Failed to fetch events');
        return response.json();
    },

    async get(id) {
        const response = await fetch(`${API_BASE_URL}/events/${id}`);
        if (!response.ok) throw new Error('Failed to fetch event');
        return response.json();
    },

    async create(event) {
        const response = await fetch(`${API_BASE_URL}/events/`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(event),
        });
        if (!response.ok) throw new Error('Failed to create event');
        return response.json();
    },

    async update(id, event) {
        const response = await fetch(`${API_BASE_URL}/events/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(event),
        });
        if (!response.ok) throw new Error('Failed to update event');
        return response.json();
    },

    async delete(id) {
        const response = await fetch(`${API_BASE_URL}/events/${id}`, {
            method: 'DELETE',
        });
        if (!response.ok) throw new Error('Failed to delete event');
    },

    async complete(id) {
        const response = await fetch(`${API_BASE_URL}/events/${id}/complete`, {
            method: 'POST',
        });
        if (!response.ok) throw new Error('Failed to complete event');
        return response.json();
    },
};

/**
 * Tasks API
 */
const tasksAPI = {
    async getAll(activeOnly = false, daysAhead = null) {
        const params = new URLSearchParams();
        if (activeOnly) params.append('active_only', 'true');
        if (daysAhead) params.append('days_ahead', daysAhead);
        const queryString = params.toString();
        const url = `${API_BASE_URL}/tasks/${queryString ? '?' + queryString : ''}`;
        const response = await fetch(url);
        if (!response.ok) throw new Error('Failed to fetch tasks');
        return response.json();
    },

    async getTodayIds() {
        const response = await fetch(`${API_BASE_URL}/tasks/today/ids`);
        if (!response.ok) throw new Error('Failed to fetch today task ids');
        return response.json();
    },

    async get(id) {
        const response = await fetch(`${API_BASE_URL}/tasks/${id}`);
        if (!response.ok) throw new Error('Failed to fetch task');
        return response.json();
    },

    async create(task) {
        const response = await fetch(`${API_BASE_URL}/tasks/`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(task),
        });
        if (!response.ok) throw new Error('Failed to create task');
        return response.json();
    },

    async update(id, task) {
        console.log('[HTTP->] PUT', `${API_BASE_URL}/tasks/${id}`, task);
        const response = await fetch(`${API_BASE_URL}/tasks/${id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(task),
        });
        if (!response.ok) {
            let errorMessage = "Failed to update task";
            try {
                const errorData = await response.json();
                console.error("API Error Response:", errorData);

                if (
                    errorData &&
                    errorData.detail &&
                    typeof errorData.detail === "object" &&
                    errorData.detail.code === "conflict_revision"
                ) {
                    const conflictError = new Error(
                        errorData.detail.message ||
                            "Конфликт версий. Обновите данные и повторите попытку."
                    );
                    conflictError.serverPayload = errorData.detail.server_payload;
                    conflictError.serverRevision = errorData.detail.server_revision;
                    conflictError.code = "conflict_revision";
                    throw conflictError;
                }

                if (Array.isArray(errorData.detail)) {
                    // Pydantic validation errors
                    errorMessage = errorData.detail
                        .map((e) => `${e.loc.join(".")}: ${e.msg}`)
                        .join(", ");
                } else if (errorData.detail) {
                    errorMessage =
                        typeof errorData.detail === "string"
                            ? errorData.detail
                            : JSON.stringify(errorData.detail);
                } else if (errorData.message) {
                    errorMessage = errorData.message;
                }
            } catch (e) {
                if (e instanceof Error && e.code === "conflict_revision") {
                    console.log(
                        "[HTTP<-] PUT",
                        `${API_BASE_URL}/tasks/${id}`,
                        "ERROR conflict_revision"
                    );
                    throw e;
                }
                console.error("Failed to parse error response:", e);
            }
            console.log(
                "[HTTP<-] PUT",
                `${API_BASE_URL}/tasks/${id}`,
                "ERROR",
                errorMessage
            );
            throw new Error(errorMessage);
        }
        const json = await response.json();
        console.log('[HTTP<-] PUT', `${API_BASE_URL}/tasks/${id}`, json);
        return json;
    },

    async uncomplete(id) {
        console.log('[HTTP->] POST', `${API_BASE_URL}/tasks/${id}/uncomplete`);
        const response = await fetch(`${API_BASE_URL}/tasks/${id}/uncomplete`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({}),
        });
        if (!response.ok) throw new Error('Failed to uncomplete task');
        const json = await response.json();
        console.log('[HTTP<-] POST', `${API_BASE_URL}/tasks/${id}/uncomplete`, json);
        return json;
    },

    async delete(id) {
        const response = await fetch(`${API_BASE_URL}/tasks/${id}`, {
            method: 'DELETE',
        });
        if (!response.ok) throw new Error('Failed to delete task');
    },

    async complete(id) {
        console.log('[HTTP->] POST', `${API_BASE_URL}/tasks/${id}/complete`);
        const response = await fetch(`${API_BASE_URL}/tasks/${id}/complete`, {
            method: 'POST',
        });
        if (!response.ok) throw new Error('Failed to complete task');
        const json = await response.json();
        console.log('[HTTP<-] POST', `${API_BASE_URL}/tasks/${id}/complete`, json);
        return json;
    },

    async getHistory(id) {
        const response = await fetch(`${API_BASE_URL}/tasks/${id}/history`);
        if (!response.ok) throw new Error('Failed to fetch task history');
        return response.json();
    },

    async getAllHistory() {
        const response = await fetch(`${API_BASE_URL}/history`);
        if (!response.ok) throw new Error('Failed to fetch all history');
        return response.json();
    },

    async markShown(id) {
        const response = await fetch(`${API_BASE_URL}/tasks/${id}/mark-shown`, {
            method: 'POST',
        });
        if (!response.ok) throw new Error('Failed to mark task as shown');
        return response.json();
    },

    async deleteHistoryEntry(historyId) {
        const response = await fetch(`${API_BASE_URL}/history/${historyId}`, {
            method: 'DELETE',
        });
        if (!response.ok) throw new Error('Failed to delete history entry');
        return response.json();
    },
};

/**
 * Groups API
 */
const groupsAPI = {
    async getAll() {
        const response = await fetch(`${API_BASE_URL}/groups/`);
        if (!response.ok) throw new Error('Failed to fetch groups');
        return response.json();
    },

    async get(id) {
        const response = await fetch(`${API_BASE_URL}/groups/${id}`);
        if (!response.ok) throw new Error('Failed to fetch group');
        return response.json();
    },

    async create(group) {
        const response = await fetch(`${API_BASE_URL}/groups/`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(group),
        });
        if (!response.ok) throw new Error('Failed to create group');
        return response.json();
    },

    async update(id, group) {
        const response = await fetch(`${API_BASE_URL}/groups/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(group),
        });
        if (!response.ok) throw new Error('Failed to update group');
        return response.json();
    },

    async delete(id) {
        const response = await fetch(`${API_BASE_URL}/groups/${id}`, {
            method: 'DELETE',
        });
        if (!response.ok) throw new Error('Failed to delete group');
    },
};

/**
 * Users API
 */
const usersAPI = {
    async getAll() {
        const response = await fetch(`${API_BASE_URL}/users/`);
        if (!response.ok) throw new Error('Failed to fetch users');
        return response.json();
    },

    async get(id) {
        const response = await fetch(`${API_BASE_URL}/users/${id}`);
        if (!response.ok) throw new Error('Failed to fetch user');
        return response.json();
    },

    async create(user) {
        const response = await fetch(`${API_BASE_URL}/users/`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(user),
        });
        if (!response.ok) throw new Error('Failed to create user');
        return response.json();
    },

    async update(id, user) {
        const response = await fetch(`${API_BASE_URL}/users/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(user),
        });
        if (!response.ok) throw new Error('Failed to update user');
        return response.json();
    },

    async delete(id) {
        const response = await fetch(`${API_BASE_URL}/users/${id}`, {
            method: 'DELETE',
        });
        if (!response.ok) throw new Error('Failed to delete user');
    },
};

/**
 * Time control API
 */
const timeAPI = {
    async getState() {
        const response = await fetch(`${API_BASE_URL}/time/`);
        if (!response.ok) throw new Error('Failed to read time state');
        return response.json();
    },

    async shift({ days = 0, hours = 0, minutes = 0 }) {
        const response = await fetch(`${API_BASE_URL}/time/shift`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ days, hours, minutes }),
        });
        if (!response.ok) {
            const err = await response.json().catch(() => ({}));
            throw new Error(err.detail || 'Failed to shift time');
        }
        return response.json();
    },

    async set(targetDatetime) {
        const response = await fetch(`${API_BASE_URL}/time/set`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ target_datetime: targetDatetime }),
        });
        if (!response.ok) {
            const err = await response.json().catch(() => ({}));
            throw new Error(err.detail || 'Failed to set time');
        }
        return response.json();
    },

    async reset() {
        const response = await fetch(`${API_BASE_URL}/time/reset`, {
            method: 'POST',
        });
        if (!response.ok) throw new Error('Failed to reset time');
        return response.json();
    },
};

