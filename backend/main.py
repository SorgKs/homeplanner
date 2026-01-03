"""Main FastAPI application entry point."""

from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI, APIRouter, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from starlette.staticfiles import StaticFiles as StarletteStaticFiles
from starlette.routing import Route, WebSocketRoute, Router
from starlette.types import ASGIApp, Receive, Scope, Send

from common.versioning import compose_component_version

from backend.config import get_settings
from backend.database import engine, init_db
from backend.logging_config import setup_logging
from backend.models import DebugLog, Event, Group, Task, TaskHistory, User  # noqa: F401
from backend.routers import events, groups, task_history, tasks, time_control, users
from backend.routers import download
from backend.routers import realtime, debug_logs

# System logger for application-level messages
system_logger = logging.getLogger("homeplanner.system")


class HTTPOnlyStaticFiles(StarletteStaticFiles):
    """StaticFiles that handles ONLY HTTP requests, rejects WebSocket.

    This ensures WebSocket requests never reach StaticFiles and are handled
    only by WebSocket routers registered separately.
    """

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        """Handle request, rejecting WebSocket connections."""
        if scope["type"] != "http":
            # Reject non-HTTP requests (WebSocket, etc.)
            # FastAPI should handle WebSocket before reaching here,
            # but this ensures they never reach StaticFiles
            from starlette.responses import Response
            response = Response("Not Found", status_code=404)
            await response(scope, receive, send)
            return
        # Handle HTTP requests normally
        await super().__call__(scope, receive, send)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup and shutdown events."""
    # Startup
    init_db()
    yield
    # Shutdown
    engine.dispose()


def create_app() -> FastAPI:
    """Create and configure FastAPI application with separate router sets.

    FastAPI automatically separates WebSocket and HTTP requests at routing level:
    - WebSocket requests (scope["type"] == "websocket") are checked only against
      @router.websocket() routes
    - HTTP requests (scope["type"] == "http") are checked only against
      @router.get(), @router.post(), etc. routes

    Returns:
        FastAPI app with separate router sets for WebSocket and HTTP.
    """
    settings = get_settings()
    
    # Configure logging to write to both console and file
    setup_logging(debug=settings.debug)

    app = FastAPI(
        title="HomePlanner API",
        description="API для планировщика и напоминалки домашних задач",
        version=compose_component_version("backend"),
        lifespan=lifespan,
    )

    # CORS middleware
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # ===== WebSocket routers (ONLY WebSocket, separate set) =====
    # Create separate router ONLY for WebSocket routes (no HTTP routes)
    # Use the same manager instance from realtime module so tasks router can broadcast to it
    from backend.routers.realtime import manager as ws_manager
    
    websocket_router = APIRouter()
    
    @websocket_router.websocket("/tasks/stream")
    async def websocket_endpoint(websocket: WebSocket) -> None:
        """WebSocket endpoint for realtime updates."""
        logger = logging.getLogger("homeplanner.realtime")
        
        # Check origin for WebSocket (CORS middleware doesn't handle WebSocket)
        origin = websocket.headers.get("origin")
        client_host = websocket.client.host if websocket.client else "unknown"
        logger.info(f"WebSocket connection attempt from {client_host}, origin: {origin}")
        
        # Allow connection regardless of origin for now (for debugging)
        # In production, you might want to check origin against allowed list
        await ws_manager.connect(websocket)
        
        try:
            while True:
                await websocket.receive_text()
        except WebSocketDisconnect:
            ws_manager.disconnect(websocket)
        except Exception as e:
            logger.error(f"WebSocket error: {e}", exc_info=True)
            ws_manager.disconnect(websocket)
    
    # Get API version from settings
    api_version_path = settings.api_version_path

    # Support both current and previous API versions for backward compatibility
    supported_api_versions = ["/api/v0.2", "/api/v0.3"]
    for version_path in supported_api_versions:
        app.include_router(websocket_router, prefix=version_path, tags=["realtime-websocket"])

    # ===== HTTP routers (ONLY HTTP, separate set) =====
    # Create separate routers ONLY for HTTP routes (no WebSocket routes)
    # Register routers for all supported API versions for backward compatibility
    for version_path in supported_api_versions:
        app.include_router(events.router, prefix=f"{version_path}/events", tags=["events"])
        app.include_router(tasks.router, prefix=f"{version_path}/tasks", tags=["tasks"])
        app.include_router(groups.router, prefix=f"{version_path}/groups", tags=["groups"])
        app.include_router(users.router, prefix=f"{version_path}/users", tags=["users"])
        app.include_router(task_history.router, prefix=version_path, tags=["task_history"])
        app.include_router(time_control.router, prefix=f"{version_path}/time", tags=["time"])
        app.include_router(debug_logs.router, prefix=version_path, tags=["debug-logs"])

    app.include_router(download.router, prefix="/download", tags=["download"])
    
    # HTTP endpoint from realtime (separate from WebSocket route)
    http_realtime_router = APIRouter()
    
    @http_realtime_router.get("/tasks/stream/status")
    def websocket_status() -> dict:
        """HTTP endpoint to introspect current WS connections (for debugging)."""
        return ws_manager.status()
    
    app.include_router(http_realtime_router, prefix=api_version_path, tags=["realtime-http"])

    # Serve common config files (for frontend to read configuration)
    from pathlib import Path
    common_config_path = Path(__file__).parent.parent / "common" / "config"
    app.mount("/common/config", HTTPOnlyStaticFiles(directory=str(common_config_path), html=False), name="common-config")

    # Serve frontend static files (ONLY HTTP requests, rejects WebSocket)
    # Uses HTTPOnlyStaticFiles to ensure WebSocket requests never reach here
    frontend_path = Path(__file__).parent.parent / "frontend"
    app.mount("/", HTTPOnlyStaticFiles(directory=str(frontend_path), html=True), name="frontend")

    return app


app = create_app()
application = app  # Alias for uvicorn auto-discovery


if __name__ == "__main__":
    import uvicorn
    import re
    from pathlib import Path
    from typing import Any

    settings = get_settings()
    
    # Ensure logging is configured before starting server
    # (setup_logging is idempotent, so safe to call multiple times)
    setup_logging(debug=settings.debug)
    
    # Validate SSL configuration if provided
    if settings.ssl_certfile or settings.ssl_keyfile:
        if not settings.ssl_certfile or not settings.ssl_keyfile:
            raise ValueError(
                "Both ssl_certfile and ssl_keyfile must be set in settings.toml "
                "to enable HTTPS. Leave both empty for HTTP mode."
            )
        cert_path = Path(settings.ssl_certfile)
        key_path = Path(settings.ssl_keyfile)
        if not cert_path.exists():
            raise FileNotFoundError(f"SSL certificate not found: {cert_path}")
        if not key_path.exists():
            raise FileNotFoundError(f"SSL key file not found: {key_path}")
    
    # Configure reload exclusions to prevent constant restarts
    # Ignore Android build outputs, logs, and other temporary files
    # Note: uvicorn uses fnmatch patterns - only file patterns work reliably
    reload_excludes = [
        "*.apk",
        "*.log",
        "*.db",
        "*.db-journal",
        "*.prof",
        "*.pyc",
        "*.txt",  # Exclude test output files
        "logs/*",  # Exclude entire logs directory
        "backend/logs/*",  # Explicitly exclude backend logs
        "backend/backend.log",  # Exclude our log file from watch
        "backend/backend_errors.log",  # Exclude error log
    ]
    
    # Configure logging to show which file triggered reload
    if settings.debug:
        # Intercept watchfiles.main logger to show file names
        watchfiles_logger = logging.getLogger("watchfiles.main")
        original_debug = watchfiles_logger.debug
        
        def logged_debug(msg: str, *args: Any, **kwargs: Any) -> None:
            """Wrap watchfiles debug to filter out log file changes."""
            formatted_msg = msg % args if args else msg
            if "backend.log" in formatted_msg or "backend_errors.log" in formatted_msg:
                return
            original_debug(msg, *args, **kwargs)
        
        watchfiles_logger.debug = logged_debug  # type: ignore[assignment]
        
        # Intercept uvicorn.error logger to extract and display file names
        uvicorn_logger = logging.getLogger("uvicorn.error")
        original_warning = uvicorn_logger.warning

        def logged_warning(msg: str, *args: Any, **kwargs: Any) -> None:
            """Wrap uvicorn warning to extract and display file names from reload messages."""
            formatted_msg = msg % args if args else msg

            # uvicorn logs messages like:
            # "WatchFiles detected changes in 'backend/routers/download.py'. Reloading..."
            # We extract the file name and format it nicely
            match = re.search(r"WatchFiles detected changes in '([^']+)'", formatted_msg)
            if match:
                file_path = match.group(1)
                # Convert to relative path for cleaner output and normalize path separators
                try:
                    rel_path = Path(file_path).relative_to(Path.cwd())
                    # Normalize path separators to forward slashes for consistency
                    normalized_path = str(rel_path).replace("\\", "/")
                    system_logger.warning(f"WatchFiles detected changes in '{normalized_path}'. Reloading...")
                except ValueError:
                    # If relative path fails, use absolute and normalize
                    normalized_path = str(Path(file_path)).replace("\\", "/")
                    system_logger.warning(f"WatchFiles detected changes in '{normalized_path}'. Reloading...")
            else:
                # If pattern doesn't match, use original message
                original_warning(msg, *args, **kwargs)

        uvicorn_logger.warning = logged_warning  # type: ignore[assignment]
    
    # Prepare SSL configuration if enabled
    ssl_kwargs = {}
    if settings.use_ssl:
        ssl_kwargs["ssl_keyfile"] = settings.ssl_keyfile
        ssl_kwargs["ssl_certfile"] = settings.ssl_certfile
        system_logger.info(f"HTTPS enabled: certfile={settings.ssl_certfile}, keyfile={settings.ssl_keyfile}")
    else:
        system_logger.info("HTTP mode (SSL not configured)")

    # Custom log config for uvicorn to include timestamps
    log_config = {
        "version": 1,
        "disable_existing_loggers": False,
        "formatters": {
            "default": {
                "format": "%(asctime)s %(levelname)s %(name)s %(message)s",
                "datefmt": "%Y-%m-%d %H:%M:%S",
            },
        },
        "handlers": {
            "default": {
                "formatter": "default",
                "class": "logging.StreamHandler",
                "stream": "ext://sys.stderr",
            },
        },
        "loggers": {
            "uvicorn": {"handlers": ["default"], "level": "INFO", "propagate": False},
            "uvicorn.error": {"handlers": ["default"], "level": "INFO", "propagate": False},
            "uvicorn.access": {"handlers": ["default"], "level": "INFO", "propagate": False},
            "uvicorn.asgi": {"handlers": ["default"], "level": "INFO", "propagate": False},
        },
    }

    uvicorn.run(
        "backend.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        reload_dirs=["backend", "frontend", "common", "scripts", "tests"] if settings.debug else None,
        reload_excludes=reload_excludes if settings.debug else None,
        reload_delay=0.25,  # Small delay to batch multiple changes
        log_config=log_config,  # Use our custom log config
        **ssl_kwargs
    )

