# ADB utilities for Android testing

import subprocess
import time
from typing import Optional, List


class ADBUtils:
    """Utility class for ADB operations."""

    def __init__(self, device_id: Optional[str] = None):
        self.device_id = device_id
        self.base_cmd = ["adb"]
        if device_id:
            self.base_cmd.extend(["-s", device_id])

    def run_command(self, cmd: List[str], timeout: int = 30) -> str:
        """Run ADB command and return output."""
        full_cmd = self.base_cmd + cmd
        print(f"Running ADB command: {' '.join(full_cmd)}")
        try:
            result = subprocess.run(
                full_cmd,
                capture_output=True,
                text=True,
                timeout=timeout,
                check=True
            )
            print(f"ADB command success: output length {len(result.stdout)}")
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            print(f"ADB command failed: {e.stderr}")
            raise RuntimeError(f"ADB command failed: {e.stderr}")

    def install_apk(self, apk_path: str) -> None:
        """Install APK on device."""
        print(f"Installing APK: {apk_path}")
        self.run_command(["install", "-r", apk_path])
        print("APK installed successfully")

    def uninstall_app(self, package: str) -> None:
        """Uninstall app from device."""
        self.run_command(["uninstall", package])

    def start_app(self, package: str, activity: str = ".MainActivity") -> None:
        """Start app activity."""
        self.run_command([
            "shell", "am", "start", "-n", f"{package}/{activity}"
        ])

    def stop_app(self, package: str) -> None:
        """Force stop app."""
        self.run_command(["shell", "am", "force-stop", package])
        # Also kill the process if still running
        try:
            pid_output = self.run_command(["shell", "pidof", package])
            if pid_output.strip():
                self.run_command(["shell", "kill", pid_output.strip()])
        except:
            pass

    def clear_app_data(self, package: str) -> None:
        """Clear app data."""
        self.run_command(["shell", "pm", "clear", package])

    def get_logcat(self, grep: Optional[str] = None, lines: int = 100) -> str:
        """Get recent logcat output."""
        cmd = ["logcat", "-d", "-t", str(lines)]
        if grep:
            # Use shell to pipe to grep
            full_cmd_str = " ".join(self.base_cmd + cmd) + f" | grep {grep}"
            try:
                result = subprocess.run(
                    full_cmd_str,
                    shell=True,
                    capture_output=True,
                    text=True,
                    timeout=30,
                    check=True
                )
                return result.stdout.strip()
            except subprocess.CalledProcessError as e:
                raise RuntimeError(f"ADB command failed: {e.stderr}")
        else:
            return self.run_command(cmd)

    def wait_for_device(self, timeout: int = 30) -> None:
        """Wait for device to be ready."""
        start_time = time.time()
        while time.time() - start_time < timeout:
            try:
                self.run_command(["shell", "echo", "device_ready"])
                return
            except:
                time.sleep(1)
        raise TimeoutError("Device not ready")