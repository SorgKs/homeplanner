// Date and time utilities

import { formatDateTime } from './utils.js';

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

// Date formatting functions (if needed separately)
export function parseDatetimeLocal(value) {
    if (!value) return null;
    // Convert local datetime string to ISO string
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return null;
    return date.toISOString();
}

export function formatDatetimeLocal(isoString) {
    if (!isoString) return '';
    try {
        const date = new Date(isoString);
        if (Number.isNaN(date.getTime())) return '';
        // Format as YYYY-MM-DDTHH:MM
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    } catch (e) {
        return '';
    }
}