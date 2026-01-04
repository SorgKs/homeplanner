"""Utilities for recurrence calculation logic."""

from datetime import datetime, timedelta

from calendar import monthrange

from backend.models.task import RecurrenceType


def find_nth_weekday_in_month(year: int, month: int, weekday: int, n: int) -> datetime:
    """Find the N-th occurrence of a weekday in a given month.
    
    Args:
        year: Year
        month: Month (1-12)
        weekday: Day of week (0=Monday, 6=Sunday)
        n: Which occurrence (1=first, 2=second, 3=third, 4=fourth, -1=last)
    
    Returns:
        datetime object for the N-th weekday in the month
    """
    # Get first day of month
    first_day = datetime(year, month, 1)
    # Find first occurrence of weekday in month
    first_weekday = first_day.weekday()
    days_to_first = (weekday - first_weekday) % 7
    if days_to_first < 0:
        days_to_first += 7
    
    if n == -1:
        # Find last occurrence: go to last day and work backwards
        last_day = monthrange(year, month)[1]
        last_date = datetime(year, month, last_day)
        last_weekday = last_date.weekday()
        days_from_last = (last_weekday - weekday) % 7
        if days_from_last < 0:
            days_from_last += 7
        result = last_date - timedelta(days=days_from_last)
        # Ensure result is still in the same month
        if result.month != month:
            result = result - timedelta(days=7)
    else:
        # Find N-th occurrence
        result = first_day + timedelta(days=days_to_first + (n - 1) * 7)
        # Check if date is still in the same month
        if result.month != month:
            # This means we're trying to get a 5th occurrence, which doesn't exist
            # Fall back to last occurrence
            last_day = monthrange(year, month)[1]
            last_date = datetime(year, month, last_day)
            last_weekday = last_date.weekday()
            days_from_last = (last_weekday - weekday) % 7
            if days_from_last < 0:
                days_from_last += 7
            result = last_date - timedelta(days=days_from_last)
            if result.month != month:
                result = result - timedelta(days=7)
    
    return result


def determine_weekday_occurrence(day_of_month: int, weekday: int, year: int, month: int) -> int:
    """Determine which occurrence (1-4 or -1 for last) a day represents.
    
    Args:
        day_of_month: Day of month (1-31)
        weekday: Day of week (0=Monday, 6=Sunday)
        year: Year
        month: Month (1-12)
    
    Returns:
        Occurrence number (1, 2, 3, 4, or -1 for last)
    """
    # Check if this is the last occurrence of this weekday in the month
    last_day = monthrange(year, month)[1]
    last_date = datetime(year, month, last_day)
    last_weekday = last_date.weekday()
    days_from_last = (last_weekday - weekday) % 7
    if days_from_last < 0:
        days_from_last += 7
    last_occurrence_date = last_date - timedelta(days=days_from_last)
    if last_occurrence_date.month != month:
        last_occurrence_date = last_occurrence_date - timedelta(days=7)
    
    # If this is the last occurrence
    target_date = datetime(year, month, day_of_month)
    if target_date.day == last_occurrence_date.day:
        return -1
    
    # Otherwise, calculate which occurrence (1-4)
    first_day = datetime(year, month, 1)
    first_weekday = first_day.weekday()
    days_to_first = (weekday - first_weekday) % 7
    if days_to_first < 0:
        days_to_first += 7
    
    first_occurrence = first_day + timedelta(days=days_to_first)
    if first_occurrence.month != month:
        # This shouldn't happen, but handle it
        return 1
    
    # Calculate which occurrence
    days_diff = (target_date - first_occurrence).days
    occurrence = (days_diff // 7) + 1
    
    # Cap at 4 (we already checked if it's last)
    return min(occurrence, 4)


def calculate_next_due_date(
    current_date: datetime,
    recurrence_type: RecurrenceType | None,
    interval: int,
    reminder_time: datetime | None = None,
) -> datetime:
    """Calculate next due date based on recurrence type and interval.
    
    For recurring tasks with reminder_time, respects the time of day and day of week.
    """
    from backend.services.time_manager import get_current_time
    
    # For WEEKLY recurring tasks, reminder_time is required
    if recurrence_type == RecurrenceType.WEEKLY and not reminder_time:
        # For WEEKLY tasks, reminder_time is required to know day of week and time
        raise ValueError("reminder_time is required for weekly recurring tasks (день недели и время обязательны для еженедельных задач)")
    
    if not reminder_time:
        # Old behavior without reminder_time (for other recurrence types)
        # Note: MONTHLY_WEEKDAY and YEARLY_WEEKDAY require reminder_time
        if recurrence_type == RecurrenceType.DAILY:
            return current_date + timedelta(days=interval)
        elif recurrence_type == RecurrenceType.MONTHLY:
            return current_date + timedelta(days=30 * interval)
        elif recurrence_type == RecurrenceType.YEARLY:
            return current_date + timedelta(days=365 * interval)
        elif recurrence_type == RecurrenceType.MONTHLY_WEEKDAY:
            # Fallback: treat like monthly
            return current_date + timedelta(days=30 * interval)
        elif recurrence_type == RecurrenceType.YEARLY_WEEKDAY:
            # Fallback: treat like yearly
            return current_date + timedelta(days=365 * interval)
        else:
            return current_date + timedelta(days=interval)
    
    # New behavior with reminder_time
    reminder_hour = reminder_time.hour
    reminder_minute = reminder_time.minute
    
    if recurrence_type == RecurrenceType.DAILY:
        # Daily: same time every day
        next_date = current_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
        # If time has passed today, schedule for tomorrow
        if next_date <= current_date:
            next_date += timedelta(days=interval)
        return next_date
    
    elif recurrence_type == RecurrenceType.WEEKDAYS:
        # Weekdays: Monday-Friday only, with interval (treat like daily)
        # Find next N weekdays (where N = interval)
        next_date = current_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
        weekday_count = 0
        days_to_add = 0
        
        while weekday_count < interval:
            candidate = current_date + timedelta(days=days_to_add)
            weekday = candidate.weekday()  # Monday=0, Sunday=6
            # Check if it's a weekday (0-4 = Monday-Friday)
            if 0 <= weekday <= 4:
                next_date = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
                if next_date > current_date:
                    weekday_count += 1
                    if weekday_count >= interval:
                        break
            days_to_add += 1
            if days_to_add > 50:  # Safety check (enough for several weeks)
                break
        return next_date
    
    elif recurrence_type == RecurrenceType.WEEKENDS:
        # Weekends: Saturday-Sunday only, with interval (treat like daily)
        # Find next N weekend days (where N = interval)
        next_date = current_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
        weekend_count = 0
        days_to_add = 0
        
        while weekend_count < interval:
            candidate = current_date + timedelta(days=days_to_add)
            weekday = candidate.weekday()  # Monday=0, Sunday=6
            # Check if it's a weekend (5=Saturday, 6=Sunday)
            if weekday in [5, 6]:
                next_date = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
                if next_date > current_date:
                    weekend_count += 1
                    if weekend_count >= interval:
                        break
            days_to_add += 1
            if days_to_add > 50:  # Safety check (enough for several weeks)
                break
        return next_date
    
    elif recurrence_type == RecurrenceType.WEEKLY:
        # Weekly: same day of week and time
        reminder_weekday = reminder_time.weekday()  # Monday=0, Sunday=6
        # Find next occurrence of this weekday
        days_to_next = (reminder_weekday - current_date.weekday()) % 7
        # Calculate days to add based on interval
        if days_to_next == 0:
            # Same weekday - check if time has passed
            candidate = current_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            if candidate <= current_date:
                # Time has passed, schedule for next week
                days_ahead = 7 * interval
            else:
                # Time hasn't passed yet today
                days_ahead = 0
        else:
            # Different weekday
            if interval == 1:
                days_ahead = days_to_next
            else:
                # interval > 1 means every N weeks
                days_ahead = days_to_next + (interval - 1) * 7
        
        next_date = current_date + timedelta(days=days_ahead)
        next_date = next_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
        return next_date
    
    elif recurrence_type == RecurrenceType.MONTHLY:
        # Monthly: same day of month and time
        reminder_day = reminder_time.day
        # Try to create date in current month
        try:
            next_date = current_date.replace(day=reminder_day, hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
        except ValueError:
            # Month doesn't have that day, use last day of current month
            last_day = monthrange(current_date.year, current_date.month)[1]
            next_date = current_date.replace(day=last_day, hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)

        # If this month's occurrence has passed, move to next month(s)
        if next_date <= current_date:
            # Calculate how many months to add
            months_to_add = interval
            # Move forward by that many months
            target_month = current_date.month + months_to_add
            target_year = current_date.year
            while target_month > 12:
                target_month -= 12
                target_year += 1

            # Create date in target month
            try:
                next_date = current_date.replace(year=target_year, month=target_month, day=reminder_day)
            except ValueError:
                # Month doesn't have that day, use last day
                last_day = monthrange(target_year, target_month)[1]
                next_date = current_date.replace(year=target_year, month=target_month, day=last_day)
            # Set the time
            next_date = next_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
        return next_date
    
    elif recurrence_type == RecurrenceType.MONTHLY_WEEKDAY:
        # Monthly by weekday: e.g., 2nd Tuesday of each month
        # reminder_time should contain a date with the target weekday
        # We need to determine which occurrence (1st, 2nd, 3rd, 4th, or last)
        reminder_weekday = reminder_time.weekday()
        reminder_day = reminder_time.day
        reminder_month = reminder_time.month
        reminder_year = reminder_time.year
        # Determine which occurrence this is (1-4 or -1 for last)
        n = determine_weekday_occurrence(reminder_day, reminder_weekday, reminder_year, reminder_month)
        
        # Start from current month
        target_month = current_date.month
        target_year = current_date.year
        
        # Find the N-th weekday in current month
        candidate = find_nth_weekday_in_month(target_year, target_month, reminder_weekday, n)
        candidate = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
        
        if candidate <= current_date:
            # Current month's occurrence has passed, move to next month(s)
            months_to_add = interval
            target_month += months_to_add
            while target_month > 12:
                target_month -= 12
                target_year += 1
            candidate = find_nth_weekday_in_month(target_year, target_month, reminder_weekday, n)
            candidate = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
        
        return candidate
    
    elif recurrence_type == RecurrenceType.YEARLY:
        # Yearly: same month, day, and time
        reminder_month = reminder_time.month
        reminder_day = reminder_time.day
        # Try to create date in current year
        try:
            next_date = current_date.replace(month=reminder_month, day=reminder_day, hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
        except ValueError:
            # Year doesn't have that month/day combination, use last day of month
            last_day = monthrange(current_date.year, reminder_month)[1]
            next_date = current_date.replace(month=reminder_month, day=last_day, hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)

        # If this year's occurrence has passed, move forward by interval years
        if next_date <= current_date:
            try:
                next_date = next_date.replace(year=current_date.year + interval)
            except ValueError:
                # Target year doesn't have that date, use last day of month
                target_year = current_date.year + interval
                last_day = monthrange(target_year, reminder_month)[1]
                next_date = next_date.replace(year=target_year, day=last_day)
        return next_date
    
    elif recurrence_type == RecurrenceType.YEARLY_WEEKDAY:
        # Yearly by weekday: e.g., 1st Monday of January each year
        reminder_month = reminder_time.month
        reminder_weekday = reminder_time.weekday()
        reminder_day = reminder_time.day
        reminder_year = reminder_time.year
        # Determine which occurrence this is (1-4 or -1 for last)
        n = determine_weekday_occurrence(reminder_day, reminder_weekday, reminder_year, reminder_month)
        
        # Start from current year
        target_year = current_date.year
        
        # Find the N-th weekday in target month of current year
        candidate = find_nth_weekday_in_month(target_year, reminder_month, reminder_weekday, n)
        candidate = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
        
        if candidate <= current_date:
            # Current year's occurrence has passed, move forward by interval years
            target_year += interval
            candidate = find_nth_weekday_in_month(target_year, reminder_month, reminder_weekday, n)
            candidate = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
        
        return candidate
    
    else:
        # Default to daily
        return current_date + timedelta(days=interval)