#!/usr/bin/env python3
"""Assign byte codes to Android .kt files."""

import os
from pathlib import Path

def main():
    project_root = Path(__file__).parent
    android_src = project_root / "android" / "app" / "src" / "main" / "java"
    
    files = []
    for root, dirs, files_in_dir in os.walk(android_src):
        for file in files_in_dir:
            if file.endswith('.kt'):
                full_path = Path(root) / file
                rel_path = full_path.relative_to(android_src)
                files.append(file)
    
    files.sort()
    
    # Assign codes from 1 to len(files)
    file_codes = {}
    for i, file in enumerate(files, 1):
        file_codes[file] = i
    
    # Generate Kotlin code for BinaryLogger
    print("// Generated file codes for BinaryLogger.kt")
    print("private val FILE_CODES = mapOf(")
    for file, code in file_codes.items():
        print(f'    "{file}" to {code},')
    print(")")
    
    # Generate Python code for backend
    print("\n# Generated FILE_CODES for backend")
    print("FILE_CODES = {")
    for file, code in file_codes.items():
        print(f'    {code}: "{file}",')
    print("}")

if __name__ == "__main__":
    main()