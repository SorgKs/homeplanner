"""Routes for serving downloadable artifacts (e.g., Android APK)."""

from pathlib import Path

from fastapi import APIRouter, HTTPException, Response
from fastapi.responses import FileResponse


router = APIRouter()


def _apk_response(apk_path: Path) -> FileResponse:
    """Return FileResponse with mobile-friendly headers for APK download."""
    if not apk_path.exists():
        raise HTTPException(status_code=404, detail="APK not found. Build it first: ./gradlew :app:assembleDebug")
    
    # Get file size for Content-Length header
    file_size = apk_path.stat().st_size
    
    return FileResponse(
        path=str(apk_path),
        media_type="application/vnd.android.package-archive",
        filename="HomePlanner-debug.apk",
        headers={
            "Content-Disposition": "attachment; filename=HomePlanner-debug.apk; filename*=UTF-8''HomePlanner-debug.apk",
            "Content-Length": str(file_size),
            "X-Content-Type-Options": "nosniff",
            "Cache-Control": "no-cache, no-store, must-revalidate",
            "Pragma": "no-cache",
            "Expires": "0",
            # Allow all origins for mobile browsers (CORS is handled by middleware, but explicit headers help)
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, OPTIONS",
            "Access-Control-Allow-Headers": "*",
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
    """Download APK with mobile-friendly headers.
    
    This endpoint serves the debug APK with headers optimized for mobile browsers.
    """
    apk_path = Path(__file__).resolve().parents[2] / "android/app/build/outputs/apk/debug/app-debug.apk"
    return _apk_response(apk_path)


@router.options("/latest.apk")
def options_latest_apk() -> Response:
    """Handle OPTIONS request for CORS preflight."""
    return Response(
        status_code=200,
        headers={
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, OPTIONS",
            "Access-Control-Allow-Headers": "*",
            "Access-Control-Max-Age": "3600",
        },
    )


