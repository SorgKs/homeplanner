### Задание для кодера: Переработка экрана задач с использованием Jetpack Navigation Compose

**Цель задания:**  
Переработать текущий код экрана `TaskListScreen` (и, при необходимости, связанные компоненты), чтобы реализовать стандартный подход к организации вкладок в Android-приложении на Jetpack Compose. Вместо локального управления состоянием вкладок через `selectedTabIndex` и `when`, использовать **Jetpack Navigation Compose** с `NavHost`, отдельными маршрутами для каждой вкладки и нижней навигационной панелью (`NavigationBar`), которая отражает текущий маршрут. Это обеспечит правильную обработку навигации, back stack, восстановление состояния и возможность добавления вложенных экранов в будущем.

**Текущие проблемы в коде (для справки):**  
- Локальное управление вкладками через `mutableIntStateOf` и `when` приводит к проблемам с навигацией назад, восстановлением состояния и масштабируемостью.  
- Смешение событий `UiEvent.NavigateTo...Tab` с локальным состоянием создает путаницу.  
- Нет поддержки deep links или вложенной навигации.  

**Рекомендуемый подход (основан на официальных гайдлайнах Google 2024–2026):**  
- Использовать один `NavHost` для всего приложения (или для основного экрана).  
- Каждая вкладка — отдельный маршрут (route) с собственным composable-экраном.  
- Нижняя панель отражает текущий маршрут через `navController.currentBackStackEntryAsState()`.  
- Переходы между вкладками — через `navController.navigate(route)` с опциями для правильной работы back stack (popUpTo start destination, launchSingleTop, restoreState).  

**Шаги по реализации:**

1. **Подготовка зависимостей:**  
    - Убедитесь, что в `build.gradle` добавлена зависимость на Jetpack Navigation Compose:  
      ```
      implementation("androidx.navigation:navigation-compose:2.8.0")  // Или актуальная версия на 2026 год
      ```  

2. **Определение маршрутов:**  
    - Создайте sealed class для экранов/маршрутов. Пример:  
      ```kotlin
      sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
          object Today : Screen("today", "Сегодня", { Icon(Icons.Filled.Home, "Сегодня") })
          object AllTasks : Screen("all_tasks", "Все задачи", { Icon(Icons.AutoMirrored.Filled.List, "Все задачи") })
          object Settings : Screen("settings", "Настройки", { Icon(Icons.Filled.Settings, "Настройки") })
      }
      ```  
    - Добавьте startDestination как `Screen.Today.route`.

3. **Переработка главного экрана (например, MainScreen или переименованный TaskListScreen):**  
    - Переименуйте `TaskListScreen` в `MainScreen` или аналогично, чтобы он стал контейнером для NavHost.  
    - Добавьте параметр `navController: NavHostController` (создайте его в Activity или выше с `rememberNavController()`).  
    - Структура:  
      - `Scaffold` с bottomBar как `NavigationBar`.  
      - В bottomBar: для каждого `Screen` проверяйте `currentRoute == screen.route` и на onClick вызывайте `navController.navigate(screen.route)` с опциями:  
        ```kotlin
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
        ```  
      - В content Scaffold: разместите `NavHost(navController, startDestination = Screen.Today.route) { ... }`.  
      - В NavHost добавьте composable для каждого маршрута:  
        - `composable(Screen.Today.route) { TodayScreen(...) }`  
        - Аналогично для AllTasks и Settings.  

4. **Разделение контента на отдельные экраны и файлы:**

    **Важно:** Текущий `TaskListScreen.kt` очень большой (811 строк). Разделите его на несколько отдельных файлов:

    - `MainScreen.kt`: Навигационная структура с NavHost, Scaffold и NavigationBar
    - `TodayScreen.kt`: Экран задач на сегодня
    - `AllTasksScreen.kt`: Экран всех задач
    - `SettingsScreen.kt`: Экран настроек

    Удалите оригинальный файл `TaskListScreen.kt` после создания новых.

    - Создайте переиспользуемые компоненты для избежания дублирования кода между TodayScreen и AllTasksScreen:
      - `TaskListContent.kt`: Общий компонент `TaskListContent(tasks: List<Task>, state: TaskScreenState, onCreateTask: () -> Unit, onTaskClick: (Task) -> Unit, onTaskComplete: (Int) -> Unit, onTaskDelete: (Int) -> Unit, modifier: Modifier)`
        - Содержит логику проверки loading/error/empty и отображения AllTasksList или EmptyState

    - Выделите контент вкладок в отдельные composable-функции в соответствующих файлах:
      - `TodayScreen.kt`: `TodayScreen(state: TaskScreenState, selectedUser: User?, onCreateTask: () -> Unit, onTaskClick: (Task) -> Unit, onTaskComplete: (Int) -> Unit, onTaskDelete: (Int) -> Unit, modifier: Modifier)`
        - Фильтрует задачи через `TodayTaskFilter`, затем использует `TaskListContent`
      - `AllTasksScreen.kt`: `AllTasksScreen(state: TaskScreenState, onCreateTask: () -> Unit, onTaskClick: (Task) -> Unit, onTaskComplete: (Int) -> Unit, onTaskDelete: (Int) -> Unit, modifier: Modifier)`
        - Передает все задачи напрямую в `TaskListContent`
      - `SettingsScreen.kt`: `SettingsScreen(modifier: Modifier)` - вынесите из TaskListScreen_p1.kt и TaskListScreen_p2.kt.

    - Передавайте `paddingValues` как `Modifier.padding(paddingValues)`.
    - Удалите локальный `selectedTabIndex` и `when` — навигация теперь управляется NavHost.
    - Удалите `UiEvent.NavigateTo...Tab` — они больше не нужны, так как навигация через navController.

5. **Обработка состояния и зависимостей:**  
    - `UserSettings` и `selectedUser` (из collectAsState): Разместите их в главном экране и передавайте в дочерние экраны (или используйте CompositionLocal для глобального доступа).  
    - Состояние `state: TaskScreenState` и колбэки (`onEvent`, `onTaskClick` и т.д.): Передавайте их в соответствующие экраны. Если состояние общее, подумайте о ViewModel с shared state.  

6. **Требования к коду:**  
    - Код должен быть чистым, с использованием rememberSaveable где нужно.    
    - Обеспечьте совместимость с темной/светлой темой и Material3.

7. **Тестирование рефакторинга:**
    - **Интеграционные тесты:** Протестируйте навигацию между вкладками, сохранение состояния при повороте устройства.
    - **Unit-тесты:** Добавьте тесты для новых composable-функций (TodayScreen, AllTasksScreen, SettingsScreen).
    - **UI-тесты:** Обновите существующие UI-тесты для работы с новой навигационной структурой.
    - **Регрессионное тестирование:** Убедитесь, что функциональность задач (создание, редактирование, удаление) работает корректно.
    - **Проверка навигации:** Протестируйте back stack, deep links и восстановление состояния после process death.

8. **Работа с существующими кусками кода:**
    - Файл `TaskListScreen.kt` уже временно разбит на `TaskListScreen_p1.kt` и `TaskListScreen_p2.kt`.
    - Используйте эти куски как основу для создания новых файлов `MainScreen.kt` и `TaskScreens.kt`.
    - После успешного рефакторинга удалите временные файлы `TaskListScreen_p*.kt`.