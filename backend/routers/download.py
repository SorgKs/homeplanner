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


def _get_apk_filename() -> str:
    """Get current APK filename based on current version (dynamic)."""
    return f"homeplanner_v{_resolve_version_string()}.apk"


def _get_apk_build_path() -> Path:
    """Get current APK build path based on current version (dynamic)."""
    return REPO_ROOT / "android/app/build/outputs/apk/debug" / _get_apk_filename()


def _find_apk_file() -> Path | None:
    """Find APK file in build directory.

    First tries debug directory, then release directory.
    First tries to find the versioned APK file, then falls back to any APK file.
    Returns None if no APK found.
    """
    for build_type in ["debug", "release"]:
        apk_dir = REPO_ROOT / "android/app/build/outputs/apk" / build_type
        if not apk_dir.exists():
            continue

        # First, try the versioned filename (read dynamically)
        apk_build_path = _get_apk_build_path().parent.parent / build_type / _get_apk_filename()
        if apk_build_path.exists():
            return apk_build_path

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
    # Use actual filename from path, or current version if different
    apk_filename = apk_path.name if apk_path.name.endswith('.apk') else _get_apk_filename()

    return FileResponse(
        path=str(apk_path),
        media_type="application/vnd.android.package-archive",
        filename=apk_filename,
        headers={
            "Content-Disposition": (
                f"attachment; filename={apk_filename}; "
                f"filename*=UTF-8''{apk_filename}"
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


@router.get("/{filename:path}", response_class=FileResponse, summary="Download Android APK (versioned filename)")
def download_versioned_apk(filename: str) -> FileResponse:
    """Download APK using the versioned filename.
    
    Supports any APK filename pattern (e.g., homeplanner_v0_2_78.apk).
    """
    # Validate that it's an APK filename
    if not filename.endswith('.apk') or not filename.startswith('homeplanner_v'):
        raise HTTPException(status_code=404, detail="Invalid APK filename")
    
    apk_path = _find_apk_file()
    if apk_path is None:
        raise HTTPException(status_code=404, detail="APK not found. Build it first: ./gradlew :app:assembleDebug")
    return _apk_response(apk_path)


@router.options("/{filename:path}")
def options_versioned_apk(filename: str) -> Response:
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

