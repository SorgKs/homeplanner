#!/bin/bash
# Скрипт для вывода итоговой статистики Android тестов

REPORT_PATH="app/build/reports/androidTests/connected/debug/index.html"

if [ ! -f "$REPORT_PATH" ]; then
    echo "Отчет не найден. Запустите тесты сначала:"
    echo "  ./gradlew :app:connectedDebugAndroidTest"
    exit 1
fi

# Парсим HTML отчет с помощью Python
python3 << 'PYTHON_SCRIPT'
import re
from pathlib import Path

report_path = Path('app/build/reports/androidTests/connected/debug/index.html')
if not report_path.exists():
    print('Отчет не найден')
    exit(1)

content = report_path.read_text()

# Извлекаем значения
tests_match = re.search(r'<div class="counter">(\d+)</div>\s*<p>tests</p>', content)
failures_match = re.search(r'<div class="counter">(\d+)</div>\s*<p>failures</p>', content)
skipped_match = re.search(r'<div class="counter">(\d+)</div>\s*<p>skipped</p>', content)
duration_match = re.search(r'<div class="counter">([\d.]+)s</div>\s*<p>duration</p>', content)
success_match = re.search(r'<div class="percent">(\d+)%</div>', content)

tests = int(tests_match.group(1)) if tests_match else 0
failures = int(failures_match.group(1)) if failures_match else 0
skipped = int(skipped_match.group(1)) if skipped_match else 0
duration = duration_match.group(1) if duration_match else "0"
success = success_match.group(1) if success_match else "0"

passed = tests - failures - skipped

print("=" * 60)
print("ИТОГОВАЯ СТАТИСТИКА ТЕСТОВ")
print("=" * 60)
print(f"Всего тестов:        {tests}")
print(f"Успешно:             {passed}")
print(f"Упало:               {failures}")
print(f"Пропущено:           {skipped}")
print(f"Время выполнения:    {duration}s")
print(f"Успешность:          {success}%")
print("=" * 60)
PYTHON_SCRIPT

