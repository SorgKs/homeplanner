"""Утилиты для построения API путей в тестах."""

from __future__ import annotations

from functools import lru_cache

from backend.config import get_settings


@lru_cache(maxsize=1)
def _api_prefix() -> str:
    """Получить префикс API из настроек."""

    return get_settings().api_prefix


def api_path(path: str) -> str:
    """Вернуть абсолютный путь API с учётом версии.

    Args:
        path: относительный путь, может начинаться или не начинаться с `/`.

    Returns:
        Строка вида `/api/vX.Y/<path>`.
    """

    if not path.startswith("/"):
        path = f"/{path}"
    return f"{_api_prefix()}{path}"


__all__ = ["api_path"]

