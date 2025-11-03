"""Routes for serving downloadable artifacts (e.g., Android APK)."""

from pathlib import Path

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse


router = APIRouter()


def _apk_response(apk_path: Path) -> FileResponse:
    if not apk_path.exists():
        raise HTTPException(status_code=404, detail="APK not found. Build it first: ./gradlew :app:assembleDebug")
    return FileResponse(
        path=str(apk_path),
        media_type="application/vnd.android.package-archive",
        filename="HomePlanner-debug.apk",
        headers={
            "Content-Disposition": "attachment; filename=HomePlanner-debug.apk",
            "X-Content-Type-Options": "nosniff",
        },
    )


@router.get("/apk", response_class=FileResponse, summary="Download Android APK")
def download_apk() -> FileResponse:
    """Return the built debug APK if available.

    Looks for the default Gradle output path created by :app:assembleDebug.
    """
    # __file__ is .../backend/routers/download.py → parents[2] is repo root
    apk_path = Path(__file__).resolve().parents[2] / "android/app/build/outputs/apk/debug/app-debug.apk"
    return _apk_response(apk_path)


@router.get("/latest.apk", response_class=FileResponse, summary="Download APK (friendly .apk URL)")
def download_latest_apk() -> FileResponse:
    apk_path = Path(__file__).resolve().parents[2] / "android/app/build/outputs/apk/debug/app-debug.apk"
    return _apk_response(apk_path)


