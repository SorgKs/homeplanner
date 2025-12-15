"""Router for app info endpoint."""

import logging
from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

logger = logging.getLogger(__name__)

router = APIRouter()


class AppInfo(BaseModel):
    """App information sent by Android client."""
    app_version: str
    debug_build: bool
    device_id: str
    device_info: str
    network_configured: bool


@router.post("/app-info", status_code=200)
def receive_app_info(info: AppInfo) -> dict:
    """Receive app information from Android client.

    This endpoint allows the Android app to report its version and status
    when establishing connection with the server.
    """
    logger.info(f"App info received: version={info.app_version}, debug={info.debug_build}, device={info.device_id}, network_configured={info.network_configured}")
    return {"status": "received"}