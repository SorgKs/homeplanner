"""Router for debug logs endpoint."""

import logging
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request, Header
from sqlalchemy.orm import Session

from backend.binary_chunk_decoder import BinaryChunkDecoder
from backend.database import get_db
from backend.models.debug_log import DebugLog
from backend.schemas.debug_log import DebugLogBatchCreate, DebugLogCreate, DebugLogResponse

logger = logging.getLogger(__name__)

router = APIRouter()

# Store для отслеживания повторных попыток отправки чанков
# Ключ: (device_id, chunk_id), значение: количество попыток
_chunk_retry_tracker: dict[tuple[str, str], int] = {}


def _persist_and_log_bad_chunk(
    decoder: BinaryChunkDecoder,
    binary_data: bytes,
    device_id: Optional[str],
    chunk_id: Optional[str],
    error: Exception,
    context_note: Optional[str] = None,
) :  # type: ignore[override]
    """Сохранить повреждённый бинарный чанк и записать подробный лог расшифровки.

    Args:
        decoder: Экземпляр декодера бинарных чанков.
        binary_data: Сырые байты полученного чанка.
        device_id: Идентификатор устройства из заголовка запроса или чанка.
        chunk_id: Идентификатор чанка из заголовка запроса или чанка.
        error: Исключение, возникшее при обработке.
        context_note: Дополнительная информация для лога (например, источник ошибки).

    Returns:
        Путь к сохранённому файлу с чанком в виде строки или ``None``,
        если сохранить файл не удалось.
    """
    device_id_for_error = device_id or "unknown"
    chunk_id_for_error = chunk_id or "unknown"

    # Попытка сохранить сырые байты чанка для последующего офлайн-анализа.
    chunk_path: Optional[Path] = None
    bad_chunks_dir: Path | None = None
    try:
        project_root = Path(__file__).parent.parent
        bad_chunks_dir = project_root / "logs" / "bad_debug_chunks"
        bad_chunks_dir.mkdir(parents=True, exist_ok=True)

        timestamp_suffix = datetime.utcnow().strftime("%Y%m%dT%H%M%S%fZ")
        safe_device = device_id_for_error.replace("/", "_")
        safe_chunk = chunk_id_for_error.replace("/", "_")
        file_name = f"{safe_device}_{safe_chunk}_{timestamp_suffix}.bin"
        chunk_path = bad_chunks_dir / file_name
        chunk_path.write_bytes(binary_data)
    except Exception as persist_exc:  # noqa: BLE001
        logger.error(
            "Failed to persist corrupted binary chunk %s (device: %s): %s",
            chunk_id_for_error,
            device_id_for_error,
            persist_exc,
            exc_info=True,
        )

    # Диагностическая расшифровка: пытаемся понять, где именно «поехала» структура.
    diag_header, diag_entries, diag_error = decoder.try_decode_for_diagnostics(binary_data)

    last_entry_summary: Optional[str] = None
    if diag_entries:
        last_entry = diag_entries[-1]
        last_entry_summary = (
            f"{last_entry.timestamp.isoformat()} {last_entry.level} {last_entry.text}"
        )

    logger.error(
        (
            "Failed to process binary chunk %s (device: %s): %s; "
            "context=%r; diagnostics: decoded_entries=%d, last_entry=%r, "
            "diag_error=%r, persisted_path=%s, header_for_diag=%r"
        ),
        chunk_id_for_error,
        device_id_for_error,
        error,
        context_note,
        len(diag_entries),
        last_entry_summary,
        diag_error,
        str(chunk_path) if chunk_path is not None else None,
        diag_header,
        exc_info=True,
    )

    # Когда битых чанков наберётся заметное количество, помечаем в логах TODO
    # на ручной анализ (считаем только .bin в каталоге bad_debug_chunks).
    if bad_chunks_dir is not None:
        try:
            bad_chunk_files = [p for p in bad_chunks_dir.iterdir() if p.is_file()]
            if len(bad_chunk_files) >= 5:
                logger.warning(
                    "TODO: analyze bad debug chunks — found %d files in %s",
                    len(bad_chunk_files),
                    bad_chunks_dir,
                )
        except Exception as count_exc:  # noqa: BLE001
            logger.error(
                "Failed to count bad debug chunks in %s: %s",
                bad_chunks_dir,
                count_exc,
                exc_info=True,
            )

    return str(chunk_path) if chunk_path is not None else None


@router.post("/debug-logs", response_model=list[DebugLogResponse], status_code=201)
def create_debug_logs(
    batch: DebugLogBatchCreate,
    db: Session = Depends(get_db),
) -> list[DebugLogResponse]:
    """Create one or more debug log entries.

    This endpoint accepts a batch of logs from Android debug builds.
    Only accessible in debug mode or with proper authentication.
    """
    logger.info(f"Received {len(batch.logs)} debug log entries from device {batch.logs[0].device_id if batch.logs else 'unknown'}")
    try:
        log_entries = []
        for log_data in batch.logs:
            log_entry = DebugLog(
                timestamp=log_data.timestamp,
                level=log_data.level.upper(),
                tag=log_data.tag,
                message_code=log_data.message_code,
                context=log_data.context,
                device_id=log_data.device_id,
                device_info=log_data.device_info,
                app_version=log_data.app_version,
                dictionary_revision=log_data.dictionary_revision,
            )
            db.add(log_entry)
            log_entries.append(log_entry)

        db.commit()

        # Refresh to get IDs
        for log_entry in log_entries:
            db.refresh(log_entry)

        logger.info(f"Created {len(log_entries)} debug log entries")
        return [DebugLogResponse.model_validate(log_entry) for log_entry in log_entries]
    except Exception as e:
        db.rollback()
        logger.error(f"Error creating debug logs: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to create debug logs: {str(e)}")


@router.get("/debug-logs", response_model=list[DebugLogResponse])
def get_debug_logs(
    level: Optional[str] = Query(None, description="Filter by log level"),
    tag: Optional[str] = Query(None, description="Filter by tag"),
    device_id: Optional[str] = Query(None, description="Filter by device ID"),
    query_text: Optional[str] = Query(None, alias="query", description="Search in text field (v2 format)"),
    limit: int = Query(100, ge=1, le=1000, description="Maximum number of logs to return"),
    hours: Optional[int] = Query(24, ge=1, le=168, description="Hours back to search"),
    db: Session = Depends(get_db),
) -> list[DebugLogResponse]:
    """Get debug logs with optional filtering.

    Returns logs ordered by timestamp (newest first).
    Use device_id to filter logs from a specific device.
    Use query parameter to search in text field (for v2 format logs).
    """
    query = db.query(DebugLog)

    # Filter by level
    if level:
        query = query.filter(DebugLog.level == level.upper())

    # Filter by tag
    if tag:
        query = query.filter(DebugLog.tag == tag)

    # Filter by device_id (for separating logs from different devices)
    if device_id:
        query = query.filter(DebugLog.device_id == device_id)
    
    # Search in text field (v2 format)
    if query_text:
        query = query.filter(DebugLog.text.ilike(f"%{query_text}%"))

    # Filter by time range
    if hours:
        cutoff_time = datetime.now() - timedelta(hours=hours)
        query = query.filter(DebugLog.timestamp >= cutoff_time)

    # Order by timestamp (newest first) and limit
    logs = query.order_by(DebugLog.timestamp.desc()).limit(limit).all()

    # Convert to response with expanded message descriptions
    result = []
    for log in logs:
        log_dict = {
            "id": log.id,
            "timestamp": log.timestamp,
            "level": log.level,
            "tag": log.tag,
            "message_code": log.message_code,
            "context": log.context or {},
            "device_id": log.device_id,
            "device_info": log.device_info,
            "app_version": log.app_version,
            "dictionary_revision": log.dictionary_revision,
            "text": log.text,
            "chunk_id": log.chunk_id,
        }
        result.append(DebugLogResponse.model_validate(log_dict))
    
    return result


@router.get("/debug-logs/devices")
def get_devices(
    db: Session = Depends(get_db),
) -> list[dict]:
    """Get list of unique devices that have sent logs.
    
    Returns list of devices with their latest log timestamp and device info.
    Useful for identifying which devices are sending logs.
    """
    from sqlalchemy import func
    
    # Get unique device_ids with their latest log timestamp and device info
    subquery = (
        db.query(
            DebugLog.device_id,
            func.max(DebugLog.timestamp).label("last_seen"),
            func.max(DebugLog.device_info).label("device_info"),
            func.max(DebugLog.app_version).label("app_version"),
            func.count(DebugLog.id).label("log_count"),
        )
        .filter(DebugLog.device_id.isnot(None))
        .group_by(DebugLog.device_id)
        .subquery()
    )
    
    results = db.query(subquery).order_by(subquery.c.last_seen.desc()).all()
    
    devices = []
    for row in results:
        devices.append({
            "device_id": row.device_id,
            "last_seen": row.last_seen.isoformat() if row.last_seen else None,
            "device_info": row.device_info,
            "app_version": row.app_version,
            "log_count": row.log_count,
        })
    
    return devices


@router.delete("/debug-logs/cleanup")
def cleanup_old_debug_logs(
    days: int = Query(7, ge=1, le=30, description="Delete logs older than this many days"),
    db: Session = Depends(get_db),
) -> dict:
    """Clean up old debug logs.

    This endpoint deletes logs older than the specified number of days.
    """
    try:
        cutoff_time = datetime.now() - timedelta(days=days)
        deleted_count = db.query(DebugLog).filter(DebugLog.timestamp < cutoff_time).delete()
        db.commit()

        logger.info(f"Cleaned up {deleted_count} debug log entries older than {days} days")
        return {"deleted": deleted_count, "cutoff_time": cutoff_time.isoformat()}
    except Exception as e:
        db.rollback()
        logger.error(f"Error cleaning up debug logs: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to cleanup debug logs: {str(e)}")


@router.post("/debug-logs/chunks")
async def receive_binary_chunk(
    request: Request,
    x_chunk_id: Optional[str] = Header(None, alias="X-Chunk-Id"),
    x_device_id: Optional[str] = Header(None, alias="X-Device-Id"),
    db: Session = Depends(get_db),
) -> dict:
    """Receive and decode binary log chunk from Android client.

    This endpoint implements the v2 binary chunk protocol with ACK/REPIT response.

    Returns:
        - {"result": "ACK", "chunk_id": "..."} - chunk successfully processed
        - {"result": "REPIT", "chunk_id": "..."} - chunk should be retried (first error)
        - {"result": "ACK", "chunk_id": "...", "error": "UNRECOVERABLE_CHUNK"} - chunk cannot be processed (second error)
    """
    decoder: Optional[BinaryChunkDecoder] = None
    binary_data: bytes = b""

    try:
        # Read binary data
        binary_data = await request.body()

        if not binary_data:
            logger.error("Received empty binary chunk")
            # Пустой чанк тоже считаем «битым», но сохранять нечего.
            return {"result": "ACK", "error": "EMPTY_CHUNK"}

        # Initialize decoder
        decoder = BinaryChunkDecoder()

        # Try to decode chunk
        try:
            header, entries = decoder.decode_chunk(binary_data)

            # Extract device_id from header or use header value
            device_id = header.device_id or x_device_id
            chunk_id_str = str(header.chunk_id) if header.chunk_id else x_chunk_id

            if not device_id:
                logger.warning("No device_id in chunk or header")
                device_id = "unknown"

            if not chunk_id_str:
                logger.warning("No chunk_id in chunk or header")
                chunk_id_str = "unknown"

            # Check if this is a retry
            retry_key = (device_id, chunk_id_str)
            retry_count = _chunk_retry_tracker.get(retry_key, 0)

            # Save decoded logs to database
            for entry in entries:
                log_record = DebugLog(
                    timestamp=entry.timestamp,
                    level=entry.level,
                    text=entry.text,
                    chunk_id=chunk_id_str,
                    device_id=device_id,
                    dictionary_revision=f"{header.dictionary_revision_major}.{header.dictionary_revision_minor}",
                )
                db.add(log_record)

            db.commit()

            # Clear retry tracker for this chunk
            if retry_key in _chunk_retry_tracker:
                del _chunk_retry_tracker[retry_key]

            logger.info(
                "Successfully decoded and saved %d log entries from chunk %s (device: %s)",
                len(entries),
                chunk_id_str,
                device_id,
            )
            return {"result": "ACK", "chunk_id": chunk_id_str}

        except ValueError as e:
            # Любая ошибка декодирования = чанк считаем битым.
            chunk_id_for_error = x_chunk_id or "unknown"
            device_id_for_error = x_device_id or header.device_id or "unknown"
            retry_key = (device_id_for_error, chunk_id_for_error)

            # Сохраняем чанк и пишем детальный лог с результатом расшифровки.
            _persist_and_log_bad_chunk(
                decoder=decoder,
                binary_data=binary_data,
                device_id=device_id_for_error,
                chunk_id=chunk_id_for_error,
                error=e,
                context_note="decode_chunk_value_error",
            )

            # Increment retry count
            retry_count = _chunk_retry_tracker.get(retry_key, 0)
            _chunk_retry_tracker[retry_key] = retry_count + 1

            if retry_count == 0:
                # First error - ask client to retry
                return {"result": "REPIT", "chunk_id": chunk_id_for_error}

            # Second error - mark as unrecoverable
            if retry_key in _chunk_retry_tracker:
                del _chunk_retry_tracker[retry_key]
            return {
                "result": "ACK",
                "chunk_id": chunk_id_for_error,
                "error": "UNRECOVERABLE_CHUNK",
            }

    except Exception as e:  # noqa: BLE001
        db.rollback()

        # Любая неожиданная ошибка также считается признаком битого чанка.
        if binary_data and decoder is not None:
            _persist_and_log_bad_chunk(
                decoder=decoder,
                binary_data=binary_data,
                device_id=x_device_id,
                chunk_id=x_chunk_id,
                error=e,
                context_note="outer_exception",
            )
        else:
            logger.error(
                "Unexpected error processing binary chunk (no decoder or data): %s",
                e,
                exc_info=True,
            )

        # Возвращаем ACK с ошибкой, чтобы избежать бесконечного ретрая.
        chunk_id_fallback = x_chunk_id or "unknown"
        return {"result": "ACK", "chunk_id": chunk_id_fallback, "error": "INTERNAL_ERROR"}
