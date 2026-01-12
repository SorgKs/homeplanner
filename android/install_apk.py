#!/usr/bin/env python3
"""Script to install Android APK on connected device"""
import subprocess
import os
import sys

def main():
    # Set working directory
    android_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(android_dir)
    
    # Read JAVA_HOME from local.properties
    java_home = None
    local_props_path = os.path.join(android_dir, "local.properties")
    if os.path.exists(local_props_path):
        with open(local_props_path, 'r') as f:
            for line in f:
                if line.strip().startswith("java.home="):
                    java_home = line.split("=", 1)[1].strip().strip('"').strip()
                    break
    
    # Set environment
    env = os.environ.copy()
    if java_home:
        env['JAVA_HOME'] = java_home
        print(f"Using JAVA_HOME: {java_home}")
    else:
        print("JAVA_HOME not set in local.properties")

    # Run Gradle install
    gradlew = os.path.join(android_dir, "gradlew")
    print(f"Looking for gradlew at: {gradlew}")
    if not os.path.exists(gradlew):
        print(f"Error: {gradlew} not found")
        sys.exit(1)
    print("gradlew found")
    
    print("Installing Android app on connected device...")
    result = subprocess.run([gradlew, ":app:installRelease"], env=env)
    sys.exit(result.returncode)

if __name__ == "__main__":
    main()








