"""Вспомогательные функции для работы с datetime в тестах."""

from __future__ import annotations

from datetime import datetime


def normalize_datetime(dt: datetime) -> datetime:
    """Удалить микросекунды из datetime."""

    return dt.replace(microsecond=0)


def isoformat_no_microseconds(dt: datetime) -> str:
    """Преобразовать datetime в ISO-строку без микросекунд."""

    return normalize_datetime(dt).isoformat()


__all__ = ["normalize_datetime", "isoformat_no_microseconds"]

