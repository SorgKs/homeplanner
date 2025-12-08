#!/bin/bash
# Обертка для запуска Gradle из корня проекта
# Переходит в android/ и запускает gradlew оттуда

cd "$(dirname "$0")/android" || exit 1
exec ./gradlew "$@"

