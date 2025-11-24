"""Routes for serving downloadable artifacts (e.g., Android APK)."""

from pathlib import Path

from fastapi import APIRouter, HTTPException, Response
from fastapi.responses import FileResponse, JSONResponse


router = APIRouter()

REPO_ROOT = Path(__file__).resolve().parents[2]


def _resolve_version_string() -> str:
    """Get Android component version and return sanitized string for filenames.
    
    Version format: MAJOR.MINOR.PATCH (3 digits).
    PATCH is read from android/version.json.
    """
    from common.versioning import MAJOR_VERSION, MINOR_VERSION
    import json
    
    major = MAJOR_VERSION()
    minor = MINOR_VERSION()
    
    # Read PATCH from android/version.json
    version_json_path = REPO_ROOT / "android" / "version.json"
    patch = 0
    if version_json_path.exists():
        try:
            version_data = json.loads(version_json_path.read_text(encoding="utf-8"))
            patch = version_data.get("patch", 0)
        except (ValueError, OSError, KeyError, json.JSONDecodeError):
            patch = 0
    
    # Compose version: MAJOR.MINOR.PATCH (3 digits)
    version = f"{major}.{minor}.{patch}"
    # Sanitize version string for filename (replace dots with underscores)
    sanitized = version.replace(".", "_")
    return sanitized


APK_DOWNLOAD_FILENAME = f"homeplanner_v{_resolve_version_string()}.apk"
APK_BUILD_PATH = REPO_ROOT / "android/app/build/outputs/apk/debug" / APK_DOWNLOAD_FILENAME


def _find_apk_file() -> Path | None:
    """Find APK file in build directory.
    
    First tries to find the versioned APK file, then falls back to any APK file.
    Returns None if no APK found.
    """
    apk_dir = REPO_ROOT / "android/app/build/outputs/apk/debug"
    if not apk_dir.exists():
        return None
    
    # First, try the versioned filename
    if APK_BUILD_PATH.exists():
        return APK_BUILD_PATH
    
    # Fallback: find any APK file in the directory
    apk_files = list(apk_dir.glob("*.apk"))
    if apk_files:
        # Return the most recent one
        return max(apk_files, key=lambda p: p.stat().st_mtime)
    
    return None


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

    Looks for the versioned APK file created by :app:assembleDebug.
    APK is renamed to homeplanner_v{version}.apk format according to build.gradle.kts.
    Falls back to any APK file if versioned one is not found.
    """
    apk_path = _find_apk_file()
    if apk_path is None:
        raise HTTPException(status_code=404, detail="APK not found. Build it first: ./gradlew :app:assembleDebug")
    return _apk_response(apk_path)


@router.get(f"/{APK_DOWNLOAD_FILENAME}", response_class=FileResponse, summary="Download Android APK (versioned filename)")
def download_versioned_apk() -> FileResponse:
    """Download APK using the versioned filename."""
    apk_path = _find_apk_file()
    if apk_path is None:
        raise HTTPException(status_code=404, detail="APK not found. Build it first: ./gradlew :app:assembleDebug")
    return _apk_response(apk_path)


@router.get("/apk/meta", response_class=JSONResponse, summary="Get APK download metadata")
def download_apk_meta() -> JSONResponse:
    """Return metadata describing the available APK file."""
    apk_path = _find_apk_file()
    if apk_path is None:
        raise HTTPException(status_code=404, detail="APK not found. Build it first: ./gradlew :app:assembleDebug")

    return JSONResponse(
        {
            "filename": apk_path.name,
            "filesize": apk_path.stat().st_size,
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

