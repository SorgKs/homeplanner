"""Utility script to run the backend in background with restart protection."""

from __future__ import annotations

import argparse
import signal
import subprocess
import sys
import time
from collections import deque
from pathlib import Path
from typing import Deque

PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from backend.config import get_settings  # noqa: E402  - import after sys.path adjustment


def _build_command() -> list[str]:
    """Construct uvicorn command respecting backend settings."""

    settings = get_settings()
    command: list[str] = [
        sys.executable,
        "-m",
        "uvicorn",
        "backend.main:app",
        "--host",
        settings.host,
        "--port",
        str(settings.port),
    ]
    if settings.debug:
        command.append("--reload")
    return command


def _run_backend(max_restarts: int, window_seconds: int) -> int:
    """Run backend process with crash monitoring."""

    command = _build_command()
    restart_times: Deque[float] = deque(maxlen=max_restarts)
    process: subprocess.Popen[str] | None = None

    def terminate_process(signum: int, frame: object) -> None:  # noqa: ARG001 - required by signal
        if process and process.poll() is None:
            process.terminate()
        sys.exit(0)

    signal.signal(signal.SIGINT, terminate_process)
    signal.signal(signal.SIGTERM, terminate_process)

    while True:
        start_time = time.time()
        try:
            process = subprocess.Popen(command)
            return_code = process.wait()
        except KeyboardInterrupt:
            if process and process.poll() is None:
                process.terminate()
            return 0

        if return_code == 0:
            # Graceful shutdown, nothing to do.
            return 0

        restart_times.append(start_time)
        while restart_times and (start_time - restart_times[0]) > window_seconds:
            restart_times.popleft()

        if len(restart_times) >= max_restarts:
            print(
                f"Backend crashed {len(restart_times)} times within {window_seconds} seconds. "
                "Stopping restarts.",
                file=sys.stderr,
            )
            return return_code or 1

        print(
            f"Backend exited with code {return_code}. Restarting... "
            f"(attempt {len(restart_times) + 1}/{max_restarts})",
            file=sys.stderr,
        )
        time.sleep(1.0)


def main() -> None:
    """CLI entrypoint."""

    parser = argparse.ArgumentParser(description="Run backend with restart guard.")
    parser.add_argument(
        "--max-restarts",
        type=int,
        default=3,
        help="maximum restarts allowed within time window (default: 3)",
    )
    parser.add_argument(
        "--window-seconds",
        type=int,
        default=60,
        help="time window in seconds to count restarts (default: 60)",
    )
    args = parser.parse_args()

    runtime_dir = Path("runtime")
    if not runtime_dir.exists():
        runtime_dir.mkdir(parents=True, exist_ok=True)

    exit_code = _run_backend(args.max_restarts, args.window_seconds)
    sys.exit(exit_code)


if __name__ == "__main__":
    main()

