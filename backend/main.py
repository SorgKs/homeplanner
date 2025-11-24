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
from backend.models import Event, Group, Task, TaskHistory, User  # noqa: F401
from backend.routers import events, groups, task_history, tasks, time_control, users
from backend.routers import download
from backend.routers import realtime


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
    # Configure logging to show INFO from our modules
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    logging.getLogger("homeplanner").setLevel(logging.INFO)
    logging.getLogger("homeplanner.realtime").setLevel(logging.INFO)
    logging.getLogger("homeplanner.tasks").setLevel(logging.INFO)
    settings = get_settings()

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
    
    app.include_router(websocket_router, prefix=api_version_path, tags=["realtime-websocket"])

    # ===== HTTP routers (ONLY HTTP, separate set) =====
    # Create separate routers ONLY for HTTP routes (no WebSocket routes)
    app.include_router(events.router, prefix=f"{api_version_path}/events", tags=["events"])
    app.include_router(tasks.router, prefix=f"{api_version_path}/tasks", tags=["tasks"])
    app.include_router(groups.router, prefix=f"{api_version_path}/groups", tags=["groups"])
    app.include_router(users.router, prefix=f"{api_version_path}/users", tags=["users"])
    app.include_router(task_history.router, prefix=api_version_path, tags=["task_history"])
    app.include_router(download.router, prefix="/download", tags=["download"])
    app.include_router(time_control.router, prefix=f"{api_version_path}/time", tags=["time"])
    
    # HTTP endpoint from realtime (separate from WebSocket route)
    http_realtime_router = APIRouter()
    
    @http_realtime_router.get("/tasks/stream/status")
    def websocket_status() -> dict:
        """HTTP endpoint to introspect current WS connections (for debugging)."""
        return ws_manager.status()
    
    app.include_router(http_realtime_router, prefix=api_version_path, tags=["realtime-http"])

    # Serve common config files (for frontend to read configuration)
    app.mount("/common/config", HTTPOnlyStaticFiles(directory="common/config", html=False), name="common-config")

    # Serve frontend static files (ONLY HTTP requests, rejects WebSocket)
    # Uses HTTPOnlyStaticFiles to ensure WebSocket requests never reach here
    app.mount("/", HTTPOnlyStaticFiles(directory="frontend", html=True), name="frontend")

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn
    import re
    from pathlib import Path
    from typing import Any

    settings = get_settings()
    
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
    ]
    
    # Configure logging to show which file triggered reload
    if settings.debug:
        # Intercept watchfiles.main logger to show file names
        watchfiles_logger = logging.getLogger("watchfiles.main")
        original_info = watchfiles_logger.info
        
        def logged_info(msg: str, *args: Any, **kwargs: Any) -> None:
            """Wrap watchfiles info to show file details when available."""
            formatted_msg = msg % args if args else msg
            # watchfiles logs "N change detected" - we need to get actual file paths
            # The file information is available in uvicorn's reload handler
            # We'll log the message and let uvicorn show the actual file names
            original_info(formatted_msg, *args, **kwargs)
        
        watchfiles_logger.info = logged_info  # type: ignore[assignment]
        
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
                    logging.warning(f"WatchFiles detected changes in '{normalized_path}'. Reloading...")
                except ValueError:
                    # If relative path fails, use absolute and normalize
                    normalized_path = str(Path(file_path)).replace("\\", "/")
                    logging.warning(f"WatchFiles detected changes in '{normalized_path}'. Reloading...")
            else:
                # If pattern doesn't match, use original message
                original_warning(msg, *args, **kwargs)
        
        uvicorn_logger.warning = logged_warning  # type: ignore[assignment]
    
    # Prepare SSL configuration if enabled
    ssl_kwargs = {}
    if settings.use_ssl:
        ssl_kwargs["ssl_keyfile"] = settings.ssl_keyfile
        ssl_kwargs["ssl_certfile"] = settings.ssl_certfile
        logging.info(f"HTTPS enabled: certfile={settings.ssl_certfile}, keyfile={settings.ssl_keyfile}")
    else:
        logging.info("HTTP mode (SSL not configured)")
    
    uvicorn.run(
        "backend.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        reload_excludes=reload_excludes if settings.debug else None,
        reload_delay=0.25,  # Small delay to batch multiple changes
        **ssl_kwargs
    )

