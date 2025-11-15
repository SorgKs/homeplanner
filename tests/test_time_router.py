"""Tests for time control API."""

from datetime import datetime, timedelta

from fastapi.testclient import TestClient

from backend.main import app


def _reset_time(client: TestClient) -> None:
    client.post("/api/v1/time/reset")


class TestTimeRouter:
    """Unit tests for /api/v1/time endpoints."""

    def test_default_state(self) -> None:
        """Default state should use real time."""
        with TestClient(app) as client:
            _reset_time(client)
            response = client.get("/api/v1/time/")
            assert response.status_code == 200
            data = response.json()
            assert data["override_enabled"] is False
            real = datetime.fromisoformat(data["real_now"])
            virtual = datetime.fromisoformat(data["virtual_now"])
            assert abs((virtual - real).total_seconds()) < 2

    def test_shift_time(self) -> None:
        """Shifting time updates virtual clock."""
        with TestClient(app) as client:
            _reset_time(client)
            response = client.post("/api/v1/time/shift", json={"days": 1})
            assert response.status_code == 200
            data = response.json()
            assert data["override_enabled"] is True
            real = datetime.fromisoformat(data["real_now"])
            virtual = datetime.fromisoformat(data["virtual_now"])
            delta = virtual - real
            assert abs(delta - timedelta(days=1)) < timedelta(seconds=2)

    def test_set_time(self) -> None:
        """Setting absolute time works."""
        with TestClient(app) as client:
            target = datetime(2030, 1, 1, 12, 0, 0)
            response = client.post("/api/v1/time/set", json={"target_datetime": target.isoformat()})
            assert response.status_code == 200
            data = response.json()
            virtual = datetime.fromisoformat(data["virtual_now"])
            assert virtual == target
            assert data["override_enabled"] is True

    def test_reset_time(self) -> None:
        """Reset disables override."""
        with TestClient(app) as client:
            client.post("/api/v1/time/shift", json={"hours": 1})
            reset_response = client.post("/api/v1/time/reset")
            assert reset_response.status_code == 200
            data = reset_response.json()
            assert data["override_enabled"] is False
            real = datetime.fromisoformat(data["real_now"])
            virtual = datetime.fromisoformat(data["virtual_now"])
            assert abs((virtual - real).total_seconds()) < 2


