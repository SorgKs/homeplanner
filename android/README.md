# Android приложение HomePlanner

Простое Android приложение для создания событий и управления напоминаниями.

## Требования

- JDK 17 или выше
- Android SDK
- Gradle (используется wrapper из проекта)

## Настройка для разработки

### 1. Настройка локальных свойств

Проект разрабатывается на нескольких машинах, поэтому локальные настройки (пути к SDK, JDK) хранятся в `local.properties`, который не попадает в репозиторий.

Создайте файл `local.properties` на основе шаблона:

```bash
# Скопируйте шаблон
cp local.properties.template local.properties
```

Или создайте файл вручную и укажите пути для вашей машины:

```properties
# Android SDK location
sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk

# Java Development Kit (JDK) location (JDK 17 или выше)
# Вариант 1: Указать в local.properties
java.home=C:\\Program Files\\Java\\jdk-17

# Вариант 2: Использовать переменную окружения JAVA_HOME (рекомендуется)
# В этом случае просто установите JAVA_HOME в системе, и эта строка не нужна
```

**Важно**: 
- Файл `local.properties` уже добавлен в `.gitignore` и не попадает в репозиторий
- Каждый разработчик настраивает пути под свою машину
- JDK можно указать либо в `local.properties` (свойство `java.home`), либо через переменную окружения `JAVA_HOME`

### 2. Сборка APK

#### Из командной строки (Windows)

Рекомендуется использовать скрипт-обертку, который автоматически читает JDK из `local.properties`:

```cmd
.\build.bat :app:assembleDebug
```

Или напрямую через Gradle wrapper (если `JAVA_HOME` установлен в системе):

```cmd
.\gradlew.bat :app:assembleDebug
```

#### Из командной строки (Linux/Mac)

```bash
./gradlew :app:assembleDebug
```

Собранный APK будет находиться в: `app/build/outputs/apk/debug/homeplanner_v<version>.apk`

### 3. Сборка в Android Studio

1. Откройте папку `android/` в Android Studio
2. Выполните синхронизацию Gradle (File → Sync Project with Gradle Files)
3. Запустите конфигурацию `app` на эмуляторе или устройстве

## Структура

Приложение создано с использованием:
- Kotlin
- Jetpack Compose для UI
- Android SDK
- OkHttp для сетевых запросов
- Kotlinx Serialization для работы с JSON

## Версионирование

**ВАЖНО**: Версия Android приложения должна быть в формате `MAJOR.MINOR.PATCH` (три компонента), как и все остальные части проекта.

Версия приложения формируется автоматически:
- `MAJOR.MINOR` - из `pyproject.toml` (общая версия проекта)
- `PATCH` - из `android/version.json`

Итоговая версия: `MAJOR.MINOR.PATCH` (например, `0.2.0`)

**Примечание**: BUILD номер (из `app/build_number.txt`) используется только для внутренних целей сборки (versionCode в Android), но не является частью версии приложения (versionName). Версия приложения всегда должна быть в формате `MAJOR.MINOR.PATCH`.

