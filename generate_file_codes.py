#!/usr/bin/env python3
"""Generate FILE_CODES dictionary from Android .kt files."""

import os
import sys
from pathlib import Path

def get_file_code(file_name: str) -> int:
    """Get file code using the same hash as Android BinaryLogger."""
    hash_val = 0
    for char in file_name:
        hash_val = (hash_val + ord(char)) % 32767
    return hash_val

def main():
    project_root = Path(__file__).parent
    android_src = project_root / "android" / "app" / "src" / "main" / "java"
    
    file_codes = {}
    
    for root, dirs, files in os.walk(android_src):
        for file in files:
            if file.endswith('.kt'):
                full_path = Path(root) / file
                rel_path = full_path.relative_to(android_src)
                # Use just the filename as in Android BinaryLogger.getFileCode
                file_name = file
                code = get_file_code(file_name)
                if code in file_codes:
                    print(f"Hash collision: {file_name} -> {code} (already used by {file_codes[code]})")
                else:
                    file_codes[code] = file_name
    
    # Generate Python code for FILE_CODES
    print("# Generated FILE_CODES from Android .kt files")
    print("FILE_CODES_PART2 = {")
    for code in sorted(file_codes.keys()):
        print(f'    {code}: "{file_codes[code]}",')
    print("}")

if __name__ == "__main__":
    main()