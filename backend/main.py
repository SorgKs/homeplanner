"""Main FastAPI application entry point."""

import json
from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from backend.config import get_settings
from pathlib import Path

from common.versioning import (
    compose_component_version,
    get_component_patch,
    get_project_version,
    get_version_config,
)
from common.networking import get_network_config


def _write_if_changed(path: Path, content: str) -> None:
    """Write content to file only if it has changed."""

    if path.exists():
        current = path.read_text(encoding="utf-8")
        if current == content:
            return
    path.write_text(content, encoding="utf-8")


def _ensure_frontend_version_assets() -> None:
    """Generate project version assets for frontend without triggering reload loops."""

    frontend_dir = Path(__file__).resolve().parent.parent / "frontend"
    project_json = frontend_dir / "version.project.json"
    project_content = json.dumps(get_version_config(), ensure_ascii=False, indent=2) + "\n"
    _write_if_changed(project_json, project_content)

    component_json = frontend_dir / "version.json"
    if not component_json.exists():
        component_content = json.dumps(
            {"patch": get_component_patch("frontend")},
            ensure_ascii=False,
            indent=2,
        )
        _write_if_changed(component_json, component_content + "\n")

    version_js = frontend_dir / "version.js"
    frontend_version = compose_component_version("frontend", get_component_patch("frontend"))
    version_js_content = (
        "(function(){\n"
        f"  window.HP_PROJECT_VERSION = '{get_project_version()}';\n"
        f"  window.HP_FRONTEND_VERSION = '{frontend_version}';\n"
        "})();\n"
    )
    _write_if_changed(version_js, version_js_content)

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
    settings = get_settings()
    _ensure_frontend_version_assets()
    log_file = settings.backend_log_file
    log_file.parent.mkdir(parents=True, exist_ok=True)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
        handlers=[
            logging.FileHandler(log_file, encoding="utf-8"),
            logging.StreamHandler(),
        ],
    )
    logging.getLogger("homeplanner").setLevel(logging.INFO)
    logging.getLogger("homeplanner.realtime").setLevel(logging.INFO)
    logging.getLogger("homeplanner.tasks").setLevel(logging.INFO)

    app = FastAPI(
        title="HomePlanner API",
        description=(
            "API для планировщика и напоминалки домашних задач "
            f"(релиз {get_project_version()})"
        ),
        version=settings.backend_version,
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
    api_prefix = settings.api_prefix
    app.include_router(events.router, prefix=f"{api_prefix}/events", tags=["events"])
    app.include_router(tasks.router, prefix=f"{api_prefix}/tasks", tags=["tasks"])
    app.include_router(groups.router, prefix=f"{api_prefix}/groups", tags=["groups"])
    app.include_router(task_history.router, prefix=api_prefix, tags=["task_history"])
    app.include_router(download.router, prefix="/download", tags=["download"])
    app.include_router(realtime.router, prefix=api_prefix, tags=["realtime"])

    # Serve frontend static files
    # Mounts the 'frontend' directory at root, serving index.html by default
    class LoggedStaticFiles(StaticFiles):
        async def __call__(self, scope, receive, send):  # type: ignore[override]
            if scope.get("type") != "http":
                logging.getLogger("homeplanner").warning(
                    "StaticFiles received non-http scope: type=%s path=%s", scope.get("type"), scope.get("path")
                )
                await send(
                    {
                        "type": "websocket.close",
                        "code": 1002,
                    }
                )
                return
            await super().__call__(scope, receive, send)

    app.mount("/", LoggedStaticFiles(directory="frontend", html=True), name="frontend")

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    settings = get_settings()
    uvicorn_kwargs = {
        "host": settings.host,
        "port": settings.port,
    }
    if settings.debug:
        uvicorn_kwargs["reload"] = True
        uvicorn_kwargs["reload_dirs"] = ["backend", "common"]
        uvicorn_kwargs["reload_excludes"] = [
            "frontend/*",
            "frontend/**/*",
            "runtime/logs/*",
            "runtime/logs/**/*",
            "runtime/db/*",
            "runtime/db/**/*",
        ]
    uvicorn.run(
        "backend.main:app",
        **uvicorn_kwargs,
    )

