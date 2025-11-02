# API Документация

## Базовый URL

```
http://localhost:8000/api/v1
```

## Формат данных

### Работа с временем

**Важно**: Все даты и время хранятся и обрабатываются в **локальном времени**, без конвертации в UTC.

- Формат: ISO 8601 без указания timezone (`YYYY-MM-DDTHH:mm:ss` или `YYYY-MM-DDTHH:mm`)
- Примеры:
  - `2024-01-01T12:00:00`
  - `2024-01-01T12:00`

При отправке данных на сервер используйте локальное время. Сервер сохраняет время как есть, без преобразований.

### Типы задач

Задачи могут быть трех типов:
- `one_time` - разовая задача
- `recurring` - задача типа "расписание" (ежедневно, еженедельно, ежемесячно, ежегодно)
- `interval` - интервальная задача (повторяется через N дней после выполнения)

### Требования к полям

#### Задачи типа "расписание" (recurring)

Для задач типа `recurring` поле `reminder_time` **обязательно**. Оно определяет:
- Для `daily` - время выполнения (часы и минуты)
- Для `weekly` - день недели и время выполнения
- Для `monthly` - число месяца и время выполнения
- Для `yearly` - дату (день и месяц) и время выполнения

Если `reminder_time` не указан для задачи типа `recurring`, сервер вернет ошибку валидации.

## Endpoints

### События (Events)

#### Создать событие
```http
POST /events/
Content-Type: application/json

{
  "title": "Название события",
  "description": "Описание",
  "event_date": "2024-01-01T12:00:00",
  "reminder_time": "2024-01-01T11:00:00"
}
```

#### Получить все события
```http
GET /events/?completed=true|false|null
```

#### Получить событие по ID
```http
GET /events/{event_id}
```

#### Обновить событие
```http
PUT /events/{event_id}
Content-Type: application/json

{
  "title": "Новое название",
  "is_completed": true
}
```

#### Удалить событие
```http
DELETE /events/{event_id}
```

#### Отметить событие как выполненное
```http
POST /events/{event_id}/complete
```

### Задачи (Tasks)

#### Создать задачу
```http
POST /tasks/
Content-Type: application/json

{
  "title": "Название задачи",
  "description": "Описание",
  "task_type": "one_time|recurring|interval",
  "recurrence_type": "daily|weekly|monthly|yearly" (обязательно для recurring),
  "recurrence_interval": 1 (обязательно для recurring),
  "next_due_date": "2024-01-01T12:00:00",
  "reminder_time": "2024-01-01T11:00:00" (обязательно для recurring),
  "interval_days": 7 (обязательно для interval),
  "group_id": null
}
```

**Требования:**
- Для задач типа `recurring`: `reminder_time` обязателен
- Для задач типа `interval`: `interval_days` обязателен
- Для задач типа `recurring`: `recurrence_type` и `recurrence_interval` обязательны

**Типы повторения (recurrence_type):**
- `daily` - ежедневно
- `weekdays` - по будням (пн-пт)
- `weekends` - по выходным (сб-вс)
- `weekly` - еженедельно (конкретный день недели)
- `monthly` - ежемесячно (конкретное число месяца)
- `yearly` - ежегодно (конкретная дата)

#### Получить все задачи
```http
GET /tasks/?active_only=true&days_ahead=7
```

#### Получить задачу по ID
```http
GET /tasks/{task_id}
```

#### Обновить задачу
```http
PUT /tasks/{task_id}
Content-Type: application/json

{
  "title": "Новое название",
  "is_active": false
}
```

#### Удалить задачу
```http
DELETE /tasks/{task_id}
```

#### Отметить задачу как выполненную
```http
POST /tasks/{task_id}/complete
```

