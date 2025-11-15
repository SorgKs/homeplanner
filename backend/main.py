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
    from backend.routers.realtime import ConnectionManager
    
    websocket_router = APIRouter()
    ws_manager = ConnectionManager()
    
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

    # Serve frontend static files (ONLY HTTP requests, rejects WebSocket)
    # Uses HTTPOnlyStaticFiles to ensure WebSocket requests never reach here
    app.mount("/", HTTPOnlyStaticFiles(directory="frontend", html=True), name="frontend")

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    settings = get_settings()
    uvicorn.run(
        "backend.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
    )

