# План реализации сетевых настроек в Android приложении

## Текущая ситуация

### Как сейчас работает подключение:
1. **BuildConfig.API_BASE_URL** - задается при сборке из:
   - `local.properties` (apiBaseUrl)
   - Переменная окружения `HP_API_BASE_URL`

2. **TasksApi** - принимает `baseUrl` в конструкторе, по умолчанию `BuildConfig.API_BASE_URL`
3. **GroupsApi** - использует напрямую `BuildConfig.API_BASE_URL`
4. **WebSocket URL** - вычисляется из `BuildConfig.API_BASE_URL` в функции `resolveWebSocketUrl()`

### Проблема:
- Нельзя изменить настройки без пересборки приложения
- Нет возможности настроить подключение на разных устройствах
- Сложно переключаться между разными серверами (dev/prod)

## Цель

Добавить возможность настройки сетевых параметров:
1. Через UI в разделе настроек
2. Через сканирование QR-кода из веб-версии
3. Сохранять настройки локально на устройстве

## Детальный план

### 1. Хранилище сетевых настроек

**Что создать:**
- Класс `NetworkSettings` для работы с настройками
- Data class `NetworkConfig` для хранения конфигурации
- Использовать **Preferences DataStore** (современный, асинхронный, безопасный)
- Добавить зависимость: `implementation("androidx.datastore:datastore-preferences:1.0.0")`

**Файлы:**
- `android/app/src/main/java/com/homeplanner/NetworkConfig.kt` - data class
- `android/app/src/main/java/com/homeplanner/NetworkSettings.kt` - класс для работы с DataStore

**Что хранить:**
```kotlin
data class NetworkConfig(
    val host: String,           // "192.168.1.2"
    val port: Int,              // 8000
    val apiVersion: String,     // "0.2" (должна быть из SupportedApiVersions.versions)
    val useHttps: Boolean = true  // использовать https или http, по умолчанию HTTPS
)
```

**Примечание:** `useHttps` по умолчанию `true` (HTTPS), так как это более безопасный протокол.

**Структура NetworkSettings:**
```kotlin
class NetworkSettings(private val context: Context) {
    private val dataStore: DataStore<Preferences> = 
        context.createDataStore(name = "network_settings")
    
    // Ключи для Preferences
    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val API_VERSION = stringPreferencesKey("api_version")
        val USE_HTTPS = booleanPreferencesKey("use_https")
    }
    
    // Flow для наблюдения за изменениями (для Compose)
    val configFlow: Flow<NetworkConfig?> = dataStore.data.map { prefs ->
        val host = prefs[Keys.HOST]
        val port = prefs[Keys.PORT]
        val apiVersion = prefs[Keys.API_VERSION]
        val useHttps = prefs[Keys.USE_HTTPS] ?: false
        
        if (host != null && port != null && apiVersion != null) {
            NetworkConfig(host, port, apiVersion, useHttps)
        } else {
            null  // Настройки не заданы
        }
    }
    
    // Сохранение
    suspend fun saveConfig(config: NetworkConfig) = dataStore.edit { prefs ->
        prefs[Keys.HOST] = config.host
        prefs[Keys.PORT] = config.port
        prefs[Keys.API_VERSION] = config.apiVersion
        prefs[Keys.USE_HTTPS] = config.useHttps
    }
    
    // Очистить настройки (сброс)
    suspend fun clearConfig() = dataStore.edit { prefs ->
        prefs.remove(Keys.HOST)
        prefs.remove(Keys.PORT)
        prefs.remove(Keys.API_VERSION)
        prefs.remove(Keys.USE_HTTPS)
    }
}

// Список поддерживаемых версий API
object SupportedApiVersions {
    val versions = listOf("0.2")  // Можно расширить в будущем
    val defaultVersion = versions.firstOrNull() ?: "0.2"
}
```

**Методы NetworkSettings:**
- `configFlow: Flow<NetworkConfig?>` - Flow для наблюдения за изменениями (async)
  - Возвращает `null` если настройки не заданы
- `saveConfig(config: NetworkConfig)` - сохранить настройки (async)
- `clearConfig()` - очистить настройки (сброс)

**Парсинг из URL (для QR-кода и ручного ввода):**
- Метод `NetworkConfig.parseFromUrl(url: String): NetworkConfig?` - парсит URL типа "http://192.168.1.2:8000/api/v0.2"
- Используется только для парсинга введенного пользователем URL или из QR-кода
- НЕ используется для автоматического fallback

**Значения по умолчанию:**
- ❌ **НЕТ значений по умолчанию**
- Если настройки не заданы в DataStore → `getConfig()` возвращает `null`
- При `null` - сетевые функции не работают (не создавать API клиенты, не подключать WebSocket)
- Пользователь должен настроить подключение вручную или через QR-код

**Использование в Compose:**
- Использовать `collectAsState()` для наблюдения за изменениями
- Если `networkConfig == null` → показывать сообщение "Настройте подключение к серверу"
- При изменении настроек автоматически обновлять UI и переподключать API клиенты

---

### 2. Обновление API клиентов

**Зачем обновлять:**
- Сейчас `TasksApi` и `GroupsApi` используют статический `BuildConfig.API_BASE_URL`
- Нужно, чтобы они читали URL из `NetworkSettings` во время выполнения

**Что изменить:**

#### 2.1 TasksApi
**Текущий код:**
```kotlin
class TasksApi(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = BuildConfig.API_BASE_URL,
)
```

**Изменения:**
- Конструктор уже принимает `baseUrl` - это хорошо, ничего менять не нужно
- В местах создания `TasksApi()` передавать URL из `NetworkSettings`

**Места использования (найти все):**
- В `loadTasks()` функции: `val api = TasksApi()` → `val api = TasksApi(baseUrl = networkSettings.getCurrentApiUrl())`
- В `completeTask()`, `uncompleteTask()`, `deleteTask()`, `updateTask()` - аналогично

#### 2.2 GroupsApi
**Текущий код:**
```kotlin
class GroupsApi(private val httpClient: OkHttpClient = OkHttpClient()) {
    private val baseUrl: String = BuildConfig.API_BASE_URL
    ...
}
```

**Изменения:**
```kotlin
class GroupsApi(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = BuildConfig.API_BASE_URL,  // Добавить параметр
) {
    // Убрать жестко заданный baseUrl
    ...
}
```

**Места использования:**
- В `loadTasks()`: `val groupsApi = GroupsApi()` → `val groupsApi = GroupsApi(baseUrl = networkSettings.getCurrentApiUrl())`

#### 2.3 WebSocket URL
**Текущий код:**
```kotlin
private fun resolveWebSocketUrl(): String {
    val rawBase = BuildConfig.API_BASE_URL.trimEnd('/')
    ...
}
```

**Изменения:**
- Функция должна принимать `NetworkConfig` или `baseUrl` как параметр
- Или получить из `NetworkSettings.getCurrentConfig()`
- Обновить `resolveWebSocketUrl()` чтобы использовать `NetworkConfig.toWebSocketUrl()`

**Важно:** 
- WebSocket URL используется в `LaunchedEffect(Unit)` в `TasksScreen`
- Нужно следить за изменениями настроек и переподключать WebSocket при изменении
- **Если `networkConfig == null` → НЕ подключать WebSocket, НЕ загружать задачи**
- Показывать сообщение о необходимости настройки подключения

#### 2.4 Интеграция в TasksScreen

**Проблема:** Сейчас API клиенты создаются в разных местах внутри `TasksScreen`

**Решение:**
1. Создать `NetworkSettings` instance в начале `TasksScreen`:
   ```kotlin
   val context = LocalContext.current
   val networkSettings = remember { NetworkSettings(context) }
   ```

2. Получить текущий config через Flow:
   ```kotlin
   val networkConfig by networkSettings.configFlow.collectAsState(initial = null)
   val apiBaseUrl = remember(networkConfig) {
       networkConfig?.toApiBaseUrl()
   }
   ```

3. **Проверка наличия настроек:**
   - Если `networkConfig == null` → не создавать API клиенты
   - Показывать сообщение "Настройте подключение к серверу"
   - Блокировать сетевые операции

4. Использовать `apiBaseUrl` везде где создаются API клиенты (только если не null)

5. При изменении `networkConfig` - перезагружать данные

**Места создания API клиентов:**
- `loadTasks()` - TasksApi, GroupsApi
- В checkbox handlers - TasksApi (completeTask, uncompleteTask)
- В delete handler - TasksApi (deleteTask)
- В edit dialog save - TasksApi (createTask, updateTask)

**Тесты:**
- Обновить `TasksApiInstrumentedTest` чтобы принимать baseUrl

---

### 3. UI для сетевых настроек

**Где добавить:**
- В разделе "Настройки" (ViewTab.SETTINGS) в `MainActivity.kt`
- Сейчас там только показывается версия приложения

**Что показать:**

#### 3.1 Основной экран настроек

**Текущая секция (оставить):**
- "Версия: ${BuildConfig.VERSION_NAME}"

**Новая секция "Сетевые настройки":**
```
┌─────────────────────────────────┐
│ Сетевые настройки                │
├─────────────────────────────────┤
│ Хост: 192.168.1.2                │
│ Порт: 8000                       │
│ API Версия: 0.2                  │
│ Протокол: HTTP                   │
├─────────────────────────────────┤
│ [Изменить настройки]             │
│ [Сканировать QR-код]             │
│ [Сбросить к умолчанию]           │
│ [Проверить подключение]          │
└─────────────────────────────────┘
```

**Состояния:**
- Если настройки не заданы (`networkConfig == null`):
  - Показать "⚠️ Подключение не настроено"
  - Показать кнопки: "Настроить вручную" и "Сканировать QR-код"
  - Скрыть кнопки "Проверить подключение" и "Очистить настройки"
- Если настройки заданы:
  - Показывать текущий полный URL: "API URL: http://192.168.1.2:8000/api/v0.2"
  - Показывать все кнопки

#### 3.2 Диалог редактирования настроек

**Компоненты:**
- `AlertDialog` с полями ввода

**Поля:**
- Host (OutlinedTextField) - IP адрес или домен
- Port (OutlinedTextField) - число от 1 до 65535, **по умолчанию: 8000**
- API Version (ExposedDropdownMenuBox) - **выпадающий список** из поддерживаемых версий
- HTTP/HTTPS (Switch) - переключатель протокола, **по умолчанию: HTTPS (true)**

**Значения по умолчанию в диалоге:**
- Host: пустое поле (пользователь должен ввести)
- Port: **8000** (предзаполнено)
- API Version: первая версия из списка поддерживаемых (например, "0.2")
- Use HTTPS: **true** (HTTPS выбран по умолчанию)

**Список поддерживаемых версий API:**
- Создать константу в коде:
  ```kotlin
  object SupportedApiVersions {
      val versions = listOf("0.2")  // Можно расширить в будущем
  }
  ```
- Или читать из BuildConfig (если добавить)
- Использовать `ExposedDropdownMenuBox` для выбора версии

**Валидация:**
- Host: не пустой (localhost допустим для эмуляции)
- Port: число в диапазоне 1-65535
- API Version: должна быть из списка поддерживаемых версий

**Примечание:** localhost не блокируется, так как нужен для эмуляции Android на компьютере

**Кнопки:**
- "Сохранить" - сохранить в NetworkSettings, применить настройки
- "Отмена" - закрыть диалог без изменений

**Пример UI диалога:**
```
┌─────────────────────────────────┐
│ Настройка подключения            │
├─────────────────────────────────┤
│ Хост: [________________]          │
│ Порт: [8000]                     │
│ API Версия: [0.2 ▼]              │
│                                   │
│ ☑ Использовать HTTPS             │
│                                   │
│ [Сохранить]  [Отмена]             │
└─────────────────────────────────┘
```

**После сохранения:**
- Переподключить WebSocket с новым URL
- Перезагрузить задачи с нового сервера
- Показать Toast/уведомление об успехе

#### 3.3 Проверка подключения

**Действия:**
1. Создать простой HTTP запрос к `/api/v0.2/tasks/` (GET)
2. Если успешно (200) - показать "Подключение успешно"
3. Если ошибка - показать "Ошибка подключения: {message}"

**UI:**
- Кнопка "Проверить подключение"
- Показывать индикатор загрузки во время проверки
- Показать результат (успех/ошибка) в диалоге или Toast

#### 3.4 Очистить настройки

**Действия:**
- Вызвать `networkSettings.clearConfig()`
- Очистить настройки в DataStore
- После очистки → `networkConfig` станет `null`
- Сетевые функции перестанут работать
- Показать сообщение "Настройки очищены. Настройте подключение заново."

#### 3.5 Состояния и обработка ошибок

**Обработка ошибок:**
- Некорректный формат данных - показать ошибку в диалоге
- Ошибка сохранения - показать Toast с ошибкой
- Ошибка подключения - показать в диалоге проверки

**Обновление UI:**
- Использовать `collectAsState()` для автоматического обновления при изменении настроек
- При `networkConfig == null` → показывать предупреждение и блокировать сетевые операции
- В списке задач показывать сообщение "Настройте подключение к серверу в разделе Настройки"

---

### 4. Сканер QR-кода

**Что нужно:**
- Добавить зависимость для сканирования QR-кодов
- Добавить разрешение на камеру в AndroidManifest.xml
- Запросить разрешение на камеру при первом использовании
- Реализовать Activity/Composable для сканирования

#### 4.1 Выбор библиотеки

**Варианты:**
1. **ML Kit Barcode Scanning** (рекомендуется Google)
   - `implementation("com.google.mlkit:barcode-scanning:17.2.0")`
   - Современная, поддерживается Google
   - Требует камеру, но можно использовать без CameraX

2. **ZXing** (старая, но проверенная)
   - `implementation("com.journeyapps:zxing-android-embedded:4.3.0")`
   - Проще в использовании
   - Уже включает UI для сканирования

**Рекомендация:** ML Kit Barcode Scanning + CameraX для современного подхода

#### 4.2 Зависимости

```kotlin
// ML Kit для сканирования
implementation("com.google.mlkit:barcode-scanning:17.2.0")

// CameraX для работы с камерой
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")

// Или проще: ZXing
implementation("com.journeyapps:zxing-android-embedded:4.3.0")
```

#### 4.3 Разрешения

**AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.CAMERA" />
```

**Запрос разрешения:**
- Использовать `rememberLauncherForActivityResult` с `ActivityResultContracts.RequestPermission()`
- При отказе - показать объяснение, почему нужна камера

#### 4.4 Формат QR-кода

**JSON структура (упрощенный формат):**
```json
{
  "host": "192.168.1.2",
  "port": 8000,
  "apiVersion": "0.2",
  "useHttps": true
}
```

**Примечание:** 
- Упрощенный формат без поля "type"
- `useHttps` по умолчанию `true` (HTTPS), если не указано иное

#### 4.5 Реализация сканера

**Подход: Диалог со сканером внутри приложения**
- Открывать `Dialog` (или `AlertDialog`) с камерой внутри
- Камера превью отображается прямо в диалоге
- После сканирования - закрывать диалог и обрабатывать результат

**Структура:**
- Composable `QrCodeScannerDialog` - диалог со сканером
- Внутри диалога - превью камеры и обработка сканирования
- При успешном сканировании - автоматически закрывается

**Логика сканирования:**
1. Запросить разрешение на камеру (если нет)
2. Открыть камеру в превью
3. При обнаружении QR-кода - распарсить содержимое
4. Валидировать JSON структуру
5. Проверить наличие обязательных полей (host, port, apiVersion)
6. Создать `NetworkConfig` из JSON
7. Сохранить в `NetworkSettings`
8. Закрыть сканер
9. Показать Toast "Настройки загружены успешно"
10. Применить настройки (переподключить WebSocket, загрузить данные)
11. **После сохранения сетевые функции начнут работать** (если до этого были не настроены)

**Обработка ошибок:**
- Неверный формат JSON - "Неверный формат QR-кода"
- Отсутствуют обязательные поля - "В QR-коде отсутствуют необходимые данные"
- Неподдерживаемая версия API - "Версия API '{version}' не поддерживается. Поддерживаемые версии: {список}"
- Ошибка сохранения - показать ошибку

#### 4.6 Парсинг и валидация

**Функция парсинга (упрощенный формат):**
```kotlin
fun parseNetworkConfigFromJson(jsonString: String): NetworkConfig? {
    try {
        val json = JSONObject(jsonString)
        
        // Упрощенный формат - только обязательные поля
        val host = json.getString("host")
        val port = json.getInt("port")
        val apiVersion = json.getString("apiVersion")
        val useHttps = json.optBoolean("useHttps", true)  // По умолчанию HTTPS
        
        // Валидация
        if (host.isBlank() || port !in 1..65535 || apiVersion.isBlank()) {
            return null
        }
        
        // Проверка что версия API поддерживается
        if (apiVersion !in SupportedApiVersions.versions) {
            return null
        }
        
        // localhost допустим (для эмуляции)
        // Не проверяем формат host строго
        
        return NetworkConfig(host, port, apiVersion, useHttps)
    } catch (e: Exception) {
        return null
    }
}
```

---

### 5. Генерация QR-кода в веб-версии

**Где добавить:**
- В разделе "Настройки" веб-версии (уже есть секция "Установить Android приложение")
- Файл: `frontend/index.html` - секция `#settings-view`
- Файл: `frontend/app.js` - логика генерации

#### 5.1 UI в веб-версии

**Текущая секция:**
```
┌─────────────────────────────────┐
│ Установить Android приложение    │
│ [Показать QR для APK] [Скачать]  │
│ Хост: [input] [Обновить QR]      │
│ API Base URL: [input] [Сохранить]│
└─────────────────────────────────┘
```

**Новая секция (добавить после):**
```
┌─────────────────────────────────┐
│ Настройки подключения для Android│
│                                   │
│ Текущие настройки:                │
│ Хост: localhost                   │
│ Порт: 8000                        │
│ API Версия: 0.2                   │
│ Протокол: HTTP                    │
│                                   │
│ [Показать QR-код с настройками]  │
│ [QR-код появляется здесь]        │
│                                   │
│ ℹ️ Отсканируйте QR-код в Android │
│ приложении для автоматической    │
│ настройки подключения             │
└─────────────────────────────────┘
```

#### 5.2 Генерация JSON конфигурации

**Логика:**
1. Получить текущие настройки из JavaScript:
   - `window.HP_BACKEND_HOST` или из `window.HP_API_BASE_URL`
   - `window.HP_BACKEND_PORT` или из URL
   - Версия API из `window.HP_API_BASE_URL` (парсинг `/api/v0.2`)

2. Создать JSON объект (упрощенный формат):
```javascript
const networkConfig = {
    host: window.HP_BACKEND_HOST || "localhost",
    port: window.HP_BACKEND_PORT || 8000,
    apiVersion: extractApiVersion(window.HP_API_BASE_URL) || "0.2",
    useHttps: window.location.protocol === "https:" || true  // По умолчанию HTTPS
};
```

3. Сериализовать в JSON строку

**Парсинг версии API:**
```javascript
function extractApiVersion(apiBaseUrl) {
    const match = apiBaseUrl.match(/\/api\/v([\d.]+)/);
    return match ? match[1] : "0.2";
}
```

#### 5.3 Генерация QR-кода

**Использовать существующую библиотеку:**
- Уже подключена: `qrcode.js` (через CDN)
- Функция `renderQr()` уже есть в `index.html`

**Реализация:**
```javascript
document.getElementById('show-network-config-qr-btn').addEventListener('click', function() {
    const config = generateNetworkConfig();
    const jsonString = JSON.stringify(config);
    const qrBox = document.getElementById('network-config-qr');
    
    qrBox.style.display = 'block';
    qrBox.innerHTML = '';
    renderQr(jsonString, qrBox);  // Использовать существующую функцию
});
```

**Важно:**
- QR-код должен содержать JSON строку, а не URL
- Android приложение должно распознать этот JSON и распарсить

#### 5.4 Отображение текущих настроек

**Показывать:**
- Хост, порт, версия API
- Информационное сообщение: "Убедитесь, что хост доступен с вашего Android устройства"
- Подсказка: "Для LAN подключения используйте IP адрес устройства в сети"

#### 5.5 Обработка различных сценариев

**Если localhost:**
- localhost допустим (для эмуляции на компьютере)
- Можно генерировать QR-код с localhost
- Информационное сообщение: "localhost работает только в эмуляторе"

**Если используется IP:**
- QR-код готов к использованию на реальном устройстве

---

### 6. Backend endpoint (опционально)

**Зачем:**
- Можно добавить endpoint для получения настроек подключения
- Но это не обязательно, так как веб-версия уже знает эти настройки

**Если делать:**
- `GET /api/v0.2/config/network` - возвращает JSON с настройками
- Может быть полезно для будущих расширений

---

## Архитектурные решения

### Отсутствие настроек = отсутствие работы

**Подход:**
- ❌ **НЕТ fallback на BuildConfig**
- Если `NetworkSettings.configFlow` возвращает `null` → сетевые функции не работают
- Пользователь должен явно настроить подключение (вручную или через QR-код)
- При первой установке приложения нужно настроить подключение перед использованием

**Поведение при отсутствии настроек:**
- В списке задач показывать: "⚠️ Подключение не настроено. Перейдите в Настройки для настройки подключения к серверу."
- Кнопка "Добавить задачу" - неактивна или показывает сообщение
- WebSocket не подключается
- API запросы не выполняются
- В разделе Настройки показывать предупреждение и кнопки для настройки

### Когда применять новые настройки

**Варианты:**
1. **Сразу после сохранения** - переподключить WebSocket, перезагрузить данные
2. **Только при следующем запуске** - проще, но менее удобно

**Рекомендация:** Сразу после сохранения, с переподключением WebSocket и перезагрузкой задач.

---

## Порядок реализации

### Фаза 1: Хранилище настроек
**Файлы:**
- `android/app/build.gradle.kts` - добавить зависимость DataStore
- `android/app/src/main/java/com/homeplanner/NetworkConfig.kt` - data class
- `android/app/src/main/java/com/homeplanner/NetworkSettings.kt` - класс для работы с DataStore

**Задачи:**
1. Добавить зависимость `androidx.datastore:datastore-preferences:1.0.0`
2. Создать `SupportedApiVersions` object с списком поддерживаемых версий
3. Создать `NetworkConfig` data class с методами:
   - `toApiBaseUrl(): String`
   - `toWebSocketUrl(): String`
   - `parseFromUrl(url: String): NetworkConfig?` (для парсинга введенного URL)
   - По умолчанию `useHttps = true` (HTTPS)
4. Создать `NetworkSettings` класс с:
   - DataStore instance
   - Preference keys (HOST, PORT, API_VERSION, USE_HTTPS)
   - `configFlow: Flow<NetworkConfig?>` - возвращает `null` если настройки не заданы
   - `saveConfig(config: NetworkConfig)`
   - `clearConfig()` - очистить настройки
5. Протестировать сохранение/загрузку/парсинг
6. **Убедиться что при отсутствии настроек возвращается `null`**

### Фаза 2: Обновление API клиентов
**Файлы:**
- `android/app/src/main/java/com/homeplanner/api/TasksApi.kt` - уже готов
- `android/app/src/main/java/com/homeplanner/api/TasksApi.kt` - обновить GroupsApi
- `android/app/src/main/java/com/homeplanner/MainActivity.kt` - обновить все использования

**Задачи:**
1. Изменить `GroupsApi` конструктор - добавить `baseUrl` параметр
2. В `TasksScreen`:
   - Создать `NetworkSettings` instance
   - Получить `networkConfig` через `collectAsState()`
   - **Проверить: если `networkConfig == null` → показать сообщение "Настройте подключение"**
   - Вычислить `apiBaseUrl` из конфига (только если не null)
   - Передать `apiBaseUrl` во все места создания `TasksApi()` и `GroupsApi()` (только если не null)
3. Обновить `resolveWebSocketUrl()` - использовать `NetworkConfig.toWebSocketUrl()` (только если config не null)
4. Обновить `LaunchedEffect` для WebSocket:
   - **Если `networkConfig == null` → НЕ подключать WebSocket**
   - Следить за изменениями `networkConfig` и переподключать при изменении
5. Обновить `loadTasks()`:
   - **Если `networkConfig == null` → не загружать задачи, показать сообщение**
6. Обновить тесты (если есть)
7. Протестировать: без настроек → ничего не работает, с настройками → работает

### Фаза 3: UI для ручного ввода
**Файлы:**
- `android/app/src/main/java/com/homeplanner/MainActivity.kt` - расширить ViewTab.SETTINGS

**Задачи:**
1. В секции Settings добавить:
   - Отображение текущих настроек (host, port, apiVersion, protocol)
   - Кнопки действий
2. Создать диалог редактирования:
   - OutlinedTextField для host (пустое по умолчанию)
   - OutlinedTextField для port (**8000 по умолчанию**)
   - **ExposedDropdownMenuBox** для apiVersion (список из `SupportedApiVersions.versions`)
   - Switch для HTTP/HTTPS (**HTTPS (true) по умолчанию**)
   - Валидация полей (включая проверку версии API)
   - Кнопки Сохранить/Отмена
3. Реализовать "Проверка подключения":
   - HTTP/HTTPS запрос к `/api/v{apiVersion}/tasks/`
   - Показать результат (успех/ошибка)
4. Реализовать "Очистить настройки":
   - Вызвать `networkSettings.clearConfig()`
   - Применить настройки
5. После сохранения:
   - Переподключить WebSocket
   - Перезагрузить данные
   - Показать уведомление

### Фаза 4: Сканер QR-кода
**Файлы:**
- `android/app/build.gradle.kts` - зависимости
- `android/app/src/main/AndroidManifest.xml` - разрешения
- `android/app/src/main/java/com/homeplanner/QrCodeScanner.kt` - новый файл
- `android/app/src/main/java/com/homeplanner/MainActivity.kt` - интеграция

**Задачи:**
1. Добавить зависимости (ML Kit или ZXing)
2. Добавить разрешение `CAMERA` в манифест
3. Реализовать запрос разрешения в Compose
4. Создать `QrCodeScannerDialog` Composable:
   - Dialog с камерой превью внутри
   - Обработка сканирования
   - Парсинг JSON (упрощенный формат)
   - Автоматическое закрытие при успешном сканировании
5. Создать функцию парсинга `parseNetworkConfigFromJson()` (упрощенный формат)
6. Интегрировать в Settings:
   - Кнопка "Сканировать QR-код"
   - Открытие диалога со сканером
   - Сохранение результата
7. Обработка ошибок (неверный формат, отсутствие данных, неподдерживаемая версия API)

### Фаза 5: Генерация QR в веб-версии
**Файлы:**
- `frontend/index.html` - добавить секцию UI
- `frontend/app.js` - добавить логику генерации

**Задачи:**
1. В `#settings-view` добавить новую секцию "Настройки подключения для Android"
2. Добавить кнопку "Показать QR-код с настройками"
3. Создать функцию `generateNetworkConfig()`:
   - Получить host, port, apiVersion из window объектов
   - Определить useHttps из протокола (если `window.location.protocol === "https:"` → true)
   - **По умолчанию useHttps = true** (если не удалось определить)
   - **Использовать упрощенный формат без поля "type"**
4. Создать функцию `extractApiVersion()` для парсинга версии из URL
5. Использовать существующую `renderQr()` для генерации QR-кода
6. Показывать текущие настройки
7. Добавить информационное сообщение про localhost (для эмуляции)

**Дополнительно:**
- localhost допустим для QR-кода (для использования в эмуляторе)

---

## Вопросы для обсуждения

1. **Формат хранения:** ✅ **Preferences DataStore** (выбрано)
   - Асинхронный API, безопасность потоков
   - Обработка ошибок, гарантии целостности данных
   - Современный подход, рекомендуется Google

2. **Валидация URL:** ✅ **Простая проверка формата** (выбрано)
   - Проверка что host не пустой
   - Проверка что port в диапазоне 1-65535
   - Проверка что apiVersion не пустой
   - Попытка подключения - только по кнопке "Проверить подключение"

3. **Версия API:** ✅ **Из BuildConfig + ручной ввод** (выбрано)
   - По умолчанию парсить из `BuildConfig.API_BASE_URL`
   - Пользователь может изменить вручную
   - В веб-версии парсить из текущего URL

4. **Обратная совместимость:** ✅ **НЕТ fallback** (выбрано)
   - Если настройки не заданы - сетевые функции не работают
   - Пользователь должен явно настроить подключение
   - При первом запуске показывать инструкцию по настройке

5. **WebSocket:** ✅ **Переподключать сразу** (выбрано)
   - При изменении настроек - сразу переподключить WebSocket
   - Перезагрузить данные
   - Лучший UX, пользователь сразу видит результат

6. **Библиотека для QR:** Какую выбрать?
   - ML Kit Barcode Scanning - современная, от Google
   - ZXing - проще, проверенная временем
   - **Рекомендация:** ML Kit (более современная)

7. **Формат QR-кода:** ✅ **Упрощенный формат** (выбрано)
   - Без поля "type", только обязательные поля (host, port, apiVersion, useHttps)
   - Проще парсинг, меньше данных

8. **Обработка ошибок сети:**
   - Показывать ли ошибки подключения в UI?
   - **Рекомендация:** Да, но не блокировать работу приложения

