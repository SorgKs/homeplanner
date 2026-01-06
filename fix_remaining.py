#!/usr/bin/env python3
"""Fix remaining BinaryLogger issues."""

import os
import re
from pathlib import Path

def fix_file(file_path: Path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Fix emptyList<Any>(, number) to emptyList<Any>()
    content = re.sub(r'emptyList<Any>\(\s*,\s*\d+\s*\)', 'emptyList<Any>()', content)
    
    # Fix listOf<Any>(..., number, number) to listOf<Any>(..., number)
    # Remove trailing , number if there are two numbers
    # Assuming pattern: listOf<Any>( something , number , number )
    content = re.sub(r'listOf<Any>\(([^)]*?),\s*\d+\s*,\s*\d+\s*\)', lambda m: f'listOf<Any>({m.group(1)}, {m.group(1).split(",")[-1].strip()})', content)
    
    # Simpler: remove trailing , number if the list ends with two numbers
    # But it's hard. Let's use multiple passes.
    
    # First, replace listOf<Any>(..., number, number) with listOf<Any>(..., number)
    # Find and replace
    def fix_listof(match):
        inner = match.group(1)
        # Split by comma, remove last if it's number
        parts = [p.strip() for p in inner.split(',')]
        if len(parts) >= 2 and re.match(r'\d+', parts[-1]) and re.match(r'\d+', parts[-2]):
            parts = parts[:-1]  # remove last number
        return f'listOf<Any>({",".join(parts)})'
    content = re.sub(r'listOf<Any>\(([^)]+)\)', fix_listof, content)
    
    # Fix duplicate fileCode in log(..., fileCode, fileCode)
    content = re.sub(r'BinaryLogger\.getInstance\(\)\?\.log\(([^)]+),\s*\d+\s*,\s*\d+\s*\)', lambda m: f'BinaryLogger.getInstance()?.log({m.group(1)}, {m.group(1).split(",")[-1].strip()})', content)
    
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
    
    print("Done")

if __name__ == "__main__":
    main()