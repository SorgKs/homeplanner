"""Binary chunk decoder for Android debug logs.

This module decodes binary log chunks sent from Android clients
according to the LOGGING_FORMAT.md specification.
"""

from __future__ import annotations

import logging
import struct
from dataclasses import dataclass
from datetime import datetime, timedelta
from io import BytesIO
from typing import Any

from backend.debug_log_dictionary import LOG_MESSAGE_DICTIONARY, get_message_level

logger = logging.getLogger(__name__)


@dataclass
class ChunkHeader:
    """Header information from a binary log chunk."""

    magic: bytes  # Should be b"HDBG"
    format_version_major: int
    format_version_minor: int
    year: int
    month: int
    day: int
    dictionary_revision_major: int
    dictionary_revision_minor: int
    device_id: str | None
    chunk_id: int  # uint64, required for format v1.1+


@dataclass
class DecodedLogEntry:
    """Decoded log entry with text representation."""

    timestamp: datetime
    level: str
    text: str


class BinaryChunkDecoder:
    """Decoder for binary log chunks."""

    MAGIC = b"HDBG"
    MAX_INTERVALS_PER_DAY = 8_640_000  # 10ms intervals in a day

    def __init__(self) -> None:
        """Initialize the decoder."""
        # Decoder is currently stateless; this is reserved for future options.
        return

    def decode_chunk(self, data: bytes) -> tuple[ChunkHeader, list[DecodedLogEntry]]:
        """Decode a complete binary chunk.

        This method is strict: любая ошибка чтения отдельной записи
        приводит к выбросу :class:`ValueError`. Это позволяет
        вызывающему коду отличать битые чанки и не сохранять
        частично декодированные данные.

        Args:
            data: Raw binary chunk data.

        Returns:
            Tuple of (header, list of decoded log entries).

        Raises:
            ValueError: If the chunk format is invalid or a log entry
                cannot be fully decoded.
        """
        stream = BytesIO(data)

        # Decode header
        header = self._decode_header(stream)

        # Validate format version
        if header.format_version_major != 1:
            raise ValueError(
                f"Unsupported format version: {header.format_version_major}.{header.format_version_minor}"
            )

        # For v1.1+, chunk_id is required
        if header.format_version_minor >= 1 and header.chunk_id is None:
            raise ValueError("chunk_id is required for format v1.1+")

        # Get dictionary revision
        dict_revision = f"{header.dictionary_revision_major}.{header.dictionary_revision_minor}"

        # Create base date for relative timestamps
        base_date = datetime(header.year, header.month, header.day)

        # Decode all log entries strictly
        entries: list[DecodedLogEntry] = []
        while stream.tell() < len(data):
            entry_offset = stream.tell()
            try:
                entry = self._decode_entry(stream, base_date, dict_revision)
                entries.append(entry)
            except struct.error as exc:
                raise ValueError(
                    f"Failed to decode log entry at byte offset {entry_offset}; "
                    f"decoded_entries={len(entries)}"
                ) from exc

        return header, entries

    def _decode_header(self, stream: BytesIO) -> ChunkHeader:
        """Decode chunk header.

        Args:
            stream: Binary stream positioned at header start.

        Returns:
            Decoded header.

        Raises:
            ValueError: If header format is invalid.
        """
        # Read magic (4 bytes)
        magic = stream.read(4)
        if magic != self.MAGIC:
            raise ValueError(f"Invalid magic: expected {self.MAGIC}, got {magic}")

        # Read format version (2 bytes)
        format_version_major = struct.unpack("<B", stream.read(1))[0]
        format_version_minor = struct.unpack("<B", stream.read(1))[0]

        # Read date (4 bytes)
        year = struct.unpack("<H", stream.read(2))[0]
        month = struct.unpack("<B", stream.read(1))[0]
        day = struct.unpack("<B", stream.read(1))[0]

        # Read dictionary revision (2 bytes)
        dictionary_revision_major = struct.unpack("<B", stream.read(1))[0]
        dictionary_revision_minor = struct.unpack("<B", stream.read(1))[0]

        # Read device ID (1 byte length + N bytes UTF-8)
        device_id_length = struct.unpack("<B", stream.read(1))[0]
        device_id: str | None = None
        if device_id_length > 0:
            device_id_bytes = stream.read(device_id_length)
            device_id = device_id_bytes.decode("utf-8")

        # Read chunk ID (8 bytes, little-endian) - only for v1.1+
        chunk_id: int | None = None
        if format_version_minor >= 1:
            chunk_id_bytes = stream.read(8)
            if len(chunk_id_bytes) == 8:
                chunk_id = struct.unpack("<Q", chunk_id_bytes)[0]

        return ChunkHeader(
            magic=magic,
            format_version_major=format_version_major,
            format_version_minor=format_version_minor,
            year=year,
            month=month,
            day=day,
            dictionary_revision_major=dictionary_revision_major,
            dictionary_revision_minor=dictionary_revision_minor,
            device_id=device_id,
            chunk_id=chunk_id,
        )

    def _decode_entry(
        self, stream: BytesIO, base_date: datetime, dict_revision: str
    ) -> DecodedLogEntry:
        """Decode a single log entry.

        Args:
            stream: Binary stream positioned at entry start.
            base_date: Base date for relative timestamp.
            dict_revision: Dictionary revision string.

        Returns:
            Decoded log entry.

        Raises:
            struct.error: If entry is incomplete or corrupted.
        """
        # Read message code (2 bytes, unsigned short, little-endian)
        message_code = struct.unpack("<H", stream.read(2))[0]

        # Read timestamp (3 bytes, unsigned 24-bit, little-endian)
        ts_bytes = stream.read(3)
        if len(ts_bytes) < 3:
            raise struct.error("Incomplete timestamp")
        intervals = struct.unpack("<I", ts_bytes + b"\x00")[0]  # Add padding byte

        # Calculate absolute timestamp
        timestamp = base_date + timedelta(milliseconds=intervals * 10)

        # Read context data (depends on message code)
        # For now, we'll use simple schema from dictionary
        context_values = self._decode_context(stream, message_code, dict_revision)

        # Get message template and level from dictionary
        message_info = LOG_MESSAGE_DICTIONARY.get(message_code)
        if message_info:
            text = message_info["template"]
            level = message_info["level"]
        else:
            text = f"Unknown message code: {message_code}"
            level = "INFO"

        # TODO: Replace placeholders in text with context values
        # For now, just append context as string if present
        if context_values:
            context_str = ", ".join(f"{k}={v}" for k, v in context_values.items())
            text = f"{text} [{context_str}]"

        return DecodedLogEntry(timestamp=timestamp, level=level, text=text)

    def _decode_context(
        self, stream: BytesIO, message_code: int, dict_revision: str
    ) -> dict[str, Any]:
        """Decode context data for a log entry.

        Args:
            stream: Binary stream positioned at context start.
            message_code: Message code to determine context schema.
            dict_revision: Dictionary revision string.

        Returns:
            Dictionary of context values.
        """
        # Get context schema from dictionary
        message_info = LOG_MESSAGE_DICTIONARY.get(message_code)
        if not message_info:
            return {}

        context_schema = message_info.get("context_schema", [])
        if not context_schema:
            return {}

        # Decode values according to schema
        context: dict[str, Any] = {}
        for field in context_schema:
            field_name = field["name"]
            field_type = field["type"]

            if field_type == "int":
                value = struct.unpack("<i", stream.read(4))[0]
            elif field_type == "long":
                value = struct.unpack("<q", stream.read(8))[0]
            elif field_type == "float":
                value = struct.unpack("<f", stream.read(4))[0]
            elif field_type == "double":
                value = struct.unpack("<d", stream.read(8))[0]
            elif field_type == "bool":
                value = struct.unpack("<B", stream.read(1))[0] != 0
            elif field_type == "string":
                length = struct.unpack("<B", stream.read(1))[0]
                value = stream.read(length).decode("utf-8")
            else:
                raise ValueError(f"Unknown field type: {field_type}")

            context[field_name] = value

        return context

    def try_decode_for_diagnostics(
        self, data: bytes
    ) -> tuple[ChunkHeader | None, list[DecodedLogEntry], str | None]:
        """Best-effort decode of a chunk for diagnostics.

        Этот метод предназначен только для диагностики битых чанков и
        никогда не выбрасывает исключения. Он пытается декодировать
        как можно больше записей и возвращает краткое описание
        первой возникшей ошибки.

        Args:
            data: Raw binary chunk data.

        Returns:
            Tuple of:
                - decoded header or None, если заголовок не удалось прочитать;
                - список успешно декодированных записей;
                - строка с описанием ошибки (или None, если ошибок не было).
        """
        stream = BytesIO(data)
        header: ChunkHeader | None = None
        entries: list[DecodedLogEntry] = []
        error_description: str | None = None

        try:
            header = self._decode_header(stream)
            dict_revision = (
                f"{header.dictionary_revision_major}.{header.dictionary_revision_minor}"
            )
            base_date = datetime(header.year, header.month, header.day)
        except Exception as exc:  # noqa: BLE001
            error_description = f"Failed to decode header: {exc}"
            logger.error(error_description, exc_info=True)
            return None, [], error_description

        while stream.tell() < len(data):
            entry_offset = stream.tell()
            try:
                entry = self._decode_entry(stream, base_date, dict_revision)
                entries.append(entry)
            except Exception as exc:  # noqa: BLE001
                error_description = (
                    f"Failed to decode entry at byte offset {entry_offset}: {exc}"
                )
                logger.error(error_description, exc_info=True)
                break

        return header, entries, error_description
