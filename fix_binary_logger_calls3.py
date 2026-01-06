#!/usr/bin/env python3
"""Fix BinaryLogger.log calls by removing duplicate fileCodes from params."""

import os
import re
from pathlib import Path

def fix_file(file_path: Path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Fix emptyList<Any>(, number) to emptyList<Any>()
    content = re.sub(r'emptyList<Any>\(\s*,\s*\d+\s*\)', 'emptyList<Any>()', content)
    
    # Fix listOf<Any>(..., number) to listOf<Any>(...)
    # This is tricky, as we need to remove the last number in the list
    # Assuming the pattern is listOf<Any>(something, number)
    # We can use a function to remove the last , number
    def fix_listof(match):
        inner = match.group(1)
        # Remove trailing , number
        inner = re.sub(r',\s*\d+\s*$', '', inner)
        return f'listOf<Any>({inner})'
    
    # Find listOf<Any>(...) and fix if ends with , number
    content = re.sub(r'listOf<Any>\(([^)]+)\)', fix_listof, content)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {file_path}")

def main():
    project_root = Path(__file__).parent
    android_src = project_root / "android" / "app" / "src" / "main" / "java"
    
    for root, dirs, files in os.walk(android_src):
        for file in files:
            if file.endswith('.kt'):
                file_path = Path(root) / file
                fix_file(file_path)
    
    print("All files processed")

if __name__ == "__main__":
    main()