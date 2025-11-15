"""Routes for serving downloadable artifacts (e.g., Android APK)."""

from pathlib import Path

from fastapi import APIRouter, HTTPException, Response
from fastapi.responses import FileResponse, JSONResponse


router = APIRouter()

REPO_ROOT = Path(__file__).resolve().parents[2]
APK_BUILD_PATH = REPO_ROOT / "android/app/build/outputs/apk/debug/app-debug.apk"
VERSION_FILE = REPO_ROOT / "VERSION"


def _resolve_version_string() -> str:
    """Read project VERSION file and return sanitized string for filenames."""
    try:
        raw = VERSION_FILE.read_text(encoding="utf-8").strip()
    except FileNotFoundError:
        raw = ""

    if not raw:
        return "unknown"

    sanitized = "".join(ch if ch.isalnum() else "_" for ch in raw)
    # Collapse consecutive underscores and strip
    while "__" in sanitized:
        sanitized = sanitized.replace("__", "_")
    sanitized = sanitized.strip("_")

    return sanitized or "unknown"


APK_DOWNLOAD_FILENAME = f"homeplanner_v{_resolve_version_string()}.apk"


def _apk_response(apk_path: Path) -> FileResponse:
    """Return FileResponse with mobile-friendly headers for APK download."""
    if not apk_path.exists():
        raise HTTPException(status_code=404, detail="APK not found. Build it first: ./gradlew :app:assembleDebug")

    file_size = apk_path.stat().st_size

    return FileResponse(
        path=str(apk_path),
        media_type="application/vnd.android.package-archive",
        filename=APK_DOWNLOAD_FILENAME,
        headers={
            "Content-Disposition": (
                f"attachment; filename={APK_DOWNLOAD_FILENAME}; "
                f"filename*=UTF-8''{APK_DOWNLOAD_FILENAME}"
            ),
            "Content-Length": str(file_size),
            "X-Content-Type-Options": "nosniff",
            "Cache-Control": "no-cache, no-store, must-revalidate",
            "Pragma": "no-cache",
            "Expires": "0",
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
    return _apk_response(APK_BUILD_PATH)


@router.get(f"/{APK_DOWNLOAD_FILENAME}", response_class=FileResponse, summary="Download Android APK (versioned filename)")
def download_versioned_apk() -> FileResponse:
    """Download APK using the versioned filename."""
    return _apk_response(APK_BUILD_PATH)


@router.get("/apk/meta", response_class=JSONResponse, summary="Get APK download metadata")
def download_apk_meta() -> JSONResponse:
    """Return metadata describing the available APK file."""
    if not APK_BUILD_PATH.exists():
        raise HTTPException(status_code=404, detail="APK not found. Build it first: ./gradlew :app:assembleDebug")

    return JSONResponse(
        {
            "filename": APK_DOWNLOAD_FILENAME,
            "filesize": APK_BUILD_PATH.stat().st_size,
        }
    )


@router.options(f"/{APK_DOWNLOAD_FILENAME}")
def options_versioned_apk() -> Response:
    """Handle OPTIONS request for CORS preflight on the versioned filename."""
    return Response(
        status_code=200,
        headers={
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, OPTIONS",
            "Access-Control-Allow-Headers": "*",
            "Access-Control-Max-Age": "3600",
        },
    )

