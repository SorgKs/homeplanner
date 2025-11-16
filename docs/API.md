# API Документация

## Базовый URL

Базовый URL API формируется из конфигурации сервера и версии API:

```
http://<host>:<port>/api/v<version>
```

Где:
- `<host>` — адрес сервера (по умолчанию `localhost`, настраивается в `common/config/settings.toml`)
- `<port>` — порт сервера (по умолчанию `8000`, настраивается в `common/config/settings.toml`)
- `<version>` — версия API (настраивается в `common/config/settings.toml` в секции `[api].version`)

**Пример**: При настройках по умолчанию базовый URL будет:
```
http://localhost:8000/api/v0.2
```

**Важно**: Версия API не захардкожена и должна соответствовать одной из поддерживаемых версий, указанных в `pyproject.toml` в секции `[tool.homeplanner.api].supported_versions`. Подробнее о версионировании API см. в [VERSIONING.md](VERSIONING.md).

## Формат данных

### Работа с временем

**Важно**: Все даты и время хранятся и обрабатываются в **локальном времени**, без конвертации в UTC.

- Формат: ISO 8601 без указания timezone (`YYYY-MM-DDTHH:mm:ss` или `YYYY-MM-DDTHH:mm`)
- Примеры:
  - `2024-01-01T12:00:00`
  - `2024-01-01T12:00`

При отправке данных на сервер используйте локальное время. Сервер сохраняет время как есть, без преобразований. Точность сохраняемого времени — до минут (секунды и микросекунды нормализуются к `00` для абсолютных операций, таких как `/time/set`).

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
  "reminder_time": "2024-01-01T12:00:00" (обязательно для всех типов),
  "interval_days": 7 (обязательно для interval),
  "group_id": null
}
```

**Требования:**
- Для задач: `reminder_time` обязателен
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
GET /tasks/
```

Возвращает полный список задач с исходными полями (`TaskResponse`). Дополнительные параметры не требуются.

#### Получить идентификаторы задач «Сегодня»
```http
GET /tasks/today/ids
```

Возвращает массив идентификаторов задач, которые должны отображаться в представлении «Сегодня».

**Пример ответа**
```json
[1, 42, 105]
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
  "active": false,
  "revision": 0
}
```

Оптимистичная блокировка: при обновлении рекомендуется передавать текущее значение `revision` из последнего GET. В случае конфликта версий сервер вернёт 409 (conflict_revision) с актуальным payload и `server_revision`.
#### Удалить задачу
```http
DELETE /tasks/{task_id}
```

#### Отметить задачу как выполненную
```http
POST /tasks/{task_id}/complete
```

