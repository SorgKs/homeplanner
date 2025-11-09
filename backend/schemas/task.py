"""Pydantic схемы для работы с задачами."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field, field_validator

from backend.models.task import RecurrenceType, TaskType


class TaskRecurrencePayload(BaseModel):
    """Описание правил повторяемости задачи."""

    rrule: str = Field(..., min_length=3, description="RRULE-строка для описания повторов")
    end_at: datetime | None = Field(
        None,
        description="Дата завершения повторов (локальное время, без таймзоны)",
    )


class TaskNotificationPayload(BaseModel):
    """Конфигурация напоминания для задачи."""

    notification_type: str = Field(
        "reminder",
        min_length=1,
        max_length=32,
        description="Тип уведомления (например, reminder)",
    )
    channel: str = Field(
        "local",
        min_length=1,
        max_length=32,
        description="Канал доставки (push, local и т.д.)",
    )
    offset_minutes: int = Field(
        0,
        ge=0,
        description="Смещение в минутах относительно времени напоминания",
    )


class TaskBase(BaseModel):
    """Базовая модель задачи, общая для create/update."""

    title: str = Field(..., min_length=1, max_length=255, description="Название задачи")
    description: str | None = Field(None, description="Описание задачи")
    task_type: TaskType = Field(TaskType.ONE_TIME, description="Тип задачи")
    recurrence_type: RecurrenceType | None = Field(
        None,
        description="Тип повторяемости (для расписаний)",
    )
    recurrence_interval: int | None = Field(
        None,
        ge=1,
        description="Интервал повторяемости (каждые N периодов)",
    )
    interval_days: int | None = Field(
        None,
        ge=1,
        description="Интервал в днях для интервальных задач",
    )
    reminder_time: datetime = Field(
        ...,
        description="Время напоминания/исполнения в локальном часовом поясе сервера",
    )
    completed: bool = Field(
        False,
        description="Флаг выполнения задачи в текущие сутки",
    )
    group_id: int | None = Field(None, description="Идентификатор группы задач")
    active: bool = Field(True, description="Флаг активности задачи")


class TaskCreate(TaskBase):
    """Схема для создания задачи."""

    revision: int = Field(
        0,
        ge=0,
        description="Начальная ревизия задачи (для optimistic lock, всегда 0 при создании)",
    )
    recurrence: TaskRecurrencePayload | None = Field(
        None,
        description="Расширенные правила повторяемости",
    )
    notifications: list[TaskNotificationPayload] = Field(
        default_factory=list,
        description="Массив настроек напоминаний",
    )


class TaskUpdate(BaseModel):
    """Схема для обновления задачи с контролем ревизии."""

    revision: int = Field(
        ...,
        ge=0,
        description="Актуальная ревизия клиента (обязательна для обновления)",
    )
    title: str | None = Field(None, min_length=1, max_length=255)
    description: str | None = None
    task_type: TaskType | None = None
    recurrence_type: RecurrenceType | None = None
    recurrence_interval: int | None = Field(
        None,
        ge=1,
        description="Интервал повторяемости (для расписаний)",
    )
    interval_days: int | None = Field(
        None,
        ge=1,
        description="Интервал в днях (для интервальных задач)",
    )
    reminder_time: datetime | None = None
    completed: bool | None = None
    active: bool | None = None
    group_id: int | None = None
    recurrence: TaskRecurrencePayload | None = Field(
        None,
        description="Полное описание повторов (если нужно заменить)",
    )
    notifications: list[TaskNotificationPayload] | None = Field(
        None,
        description="Полный массив напоминаний (если нужно заменить)",
    )
    soft_lock_owner: str | None = Field(
        None,
        description="Обновление владельца soft-lock (опционально)",
    )
    soft_lock_expires_at: datetime | None = Field(
        None,
        description="Обновление времени протухания soft-lock",
    )

    @field_validator("title")
    @classmethod
    def validate_title(cls, value: str | None) -> str | None:
        """Не позволять задавать пустой title."""

        if value is not None and not value.strip():
            raise ValueError("Название задачи не может быть пустым")
        return value


class TaskNotificationResponse(TaskNotificationPayload):
    """Ответ с информацией о напоминании."""

    id: str = Field(..., description="Идентификатор настройки напоминания")
    created_at: datetime = Field(..., description="Дата создания записи")
    updated_at: datetime = Field(..., description="Дата последнего обновления записи")


class TaskRecurrenceResponse(TaskRecurrencePayload):
    """Ответ с информацией о повторяемости."""

    id: str = Field(..., description="Идентификатор записи повторяемости")
    created_at: datetime = Field(..., description="Дата создания записи")
    updated_at: datetime = Field(..., description="Дата последнего обновления записи")


class TaskResponse(TaskBase):
    """Полное описание задачи в ответе API."""

    id: int
    group_id: int | None
    interval_days: int | None
    last_shown_at: datetime | None
    created_at: datetime
    updated_at: datetime
    revision: int = Field(..., description="Текущая ревизия задачи")
    soft_lock_owner: str | None = Field(
        None,
        description="Клиент, удерживающий soft-lock",
    )
    soft_lock_expires_at: datetime | None = Field(
        None,
        description="Когда soft-lock протухает",
    )
    recurrence: TaskRecurrenceResponse | None = Field(
        None,
        description="Текущие правила повторяемости",
    )
    notifications: list[TaskNotificationResponse] = Field(
        default_factory=list,
        description="Настройки напоминаний",
    )
    readable_config: str | None = Field(
        None,
        description="Человекочитаемое описание расписания",
    )

    class Config:
        """Настройки модели."""

        from_attributes = True

    @classmethod
    def model_validate(
        cls,
        obj: Any,
        /,
        *,
        strict: bool | None = None,
        from_attributes: bool | None = None,
        context: dict[str, Any] | None = None,
    ) -> "TaskResponse":
        """Добавить readable_config при валидации модели."""

        from backend.services.task_service import TaskService

        instance = super().model_validate(
            obj,
            strict=strict,
            from_attributes=from_attributes,
            context=context,
        )

        task_type_value = instance.task_type.value if isinstance(instance.task_type, TaskType) else str(instance.task_type)

        recurrence_type_value = instance.recurrence_type
        if recurrence_type_value is not None and not isinstance(recurrence_type_value, RecurrenceType):
            try:
                recurrence_type_value = RecurrenceType(str(recurrence_type_value))
            except (ValueError, TypeError):
                recurrence_type_value = None

        task_dict = {
            "task_type": task_type_value,
            "recurrence_type": recurrence_type_value,
            "recurrence_interval": instance.recurrence_interval,
            "interval_days": instance.interval_days,
            "reminder_time": instance.reminder_time,
        }
        instance.readable_config = TaskService._format_task_settings(task_type_value, task_dict)
        return instance

