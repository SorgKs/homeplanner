#!/usr/bin/env python3

import sys
from pathlib import Path
from backend.binary_chunk_decoder import BinaryChunkDecoder

def main():
    if len(sys.argv) != 2:
        print("Usage: python decode_chunk.py <chunk_file>")
        sys.exit(1)

    chunk_path = Path(sys.argv[1])
    if not chunk_path.exists():
        print(f"File not found: {chunk_path}")
        sys.exit(1)

    binary_data = chunk_path.read_bytes()

    decoder = BinaryChunkDecoder()
    header, entries, error = decoder.try_decode_for_diagnostics(binary_data)

    if header:
        print(f"Header: magic={header.magic}, version={header.format_version_major}.{header.format_version_minor}")
        print(f"Date: {header.year}-{header.month:02d}-{header.day:02d}")
        print(f"Device: {header.device_id}, Chunk ID: {header.chunk_id}")
        print(f"Dictionary revision: {header.dictionary_revision_major}.{header.dictionary_revision_minor}")
        print()

    if entries:
        print(f"Decoded {len(entries)} entries:")
        for i, entry in enumerate(entries):
            print(f"  {i+1}: {entry.timestamp} {entry.level} {entry.text}")
    else:
        print("No entries decoded")

    if error:
        print(f"Error: {error}")

if __name__ == "__main__":
    main()