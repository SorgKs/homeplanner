"""Realtime updates via WebSocket."""

from __future__ import annotations

from typing import Any, Set, Dict
import json
import logging

from fastapi import APIRouter, WebSocket, WebSocketDisconnect


logger = logging.getLogger("homeplanner.realtime")


class ConnectionManager:
    """Manage WebSocket connections and broadcasting messages."""

    def __init__(self) -> None:
        self._connections: Set[WebSocket] = set()
        self._meta: Dict[WebSocket, dict[str, Any]] = {}

    async def connect(self, websocket: WebSocket) -> None:
        """Accept and register a new WebSocket connection."""
        await websocket.accept()
        self._connections.add(websocket)
        # Store client info
        try:
            client = getattr(websocket, "client", None)
            client_info = {"host": getattr(client, "host", None), "port": getattr(client, "port", None)}
        except Exception:
            client_info = {"host": None, "port": None}
        self._meta[websocket] = {"id": id(websocket), **client_info}
        logger.info("WS connect: id=%s host=%s port=%s total=%d", self._meta[websocket]["id"], self._meta[websocket]["host"], self._meta[websocket]["port"], len(self._connections))

    def disconnect(self, websocket: WebSocket) -> None:
        """Unregister a WebSocket connection."""
        self._connections.discard(websocket)
        meta = self._meta.pop(websocket, {"id": id(websocket)})
        logger.info("WS disconnect: id=%s, total=%d", meta.get("id"), len(self._connections))

    async def broadcast(self, message: dict[str, Any]) -> None:
        """Broadcast a JSON message to all active connections."""
        to_remove: list[WebSocket] = []
        payload = json.dumps(message, ensure_ascii=False)
        logger.info("WS broadcast: targets=%d, payload=%s", len(self._connections), payload)
        for ws in list(self._connections):
            try:
                await ws.send_json(message)
            except Exception:
                to_remove.append(ws)
        for ws in to_remove:
            self.disconnect(ws)

    def status(self) -> dict[str, Any]:
        """Return current connection status and metadata."""
        return {
            "active_connections": len(self._connections),
            "clients": list(self._meta.values()),
        }


router = APIRouter()
manager = ConnectionManager()


@router.websocket("/tasks/stream")
async def websocket_endpoint(websocket: WebSocket) -> None:
    """WebSocket endpoint for realtime updates.

    The server pushes JSON messages like:
    {"type": "task_update", "action": "created|updated|deleted|completed", "task_id": 123}
    """
    await manager.connect(websocket)
    try:
        while True:
            # Keep the connection alive; ignore incoming messages
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(websocket)



@router.get("/tasks/stream/status")
def websocket_status() -> dict[str, Any]:
    """HTTP endpoint to introspect current WS connections (for debugging)."""
    return manager.status()

