# API Документация

## Базовый URL

```
http://localhost:8000/api/v1
```

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
  "recurrence_type": "daily|weekly|monthly|yearly",
  "recurrence_interval": 1,
  "next_due_date": "2024-01-01T12:00:00",
  "reminder_time": "2024-01-01T11:00:00"
}
```

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

