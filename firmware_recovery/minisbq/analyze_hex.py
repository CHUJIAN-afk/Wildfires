#!/usr/bin/env python3
"""Validate and inspect an Intel HEX Cortex-M firmware image."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
from pathlib import Path


def parse_ihex(path: Path) -> tuple[dict[int, int], dict]:
    memory: dict[int, int] = {}
    upper = 0
    eof_seen = False
    start_linear = None
    counts: dict[int, int] = {}

    for line_number, raw in enumerate(path.read_text(encoding="ascii").splitlines(), 1):
        line = raw.strip()
        if not line:
            continue
        if not line.startswith(":"):
            raise ValueError(f"line {line_number}: missing ':'")
        try:
            record = bytes.fromhex(line[1:])
        except ValueError as exc:
            raise ValueError(f"line {line_number}: invalid hex") from exc
        if len(record) < 5 or len(record) != record[0] + 5:
            raise ValueError(f"line {line_number}: invalid record length")
        if sum(record) & 0xFF:
            raise ValueError(f"line {line_number}: checksum mismatch")
        length = record[0]
        offset = int.from_bytes(record[1:3], "big")
        kind = record[3]
        payload = record[4 : 4 + length]
        counts[kind] = counts.get(kind, 0) + 1

        if kind == 0x00:
            base = upper + offset
            for index, value in enumerate(payload):
                address = base + index
                previous = memory.get(address)
                if previous is not None and previous != value:
                    raise ValueError(f"line {line_number}: conflicting byte at 0x{address:08X}")
                memory[address] = value
        elif kind == 0x01:
            eof_seen = True
        elif kind == 0x02:
            upper = int.from_bytes(payload, "big") << 4
        elif kind == 0x04:
            upper = int.from_bytes(payload, "big") << 16
        elif kind == 0x05:
            start_linear = int.from_bytes(payload, "big")
        elif kind == 0x03:
            pass
        else:
            raise ValueError(f"line {line_number}: unsupported record type {kind}")

    if not eof_seen:
        raise ValueError("missing EOF record")
    if not memory:
        raise ValueError("HEX contains no data")
    return memory, {"record_counts": counts, "start_linear": start_linear}


def contiguous_segments(memory: dict[int, int]) -> list[tuple[int, int]]:
    addresses = sorted(memory)
    segments: list[tuple[int, int]] = []
    start = previous = addresses[0]
    for address in addresses[1:]:
        if address != previous + 1:
            segments.append((start, previous + 1))
            start = address
        previous = address
    segments.append((start, previous + 1))
    return segments


def u32(memory: dict[int, int], address: int) -> int | None:
    try:
        return struct.unpack("<I", bytes(memory[address + i] for i in range(4)))[0]
    except KeyError:
        return None


def extract_strings(blob: bytes, base: int, minimum: int = 4) -> list[dict]:
    result = []
    for match in re.finditer(rb"[\x20-\x7e]{%d,}" % minimum, blob):
        text = match.group().decode("ascii")
        result.append({"address": base + match.start(), "text": text})
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()

    raw = args.input.read_bytes()
    memory, metadata = parse_ihex(args.input)
    segments = contiguous_segments(memory)
    image_start = segments[0][0]
    image_end = segments[-1][1]
    blob = bytes(memory.get(address, 0xFF) for address in range(image_start, image_end))

    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "minisbq.bin").write_bytes(blob)

    vectors = []
    vector_names = [
        "initial_sp", "reset", "nmi", "hardfault", "memmanage", "busfault",
        "usagefault", "reserved_7", "reserved_8", "reserved_9", "reserved_10",
        "svcall", "debugmon", "reserved_13", "pendsv", "systick",
    ]
    # Include all apparent vector entries until normal code/data begins. 128 is a safe
    # inspection ceiling; consumers can decide the exact MCU-specific IRQ count.
    for index in range(128):
        value = u32(memory, image_start + 4 * index)
        if value is None:
            break
        name = vector_names[index] if index < len(vector_names) else f"irq_{index - 16}"
        vectors.append({"index": index, "address": image_start + 4 * index, "name": name, "value": value})

    report = {
        "input": str(args.input),
        "input_size": len(raw),
        "input_sha256": hashlib.sha256(raw).hexdigest(),
        "hex_valid": True,
        "segments": [{"start": a, "end_exclusive": b, "size": b - a} for a, b in segments],
        "image_start": image_start,
        "image_end_exclusive": image_end,
        "spanned_size": len(blob),
        "programmed_bytes": len(memory),
        "metadata": metadata,
        "vectors": vectors,
        "strings": extract_strings(blob, image_start),
    }
    (args.output_dir / "analysis.json").write_text(json.dumps(report, indent=2), encoding="utf-8")

    print(f"valid Intel HEX: yes")
    print(f"sha256: {report['input_sha256']}")
    for segment in segments:
        print(f"segment: 0x{segment[0]:08X}-0x{segment[1] - 1:08X} ({segment[1] - segment[0]} bytes)")
    print(f"programmed bytes: {len(memory)}; spanned image: {len(blob)} bytes")
    print(f"initial SP: 0x{vectors[0]['value']:08X}")
    print(f"reset vector: 0x{vectors[1]['value']:08X}")
    print(f"ASCII strings: {len(report['strings'])}")


if __name__ == "__main__":
    main()
