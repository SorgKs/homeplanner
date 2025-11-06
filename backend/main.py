"""Main FastAPI application entry point."""

from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from backend.config import get_settings
from backend.database import engine, init_db
from backend.models import Event, Group, Task, TaskHistory  # noqa: F401
from backend.routers import events, groups, task_history, tasks
from backend.routers import download
from backend.routers import realtime


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup and shutdown events."""
    # Startup
    init_db()
    yield
    # Shutdown
    engine.dispose()


def create_app() -> FastAPI:
    """Create and configure FastAPI application."""
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
        version="0.1.0",
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

    # Include routers
    app.include_router(events.router, prefix="/api/v1/events", tags=["events"])
    app.include_router(tasks.router, prefix="/api/v1/tasks", tags=["tasks"])
    app.include_router(groups.router, prefix="/api/v1/groups", tags=["groups"])
    app.include_router(task_history.router, prefix="/api/v1", tags=["task_history"])
    app.include_router(download.router, prefix="/download", tags=["download"])
    app.include_router(realtime.router, tags=["realtime"])  # /ws

    # Serve frontend static files
    # Mounts the 'frontend' directory at root, serving index.html by default
    app.mount("/", StaticFiles(directory="frontend", html=True), name="frontend")

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

