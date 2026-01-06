#!/usr/bin/env python3
"""Script to analyze a bad debug chunk."""

import sys
from pathlib import Path

# Add backend to path
sys.path.insert(0, str(Path(__file__).parent / "backend"))

from backend.binary_chunk_decoder import BinaryChunkDecoder

def analyze_chunk(file_path: Path):
    """Analyze a chunk file."""
    print(f"Analyzing {file_path}")
    data = file_path.read_bytes()
    print(f"File size: {len(data)} bytes")

    decoder = BinaryChunkDecoder()
    header, entries, error = decoder.try_decode_for_diagnostics(data)

    print(f"Header: {header}")
    print(f"Decoded entries: {len(entries)}")
    print(f"Error: {error}")

    if entries:
        print("\nAll entries:")
        for i, entry in enumerate(entries, 1):
            print(f"{i}: {entry.timestamp} {entry.level} {entry.text}")

    # Print hex dump around offset 583
    print(f"\nHex dump around offset 583:")
    start = max(0, 583 - 20)
    end = min(len(data), 583 + 20)
    hex_data = data[start:end]
    print(f"Offset {start:04x}: {' '.join(f'{b:02x}' for b in hex_data)}")
    print(f"ASCII:    {''.join(chr(b) if 32 <= b <= 126 else '.' for b in hex_data)}")

    # Try to find message codes
    print("\nTrying to find message codes around the error:")
    from io import BytesIO
    stream = BytesIO(data)
    decoder._decode_header(stream)  # Skip header
    pos = stream.tell()
    count = 0
    while pos < len(data) and count < 30:
        if pos >= 580 and pos <= 590:  # Around error offset
            remaining = len(data) - pos
            print(f"Position {pos}: remaining {remaining} bytes")
            if remaining >= 2:
                try:
                    code = int.from_bytes(data[pos:pos+2], byteorder='little')
                    print(f"  Possible message_code at {pos}: {code}")
                except:
                    pass
        # Skip to next possible message_code
        pos += 1
        count += 1

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python analyze_chunk.py <chunk_file>")
        sys.exit(1)

    file_path = Path(sys.argv[1])
    if not file_path.exists():
        print(f"File {file_path} does not exist")
        sys.exit(1)

    analyze_chunk(file_path)