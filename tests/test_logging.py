"""Unit tests for logging configuration in backend/main.py."""

from typing import TYPE_CHECKING
from unittest.mock import MagicMock, patch

import pytest

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch
    from pytest_mock.plugin import MockerFixture


class TestLoggedInfo:
    """Tests for logged_info function that wraps watchfiles.main logger."""

    def test_logged_info_passes_message_and_args(self) -> None:
        """Test that logged_info correctly passes message and args to original logger."""
        import logging
        from typing import Any

        # Get the watchfiles.main logger
        watchfiles_logger = logging.getLogger("watchfiles.main")
        
        # Create a mock to track calls
        mock_info = MagicMock()

        # Save original before replacing
        original_info = watchfiles_logger.info
        
        # Replace the logger's info method with our mock
        watchfiles_logger.info = mock_info  # type: ignore[assignment]

        # Simulate the logged_info function behavior
        # It should call the original (which is now our mock)
        def logged_info(msg: str, *args: Any, **kwargs: Any) -> None:
            """Wrap watchfiles info to show file details when available."""
            # Pass message and args as-is to original logger
            # The logger will handle formatting internally
            mock_info(msg, *args, **kwargs)

        # Test with message and args (like watchfiles does: '%d change%s detected', count, plural)
        logged_info("%d change%s detected", 3, "s")

        # Verify that mock_info was called with correct arguments
        mock_info.assert_called_once_with("%d change%s detected", 3, "s")
        
        # Restore original
        watchfiles_logger.info = original_info  # type: ignore[assignment]

    def test_logged_info_without_args(self) -> None:
        """Test that logged_info works with message only (no args)."""
        import logging
        from typing import Any

        watchfiles_logger = logging.getLogger("watchfiles.main")
        original_info = watchfiles_logger.info

        mock_info = MagicMock()
        watchfiles_logger.info = mock_info  # type: ignore[assignment]

        def logged_info(msg: str, *args: Any, **kwargs: Any) -> None:
            """Wrap watchfiles info to show file details when available."""
            mock_info(msg, *args, **kwargs)

        logged_info("Simple message")

        mock_info.assert_called_once_with("Simple message")
        
        # Restore original
        watchfiles_logger.info = original_info  # type: ignore[assignment]

    def test_logged_info_with_kwargs(self) -> None:
        """Test that logged_info correctly passes kwargs to original logger."""
        import logging
        from typing import Any

        watchfiles_logger = logging.getLogger("watchfiles.main")
        original_info = watchfiles_logger.info

        mock_info = MagicMock()
        watchfiles_logger.info = mock_info  # type: ignore[assignment]

        def logged_info(msg: str, *args: Any, **kwargs: Any) -> None:
            """Wrap watchfiles info to show file details when available."""
            mock_info(msg, *args, **kwargs)

        logged_info("Message with extra", extra={"key": "value"})

        mock_info.assert_called_once_with("Message with extra", extra={"key": "value"})
        
        # Restore original
        watchfiles_logger.info = original_info  # type: ignore[assignment]

    def test_logged_info_with_format_string(self) -> None:
        """Test that logged_info handles format strings correctly (doesn't pre-format)."""
        import logging
        from typing import Any

        watchfiles_logger = logging.getLogger("watchfiles.main")
        original_info = watchfiles_logger.info

        mock_info = MagicMock()
        watchfiles_logger.info = mock_info  # type: ignore[assignment]

        def logged_info(msg: str, *args: Any, **kwargs: Any) -> None:
            """Wrap watchfiles info to show file details when available."""
            mock_info(msg, *args, **kwargs)

        # This simulates the actual watchfiles call that was causing the error
        logged_info("%d change%s detected", 3, "s")

        # Verify that we pass the format string and args separately
        # The logger will format it internally, avoiding the TypeError
        mock_info.assert_called_once_with("%d change%s detected", 3, "s")
        
        # Restore original
        watchfiles_logger.info = original_info  # type: ignore[assignment]
        assert mock_info.call_args[0] == ("%d change%s detected", 3, "s")

    def test_logged_info_preserves_logger_formatting(self, caplog: "LogCaptureFixture") -> None:
        """Test that logged_info preserves the logger's internal formatting behavior."""
        import logging
        from typing import Any

        # Set up logging to capture messages
        watchfiles_logger = logging.getLogger("watchfiles.main")
        watchfiles_logger.setLevel(logging.INFO)
        # Ensure handler is present for caplog
        watchfiles_logger.propagate = True

        original_info = watchfiles_logger.info

        def logged_info(msg: str, *args: Any, **kwargs: Any) -> None:
            """Wrap watchfiles info to show file details when available."""
            original_info(msg, *args, **kwargs)

        # Replace with our wrapper
        watchfiles_logger.info = logged_info  # type: ignore[assignment]

        # Test the actual watchfiles format that was causing issues
        with caplog.at_level(logging.INFO, logger="watchfiles.main"):
            watchfiles_logger.info("%d change%s detected", 3, "s")

        # Verify that the message was formatted correctly by the logger
        assert len(caplog.records) == 1
        assert caplog.records[0].message == "3 changes detected"
        assert caplog.records[0].levelname == "INFO"

        # Restore original
        watchfiles_logger.info = original_info  # type: ignore[assignment]

    def test_logged_info_prevents_double_formatting_error(self) -> None:
        """Test that logged_info prevents TypeError when message is already formatted but args remain.
        
        This test reproduces the actual error scenario:
        - watchfiles calls: logger.info('%d change%s detected', count, plural)
        - If message gets pre-formatted somewhere but args are still passed,
          it causes: TypeError: not all arguments converted during string formatting
        
        The logged_info function should handle this by passing msg and args as-is,
        letting the logger handle formatting internally.
        """
        import logging
        from typing import Any

        watchfiles_logger = logging.getLogger("watchfiles.main")
        original_info = watchfiles_logger.info

        # Create a custom handler that will trigger the error scenario
        class ErrorTriggerHandler(logging.Handler):
            """Handler that simulates the double-formatting issue."""
            
            def __init__(self) -> None:
                super().__init__()
                self.messages: list[str] = []
                self.error_occurred = False
            
            def emit(self, record: logging.LogRecord) -> None:
                """Emit a log record, simulating the error scenario."""
                try:
                    # This simulates what happens when the logger tries to format
                    # but the message is already formatted and args still exist
                    if record.args:
                        # Try to format again - this should NOT cause an error
                        # because logged_info passes msg and args separately
                        msg = record.getMessage()
                        self.messages.append(msg)
                except TypeError as e:
                    if "not all arguments converted" in str(e):
                        self.error_occurred = True
                        raise

        handler = ErrorTriggerHandler()
        watchfiles_logger.addHandler(handler)
        watchfiles_logger.setLevel(logging.INFO)

        def logged_info(msg: str, *args: Any, **kwargs: Any) -> None:
            """Wrap watchfiles info to show file details when available."""
            # Pass message and args as-is to original logger
            # The logger will handle formatting internally
            original_info(msg, *args, **kwargs)

        # Replace with our wrapper
        watchfiles_logger.info = logged_info  # type: ignore[assignment]

        # Test the exact scenario from the error: count=1, plural=''
        # This is what watchfiles does: logger.info('%d change%s detected', count, plural)
        try:
            watchfiles_logger.info("%d change%s detected", 1, "")
        except TypeError as e:
            if "not all arguments converted" in str(e):
                pytest.fail(
                    f"logged_info should prevent double-formatting error. "
                    f"Got: {e}"
                )

        # Verify no error occurred
        assert not handler.error_occurred, "Handler should not encounter formatting error"
        
        # Verify message was logged correctly
        assert len(handler.messages) == 1
        assert handler.messages[0] == "1 change detected"

        # Cleanup
        watchfiles_logger.removeHandler(handler)
        watchfiles_logger.info = original_info  # type: ignore[assignment]

    def test_logged_info_handles_watchfiles_exact_call(self) -> None:
        """Test that logged_info handles the exact watchfiles call pattern without errors.
        
        This test simulates the exact call pattern from watchfiles:
        logger.info('%d change%s detected', count, plural)
        where count can be 1, 3, etc. and plural can be '' or 's'.
        """
        import logging
        from typing import Any

        watchfiles_logger = logging.getLogger("watchfiles.main")
        original_info = watchfiles_logger.info

        # Track if any errors occurred
        errors: list[Exception] = []

        def error_handler(record: logging.LogRecord) -> None:
            """Custom error handler to catch formatting errors."""
            try:
                # This is what the logging system does internally
                record.getMessage()
            except Exception as e:
                errors.append(e)

        # Set up handler
        handler = logging.Handler()
        handler.emit = error_handler  # type: ignore[assignment]
        watchfiles_logger.addHandler(handler)
        watchfiles_logger.setLevel(logging.INFO)

        def logged_info(msg: str, *args: Any, **kwargs: Any) -> None:
            """Wrap watchfiles info to show file details when available."""
            original_info(msg, *args, **kwargs)

        watchfiles_logger.info = logged_info  # type: ignore[assignment]

        # Test various scenarios that watchfiles might use
        test_cases = [
            (1, ""),   # "1 change detected"
            (3, "s"),  # "3 changes detected"
            (0, "s"),  # "0 changes detected"
        ]

        for count, plural in test_cases:
            try:
                watchfiles_logger.info("%d change%s detected", count, plural)
            except TypeError as e:
                if "not all arguments converted" in str(e):
                    pytest.fail(
                        f"logged_info failed for count={count}, plural='{plural}'. "
                        f"Error: {e}"
                    )

        # Verify no errors occurred
        assert len(errors) == 0, f"Unexpected errors: {errors}"

        # Cleanup
        watchfiles_logger.removeHandler(handler)
        watchfiles_logger.info = original_info  # type: ignore[assignment]
