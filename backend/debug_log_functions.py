"""Dictionary of log message codes for decoding binary logs - Functions.

This module contains the functions for log decoding.
Used for decoding logs received from Android clients.

Version: 1.0 (matches DictionaryRevision(1, 0) in Android app)
"""

from typing import Dict
from backend.debug_log_constants_part1 import FILE_CODES, LOG_MESSAGE_DICTIONARY_PART1
from backend.debug_log_constants_part2 import LOG_MESSAGE_DICTIONARY_PART2

# Combine all dictionary parts into one
LOG_MESSAGE_DICTIONARY: Dict[int, Dict[str, any]] = {}
LOG_MESSAGE_DICTIONARY.update(LOG_MESSAGE_DICTIONARY_PART1)
LOG_MESSAGE_DICTIONARY.update(LOG_MESSAGE_DICTIONARY_PART2)

def get_message_description(code: int, context: dict = None, dictionary_revision: str = "1.0") -> str:
    """Get description for a message code.

    Args:
        code: Numeric message code from log entry
        context: Context dictionary with file and line info
        dictionary_revision: Dictionary revision (e.g., "1.0")

    Returns:
        Description text in format "file,line message" or code itself if not found
    """
    # For now, we only support version 1.0
    # In the future, this could check revision and use appropriate dictionary
    if dictionary_revision.startswith("1."):
        # Get message info from dictionary
        message_info = LOG_MESSAGE_DICTIONARY.get(code)
        if message_info:
            template = message_info["template"]

            # Удаляем старый суффикс если есть
            template = template.replace(" (файл %file%, строка %line%)", "")

            # Формат: file,line message
            if context and "file" in context and "line" in context:
                file_name = context["file"]
                line_number = context["line"]
                return f"{file_name},{line_number} {template}"
            else:
                # Fallback if no context
                return template

        return f"Unknown message code: {code}"

    # Fallback: return code itself
    return str(code)


def register_file_code(file_code: int, file_name: str):
    """Register a file code mapping dynamically.

    This allows the system to learn file mappings from logs.

    Args:
        file_code: Numeric file code
        file_name: File name
    """
    FILE_CODES[file_code] = file_name


def get_file_name(file_code: int) -> str:
    """Get file name for a file code.

    Args:
        file_code: Numeric file code

    Returns:
        File name

    Raises:
        ValueError: If file_code is not found in dictionary (битое сообщение)
    """
    if file_code in FILE_CODES:
        return FILE_CODES[file_code]
    else:
        raise ValueError(f"Unknown file code {file_code} (битое сообщение)")


def get_message_level(code: int, dictionary_revision: str = "1.0") -> str:
    """Get log level for a message code.

    Args:
        code: Numeric message code
        dictionary_revision: Dictionary revision (e.g., "1.0")

    Returns:
        Log level string (DEBUG, INFO, WARN, ERROR)
    """
    if dictionary_revision.startswith("1."):
        message_info = LOG_MESSAGE_DICTIONARY.get(code)
        if message_info:
            return message_info["level"]

    return "INFO"  # Default level