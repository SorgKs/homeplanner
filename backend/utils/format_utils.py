"""Utilities for formatting task information."""

from backend.models.task import TaskType, RecurrenceType
from backend.services.time_manager import get_current_time
from backend.utils.recurrence_utils import determine_weekday_occurrence
from backend.utils.date_utils import format_datetime_short


def format_task_settings(task_type: str, task_obj: "Task | dict") -> str:
    """Format complete task settings as human-readable string.
    Can work with Task object or dict of values.
    """
    # Helper to get value from either Task or dict
    def get_val(key: str, default=None):
        if isinstance(task_obj, dict):
            return task_obj.get(key, default)
        else:
            return getattr(task_obj, key, default)
    
    if task_type == "one_time":
        reminder_time = get_val("reminder_time")
        if reminder_time:
            return format_datetime_short(reminder_time)
        else:
            return "разовая задача"
    
    elif task_type == "recurring":
        recurrence_type = get_val("recurrence_type")
        recurrence_interval = get_val("recurrence_interval") or 1
        reminder_time = get_val("reminder_time")
        
        if recurrence_type == RecurrenceType.DAILY:
            if reminder_time:
                time_str = reminder_time.strftime("%H:%M")
                if recurrence_interval == 1:
                    return f"в {time_str} каждый день"
                else:
                    return f"в {time_str} каждые {recurrence_interval} дней"
            else:
                if recurrence_interval == 1:
                    return "ежедневно"
                else:
                    return f"каждые {recurrence_interval} дней"
        
        elif recurrence_type == RecurrenceType.WEEKDAYS:
            if reminder_time:
                time_str = reminder_time.strftime("%H:%M")
                if recurrence_interval == 1:
                    return f"в {time_str} по будням"
                else:
                    return f"в {time_str} каждые {recurrence_interval} будних дня"
            else:
                if recurrence_interval == 1:
                    return "по будням"
                else:
                    return f"каждые {recurrence_interval} будних дня"
        
        elif recurrence_type == RecurrenceType.WEEKENDS:
            if reminder_time:
                time_str = reminder_time.strftime("%H:%M")
                if recurrence_interval == 1:
                    return f"в {time_str} по выходным"
                else:
                    return f"в {time_str} каждые {recurrence_interval} выходных дня"
            else:
                if recurrence_interval == 1:
                    return "по выходным"
                else:
                    return f"каждые {recurrence_interval} выходных дня"
        
        elif recurrence_type == RecurrenceType.WEEKLY:
            # For weekly tasks, always show day of week and time
            if reminder_time:
                time_str = reminder_time.strftime("%H:%M")
                # Get day of week from reminder_time (Monday=0, Sunday=6)
                weekday_num = reminder_time.weekday()
                weekdays_ru = [
                    "понедельник",
                    "вторник",
                    "среда",
                    "четверг",
                    "пятница",
                    "суббота",
                    "воскресенье"
                ]
                weekday_ru = weekdays_ru[weekday_num]
                if recurrence_interval == 1:
                    return f"каждый {weekday_ru} в {time_str}"
                else:
                    # Format: "раз в 2 недели в пятница в 08:11"
                    return f"раз в {recurrence_interval} недели в {weekday_ru} в {time_str}"
            else:
                if recurrence_interval == 1:
                    return "еженедельно"
                else:
                    return f"каждые {recurrence_interval} недели"
        
        elif recurrence_type == RecurrenceType.MONTHLY:
            if reminder_time:
                time_str = reminder_time.strftime("%H:%M")
                day_of_month = reminder_time.day
                if recurrence_interval == 1:
                    return f"{day_of_month} числа в {time_str} ежемесячно"
                else:
                    return f"{day_of_month} числа в {time_str} каждые {recurrence_interval} месяца"
            else:
                if recurrence_interval == 1:
                    return "ежемесячно"
                else:
                    return f"каждые {recurrence_interval} месяца"
        
        elif recurrence_type == RecurrenceType.MONTHLY_WEEKDAY:
            if reminder_time:
                time_str = reminder_time.strftime("%H:%M")
                weekday_num = reminder_time.weekday()
                day_of_month = reminder_time.day
                weekdays_ru = [
                    "понедельник", "вторник", "среду", "четверг", "пятницу", "субботу", "воскресенье"
                ]
                weekday_ru = weekdays_ru[weekday_num]
                # Determine which occurrence (1st, 2nd, 3rd, 4th, or last)
                # Use a more accurate method by checking the actual date
                if hasattr(reminder_time, "year"):
                    year = reminder_time.year
                else:
                    year = get_current_time().year
                month = reminder_time.month
                n = determine_weekday_occurrence(day_of_month, weekday_num, year, month)
                if n == -1:
                    nth_str = "последний"
                else:
                    nth_words = {1: "первый", 2: "второй", 3: "третий", 4: "четвертый"}
                    nth_str = nth_words.get(n, f"{n}-й")
                
                if recurrence_interval == 1:
                    return f"{nth_str} {weekday_ru} месяца в {time_str}"
                else:
                    return f"{nth_str} {weekday_ru} месяца в {time_str} каждые {recurrence_interval} месяца"
            else:
                if recurrence_interval == 1:
                    return "ежемесячно (по дню недели)"
                else:
                    return f"каждые {recurrence_interval} месяца (по дню недели)"
        
        elif recurrence_type == RecurrenceType.YEARLY:
            if reminder_time:
                time_str = reminder_time.strftime("%H:%M")
                day_of_month = reminder_time.day
                month_num = reminder_time.month
                months_ru = {
                    1: "января", 2: "февраля", 3: "марта", 4: "апреля",
                    5: "мая", 6: "июня", 7: "июля", 8: "августа",
                    9: "сентября", 10: "октября", 11: "ноября", 12: "декабря"
                }
                month_ru = months_ru.get(month_num, f"{month_num} месяца")
                if recurrence_interval == 1:
                    return f"{day_of_month} {month_ru} в {time_str} ежегодно"
                else:
                    return f"{day_of_month} {month_ru} в {time_str} каждые {recurrence_interval} года"
            else:
                if recurrence_interval == 1:
                    return "ежегодно"
                else:
                    return f"каждые {recurrence_interval} года"
        
        elif recurrence_type == RecurrenceType.YEARLY_WEEKDAY:
            if reminder_time:
                time_str = reminder_time.strftime("%H:%M")
                weekday_num = reminder_time.weekday()
                day_of_month = reminder_time.day
                month_num = reminder_time.month
                weekdays_ru = [
                    "понедельник", "вторник", "среду", "четверг", "пятницу", "субботу", "воскресенье"
                ]
                weekday_ru = weekdays_ru[weekday_num]
                months_ru = {
                    1: "января", 2: "февраля", 3: "марта", 4: "апреля",
                    5: "мая", 6: "июня", 7: "июля", 8: "августа",
                    9: "сентября", 10: "октября", 11: "ноября", 12: "декабря"
                }
                month_ru = months_ru.get(month_num, f"{month_num} месяца")
                # Determine which occurrence (1st, 2nd, 3rd, 4th, or last)
                week_of_month = (day_of_month - 1) // 7 + 1
                if week_of_month > 4:
                    nth_str = "последний"
                else:
                    nth_words = {1: "первый", 2: "второй", 3: "третий", 4: "четвертый"}
                    nth_str = nth_words.get(week_of_month, f"{week_of_month}-й")
                
                if recurrence_interval == 1:
                    return f"{nth_str} {weekday_ru} {month_ru} в {time_str} ежегодно"
                else:
                    return f"{nth_str} {weekday_ru} {month_ru} в {time_str} каждые {recurrence_interval} года"
            else:
                if recurrence_interval == 1:
                    return "ежегодно (по дню недели)"
                else:
                    return f"каждые {recurrence_interval} года (по дню недели)"
        
        else:
            return "расписание"
    
    elif task_type == "interval":
        interval_days = get_val("interval_days")
        reminder_time = get_val("reminder_time")
        time_str = ""
        if reminder_time:
            time_str = f" в {reminder_time.strftime('%H:%M')}"
        
        if interval_days:
            if interval_days == 7:
                return f"раз в неделю{time_str}"
            elif interval_days == 1:
                return f"раз в день{time_str}"
            elif interval_days < 7:
                return f"раз в {interval_days} дня{time_str}"
            elif interval_days % 7 == 0:
                weeks = interval_days // 7
                if weeks == 1:
                    return f"раз в неделю{time_str}"
                else:
                    return f"раз в {weeks} недели{time_str}"
            elif interval_days % 30 == 0:
                months = interval_days // 30
                if months == 1:
                    return f"раз в месяц{time_str}"
                else:
                    return f"раз в {months} месяца{time_str}"
            else:
                return f"раз в {interval_days} дней{time_str}"
        else:
            return f"интервальная задача{time_str}"
    
    return "задача"