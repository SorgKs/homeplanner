/**
 * API client for HomePlanner backend.
 */

const API_BASE_URL = 'http://localhost:8000/api/v1';

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
 */
function parseDatetimeLocal(datetimeLocal) {
    if (!datetimeLocal) return null;
    return new Date(datetimeLocal).toISOString();
}

/**
 * Events API
 */
const eventsAPI = {
    async getAll(completed = null) {
        const params = completed !== null ? `?completed=${completed}` : '';
        const response = await fetch(`${API_BASE_URL}/events${params}`);
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
        const url = `${API_BASE_URL}/tasks${queryString ? '?' + queryString : ''}`;
        const response = await fetch(url);
        if (!response.ok) throw new Error('Failed to fetch tasks');
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
        const response = await fetch(`${API_BASE_URL}/tasks/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(task),
        });
        if (!response.ok) {
            let errorMessage = 'Failed to update task';
            try {
                const errorData = await response.json();
                console.error('API Error Response:', errorData);
                if (Array.isArray(errorData.detail)) {
                    // Pydantic validation errors
                    errorMessage = errorData.detail.map(e => `${e.loc.join('.')}: ${e.msg}`).join(', ');
                } else if (errorData.detail) {
                    errorMessage = errorData.detail;
                } else if (errorData.message) {
                    errorMessage = errorData.message;
                }
            } catch (e) {
                console.error('Failed to parse error response:', e);
            }
            throw new Error(errorMessage);
        }
        return response.json();
    },

    async delete(id) {
        const response = await fetch(`${API_BASE_URL}/tasks/${id}`, {
            method: 'DELETE',
        });
        if (!response.ok) throw new Error('Failed to delete task');
    },

    async complete(id) {
        const response = await fetch(`${API_BASE_URL}/tasks/${id}/complete`, {
            method: 'POST',
        });
        if (!response.ok) throw new Error('Failed to complete task');
        return response.json();
    },
};

/**
 * Groups API
 */
const groupsAPI = {
    async getAll() {
        const response = await fetch(`${API_BASE_URL}/groups`);
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

