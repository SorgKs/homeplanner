"""Router for app info and client config endpoints."""

import logging
from typing import Optional

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from backend.config import get_settings

logger = logging.getLogger(__name__)

router = APIRouter()


class AppInfo(BaseModel):
    """App information sent by Android client."""
    app_version: str
    debug_build: bool
    device_id: str
    device_info: str
    network_configured: bool


class ClientConfig(BaseModel):
    """Configuration sent to frontend client."""
    websocket_url: str
    api_version_path: str


@router.post("/app-info", status_code=200)
def receive_app_info(info: AppInfo) -> dict:
    """Receive app information from Android client.

    This endpoint allows the Android app to report its version and status
    when establishing connection with the server.
    """
    logger.info(f"App info received: version={info.app_version}, debug={info.debug_build}, device={info.device_id}, network_configured={info.network_configured}")
    return {"status": "received"}


@router.get("/client-config", response_model=ClientConfig)
def get_client_config(request: Request) -> ClientConfig:
    """Get client configuration for frontend.

    This endpoint provides configuration data that frontend needs to connect
    to the server, including WebSocket URL and API paths.
    """
    settings = get_settings()

    # Build WebSocket URL based on current request
    scheme = "wss" if request.url.scheme == "https" else "ws"
    host = request.url.hostname or settings.host
    port = request.url.port or settings.port

    # Use configured port only if it's not the default for the scheme
    if (scheme == "wss" and port == 443) or (scheme == "ws" and port == 80):
        port_str = ""
    else:
        port_str = f":{port}"

    websocket_url = f"{scheme}://{host}{port_str}{settings.api_version_path}/tasks/stream"

    return ClientConfig(
        websocket_url=websocket_url,
        api_version_path=settings.api_version_path
    )


