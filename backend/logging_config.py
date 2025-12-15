"""Logging configuration for backend application."""

from __future__ import annotations

import logging
import logging.handlers
from pathlib import Path


# Module-level flag to track if logging has been configured
_logging_configured = False


def setup_logging(log_dir: Path | None = None, debug: bool = False) -> None:
    """Configure logging to write to both console and file.

    This function is idempotent - multiple calls will only configure logging once.

    Args:
        log_dir: Directory for log files. If None, uses 'logs' in project root.
        debug: If True, sets DEBUG level, otherwise INFO.
    """
    global _logging_configured

    # If already configured, skip
    if _logging_configured:
        return

    # Determine log directory
    if log_dir is None:
        log_dir = Path(__file__).parent.parent / "logs"
    
    # Create log directory if it doesn't exist
    log_dir.mkdir(parents=True, exist_ok=True)
    
    # Set log level
    log_level = logging.DEBUG if debug else logging.INFO
    
    # Root logger configuration
    root_logger = logging.getLogger()
    root_logger.setLevel(log_level)
    
    # Clear existing handlers to avoid duplicates
    root_logger.handlers.clear()
    
    # Console handler (outputs to stderr)
    console_handler = logging.StreamHandler()
    console_handler.setLevel(log_level)
    console_formatter = logging.Formatter(
        "%(asctime)s %(levelname)-5s %(name)s %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )
    console_handler.setFormatter(console_formatter)
    
    # File handler with rotation (rotates daily, keeps 30 days)
    log_file = log_dir / "homeplanner.log"
    file_handler = logging.handlers.TimedRotatingFileHandler(
        filename=str(log_file),
        when="midnight",
        interval=1,
        backupCount=30,
        encoding="utf-8"
    )
    file_handler.setLevel(log_level)
    file_formatter = logging.Formatter(
        "%(asctime)s %(levelname)-5s %(name)s %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )
    file_handler.setFormatter(file_formatter)
    
    # Error log file handler (only ERROR and above)
    error_log_file = log_dir / "homeplanner_errors.log"
    error_file_handler = logging.handlers.TimedRotatingFileHandler(
        filename=str(error_log_file),
        when="midnight",
        interval=1,
        backupCount=90,  # Keep error logs longer
        encoding="utf-8"
    )
    error_file_handler.setLevel(logging.ERROR)
    error_file_handler.setFormatter(file_formatter)
    
    # Add handlers to root logger
    root_logger.addHandler(console_handler)
    root_logger.addHandler(file_handler)
    root_logger.addHandler(error_file_handler)
    
    # Set levels for specific loggers
    logging.getLogger("homeplanner").setLevel(log_level)
    logging.getLogger("homeplanner.realtime").setLevel(log_level)
    logging.getLogger("homeplanner.tasks").setLevel(log_level)
    logging.getLogger("homeplanner.system").setLevel(log_level)
    
    # Reduce noise from third-party libraries
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)
    logging.getLogger("uvicorn").setLevel(logging.INFO if not debug else logging.DEBUG)
    
    # Mark as configured
    _logging_configured = True
    
    # Log that logging is configured
    logger = logging.getLogger("homeplanner.system")
    logger.info(f"Logging configured: level={logging.getLevelName(log_level)}, log_dir={log_dir}")
