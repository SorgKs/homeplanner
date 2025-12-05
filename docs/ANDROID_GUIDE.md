# Руководство по разработке Android версии HomePlanner

Это руководство содержит информацию о разработке, сборке, тестировании и отладке Android приложения HomePlanner.

## Содержание

1. [Требования](#требования)
2. [Настройка окружения](#настройка-окружения)
3. [Эмуляция](#эмуляция)
4. [Сборка приложения](#сборка-приложения)
5. [Установка и запуск](#установка-и-запуск)
6. [Debug версия](#debug-версия)
7. [Release версия](#release-версия)
8. [Версионирование](#версионирование)
9. [Отладка](#отладка)
10. [Логирование](#логирование)
11. [Настройка сети](#настройка-сети)

---

## Требования

### Необходимое ПО

- **JDK 17** или выше (рекомендуется Eclipse Adoptium или OpenJDK)
- **Android SDK** (через Android Studio или командную строку)
- **Gradle** (используется wrapper из проекта, версия указана в `gradle/wrapper/gradle-wrapper.properties`)
- **PowerShell** (для Windows) или **Bash** (для Linux/Mac) для запуска скриптов

### Минимальные версии Android

- **Минимальная версия SDK**: Android 7.0 (API 24)
- **Целевая версия SDK**: Android 14 (API 34)
- **Рекомендуемая версия для тестирования**: Android 10+ (API 29+)

---

## Настройка окружения

### 1. Настройка локальных свойств

Проект использует файл `local.properties` для хранения путей к SDK и JDK. Этот файл не попадает в репозиторий (добавлен в `.gitignore`), поэтому каждый разработчик настраивает его под свою машину.

#### Создание файла `local.properties`

Создайте файл `android/local.properties` на основе шаблона:

```bash
# Windows
cd android
copy local.properties.template local.properties

# Linux/Mac
cd android
cp local.properties.template local.properties
```

#### Настройка путей

Откройте `android/local.properties` и укажите пути для вашей машины:

```properties
# Android SDK location
sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk

# Java Development Kit (JDK) location (JDK 17 или выше)
# Вариант 1: Указать в local.properties
java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.17.10-hotspot

# Вариант 2: Использовать переменную окружения JAVA_HOME (рекомендуется)
# В этом случае просто установите JAVA_HOME в системе, и эта строка не нужна

# Имя AVD для эмулятора (опционально, по умолчанию используется "HomePlanner34")
avd.name=HomePlanner34
```

**Важно**: 
- Используйте прямые слеши (`/`) или двойные обратные (`\\`) для путей в Windows
- JDK можно указать либо в `local.properties` (свойство `java.home`), либо через переменную окружения `JAVA_HOME`
- Если `JAVA_HOME` установлен в системе, свойство `java.home` в `local.properties` не требуется

### 2. Проверка установки

Убедитесь, что все компоненты установлены:

```bash
# Проверка JDK
java -version

# Проверка Android SDK (если установлен через Android Studio)
# Путь обычно: %LOCALAPPDATA%\Android\Sdk на Windows
# или ~/Android/Sdk на Linux/Mac

# Проверка Gradle (через wrapper)
cd android
.\gradlew.bat --version  # Windows
./gradlew --version       # Linux/Mac
```

---

## Эмуляция

### Запуск эмулятора

Для разработки и тестирования используется Android эмулятор. Проект включает скрипты для автоматического запуска эмулятора.

#### Использование скрипта запуска

**Windows (PowerShell):**
```powershell
cd android
.\start_emulator.ps1
```

**Windows (Batch):**
```cmd
cd android
start_emulator.bat
```

**Linux/Mac:**
```bash
cd android
./start_emulator.sh
```

Скрипт автоматически:
- Читает путь к SDK из `local.properties`
- Использует AVD из `local.properties` (свойство `avd.name`) или значение по умолчанию `HomePlanner34`
- Запускает эмулятор с оптимальными настройками сети

#### Запуск конкретного AVD

Вы можете указать имя AVD в качестве аргумента:

```powershell
.\start_emulator.ps1 Pixel_6_34
```

#### Создание AVD

Если у вас еще нет AVD, создайте его через Android Studio:

1. Откройте Android Studio
2. Tools → Device Manager
3. Create Device
4. Выберите устройство (рекомендуется Pixel 6 или новее)
5. Выберите системный образ (рекомендуется Android 10+)
6. Завершите создание AVD

Или через командную строку:

```bash
# Список доступных системных образов
sdkmanager --list | grep system-images

# Создание AVD
avdmanager create avd -n HomePlanner34 -k "system-images;android-34;google_apis;x86_64"
```

#### Настройка сети эмулятора

Для подключения к backend, запущенному на хосте:

- **HTTP/HTTPS**: Используйте `10.0.2.2` вместо `localhost` или `127.0.0.1`
- **Порт**: Используйте тот же порт, что и на хосте (например, `8000`)

Пример настройки в приложении:
- Хост: `10.0.2.2`
- Порт: `8000`
- Протокол: `http` или `https` (в зависимости от настроек backend)

---

## Сборка приложения

### Использование скриптов сборки

Проект включает скрипты для автоматической сборки с логированием.

#### Windows

**PowerShell:**
```powershell
cd android
.\build.ps1              # Release версия (инкрементирует версию)
.\build.ps1 --debug       # Debug версия (не инкрементирует версию)
.\build.ps1 -d            # Короткая форма для debug
```

**Batch:**
```cmd
cd android
build.bat                 # Release версия
build.bat --debug         # Debug версия
build.bat -d              # Короткая форма для debug
```

#### Linux/Mac

```bash
cd android
./build.sh                # Release версия
./build.sh --debug        # Debug версия
./build.sh -d             # Короткая форма для debug
```

### Что делают скрипты сборки

1. **Читают JDK** из `local.properties` или используют `JAVA_HOME`
2. **Обновляют версию** (только для release сборки):
   - Читают текущий patch из `android/version.json`
   - Инкрементируют patch версию
   - Сохраняют обновленную версию
3. **Очищают предыдущую сборку**: `gradlew clean`
4. **Собирают APK**: `gradlew assembleRelease` или `gradlew assembleDebug`
5. **Логируют весь процесс** в `android/build_output.log`
6. **Показывают информацию** о созданном APK (путь, размер)

### Ручная сборка через Gradle

Если вы предпочитаете использовать Gradle напрямую:

```bash
cd android

# Debug версия
.\gradlew.bat assembleDebug    # Windows
./gradlew assembleDebug        # Linux/Mac

# Release версия
.\gradlew.bat assembleRelease  # Windows
./gradlew assembleRelease      # Linux/Mac
```

### Расположение APK

После успешной сборки APK находится в:

- **Debug**: `android/app/build/outputs/apk/debug/homeplanner_v<version>.apk`
- **Release**: `android/app/build/outputs/apk/release/homeplanner_v<version>.apk`

---

## Установка и запуск

### Автоматическая установка и запуск

Используйте скрипты для автоматической установки и запуска приложения:

#### Windows

**PowerShell:**
```powershell
cd android
.\run.ps1
```

**Batch:**
```cmd
cd android
run.bat
```

#### Linux/Mac

```bash
cd android
./run.sh
```

### Что делают скрипты установки

1. **Проверяют подключенные устройства** через `adb devices`
2. **Находят последний APK** (предпочитают release, затем debug)
3. **Устанавливают APK** на устройство/эмулятор: `adb install -r <apk>`
4. **Запускают приложение**: `adb shell am start -n com.homeplanner/.MainActivity`
5. **Логируют весь процесс** в `android/install_output.log`

### Ручная установка через ADB

```bash
# Проверка подключенных устройств
adb devices

# Установка APK
adb install -r android/app/build/outputs/apk/debug/homeplanner_v0.2.74.apk

# Запуск приложения
adb shell am start -n com.homeplanner/.MainActivity
```

### Установка через Android Studio

1. Откройте проект в Android Studio
2. Подключите устройство или запустите эмулятор
3. Нажмите кнопку "Run" (зеленая стрелка) или `Shift+F10`
4. Выберите устройство из списка

---

## Debug версия

### Особенности debug версии

- **Не требует подписи**: Можно устанавливать без настройки ключей
- **Не инкрементирует версию**: Patch версия остается прежней
- **Включает отладочную информацию**: Логи, stack traces
- **Быстрая сборка**: Оптимизации отключены

### Когда использовать debug версию

- Во время разработки
- Для тестирования новых функций
- Для отладки проблем
- Для быстрой итерации

### Сборка debug версии

```powershell
# Windows
.\build.ps1 --debug

# Linux/Mac
./build.sh --debug
```

### Установка debug версии

Debug APK можно установить на любое устройство без дополнительной настройки:

```bash
adb install -r android/app/build/outputs/apk/debug/homeplanner_v0.2.74.apk
```

---

## Release версия

### Особенности release версии

- **Требует подписи**: Необходимо настроить ключи подписи (для production)
- **Инкрементирует версию**: Автоматически увеличивает patch версию
- **Оптимизирована**: Включены оптимизации кода и ресурсов
- **Минимизирована**: Удалены отладочные символы (частично)

### Когда использовать release версию

- Для тестирования финальной версии
- Для распространения среди пользователей
- Для публикации в Google Play

### Сборка release версии

```powershell
# Windows
.\build.ps1

# Linux/Mac
./build.sh
```

### Подпись release APK

Для установки release APK на реальные устройства или публикации в Google Play требуется подпись.

#### Настройка ключа подписи

1. Создайте keystore (если еще нет):

```bash
keytool -genkey -v -keystore homeplanner-release.keystore -alias homeplanner -keyalg RSA -keysize 2048 -validity 10000
```

2. Настройте `android/app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../homeplanner-release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = "homeplanner"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

**Важно**: Никогда не коммитьте keystore или пароли в репозиторий!

#### Установка неподписанного release APK

Для тестирования на эмуляторе можно установить неподписанный release APK:

```bash
adb install -r android/app/build/outputs/apk/release/homeplanner_v0.2.75.apk
```

**Примечание**: На реальных устройствах неподписанный release APK установить нельзя.

---

## Версионирование

### Формат версии

Android приложение использует формат версии `MAJOR.MINOR.PATCH` (три компонента), как и все остальные части проекта.

- **MAJOR.MINOR**: Из `pyproject.toml` (общая версия проекта)
- **PATCH**: Из `android/version.json`

Итоговая версия: `MAJOR.MINOR.PATCH` (например, `0.2.74`)

### Обновление версии

#### Автоматическое обновление (рекомендуется)

При сборке release версии скрипт автоматически:
1. Читает текущий patch из `android/version.json`
2. Инкрементирует patch версию
3. Сохраняет обновленную версию

#### Ручное обновление

Если нужно обновить версию вручную:

1. Откройте `android/version.json`:
```json
{
    "patch": 74
}
```

2. Измените значение `patch`:
```json
{
    "patch": 75
}
```

3. При следующей release сборке будет использована новая версия

### Обновление MAJOR.MINOR

При изменении MAJOR или MINOR версии проекта:

1. Обновите `pyproject.toml` (секция `[tool.homeplanner.versions]`)
2. Обнулите patch в `android/version.json`:
```json
{
    "patch": 0
}
```

Подробнее о версионировании см. [VERSIONING.md](VERSIONING.md).

---

## Отладка

### Логи приложения

Приложение использует стандартный Android Log для логирования:

```kotlin
import android.util.Log

Log.d("Tag", "Debug message")
Log.i("Tag", "Info message")
Log.w("Tag", "Warning message")
Log.e("Tag", "Error message", exception)
```

### Просмотр логов через ADB

```bash
# Все логи
adb logcat

# Логи с фильтром по тегу
adb logcat -s TasksScreen SettingsScreen

# Логи с фильтром по уровню
adb logcat *:E  # Только ошибки

# Очистка логов
adb logcat -c

# Сохранение логов в файл
adb logcat > logcat.txt
```

### Просмотр логов через Android Studio

1. Откройте проект в Android Studio
2. Запустите приложение в режиме отладки
3. Откройте вкладку "Logcat" внизу экрана
4. Используйте фильтры для поиска нужных сообщений

### Отладка через Android Studio

1. Установите breakpoints в коде
2. Запустите приложение в режиме отладки (кнопка "Debug" или `Shift+F9`)
3. Приложение остановится на breakpoints
4. Используйте панель отладки для просмотра переменных и выполнения кода пошагово

### Отладка WebSocket

Приложение включает вкладку "WebSocket" для мониторинга WebSocket соединения:

- Просмотр входящих и исходящих сообщений
- Статус подключения
- История сообщений

---

## Логирование

### Логи сборки

Все скрипты сборки автоматически логируют вывод в файлы:

- **Сборка**: `android/build_output.log`
- **Установка**: `android/install_output.log`

Логи сохраняются с временными метками и включают:
- Весь вывод команд
- Ошибки компиляции
- Информацию о созданных APK

### Просмотр логов сборки

```bash
# Windows
type android\build_output.log
type android\install_output.log

# Linux/Mac
cat android/build_output.log
cat android/install_output.log

# Последние строки
tail -n 50 android/build_output.log
```

### Очистка логов

Логи накапливаются в файлах. При необходимости можно очистить:

```bash
# Windows
del android\build_output.log
del android\install_output.log

# Linux/Mac
rm android/build_output.log
rm android/install_output.log
```

---

## Настройка сети

### Настройка подключения к backend

Приложение включает встроенные настройки сети для подключения к backend:

1. Откройте приложение
2. Перейдите на вкладку "Настройки"
3. Нажмите "Настроить подключение"
4. Введите параметры:
   - **Хост**: IP адрес или домен (для эмулятора используйте `10.0.2.2`)
   - **Порт**: Порт backend (по умолчанию `8000`)
   - **API Версия**: Версия API (по умолчанию `0.2`)
   - **Использовать HTTPS**: Включите, если backend использует HTTPS

### Проверка подключения

После настройки используйте кнопку "Проверить подключение" для тестирования:

- Проверка HTTP подключения
- Проверка WebSocket подключения
- Отображение результатов теста

### Настройка через QR-код

Приложение поддерживает настройку через QR-код:

1. В настройках нажмите "Сканировать QR-код"
2. Отсканируйте QR-код с настройками подключения
3. Настройки будут автоматически применены

Формат JSON для QR-кода:
```json
{
    "host": "10.0.2.2",
    "port": 8000,
    "api_version": "0.2",
    "use_https": false
}
```

### Подключение к backend на хосте

Для подключения к backend, запущенному на том же компьютере:

- **Эмулятор**: Используйте `10.0.2.2` вместо `localhost`
- **Реальное устройство**: Используйте IP адрес компьютера в локальной сети

---

## Часто задаваемые вопросы

### Ошибка: "version.json not found"

Убедитесь, что файл `android/version.json` существует и содержит корректный JSON:

```json
{
    "patch": 74
}
```

### Ошибка: "local.properties not found"

Создайте файл `android/local.properties` на основе шаблона `local.properties.template`.

### Ошибка: "sdk.dir not defined"

Укажите путь к Android SDK в `android/local.properties`:

```properties
sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
```

### Ошибка: "java.home not found"

Укажите путь к JDK в `android/local.properties` или установите переменную окружения `JAVA_HOME`.

### APK не устанавливается на устройство

- Для release APK требуется подпись
- Убедитесь, что на устройстве включена установка из неизвестных источников
- Проверьте, что версия Android на устройстве соответствует минимальным требованиям

### Не могу подключиться к backend

- Для эмулятора используйте `10.0.2.2` вместо `localhost`
- Убедитесь, что backend запущен и доступен
- Проверьте настройки файрвола
- Используйте функцию "Проверить подключение" в настройках приложения

---

## Дополнительные ресурсы

- [Android Developer Documentation](https://developer.android.com/)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Gradle Documentation](https://docs.gradle.org/)

---

## Поддержка

При возникновении проблем:

1. Проверьте логи сборки: `android/build_output.log`
2. Проверьте логи установки: `android/install_output.log`
3. Проверьте логи приложения через `adb logcat`
4. Обратитесь к документации проекта или создайте issue в репозитории

