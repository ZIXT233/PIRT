#!/usr/bin/env python3
"""Overlay pinned Ubuntu arm64 GUI packages onto the bundled rootfs tarball.

Package data tar streams are concatenated without unpacking them on Windows, preserving
Linux owners, modes and symlinks from both the existing rootfs and Debian packages.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import lzma
import re
import struct
import urllib.request
import time
from pathlib import Path

import zstandard


PORTS = "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/"
SUITES = ("noble", "noble-updates", "noble-security")
COMPONENTS = ("main", "universe")
TARGETS = (
    "tigervnc-standalone-server",
    "tigervnc-tools",
    "xfce4",
    "dbus-x11",
    "xterm",
    "xfonts-base",
    "novnc",
    "websockify",
)


def fetch(url: str) -> bytes:
    print(f"download {url}", flush=True)
    last_error = None
    for attempt in range(5):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "PIRT-rootfs-builder/1"})
            with urllib.request.urlopen(request, timeout=90) as response:
                return response.read()
        except Exception as error:
            last_error = error
            time.sleep(1 + attempt * 2)
    raise last_error


def parse_control(raw: str) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for paragraph in raw.split("\n\n"):
        fields: dict[str, str] = {}
        current = None
        for line in paragraph.splitlines():
            if line.startswith((" ", "\t")) and current:
                fields[current] += " " + line.strip()
            elif ":" in line:
                current, value = line.split(":", 1)
                fields[current] = value.strip()
        if "Package" in fields and "Filename" in fields:
            result.append(fields)
    return result


def dependency_names(value: str) -> list[list[str]]:
    groups = []
    for group in value.split(","):
        alternatives = []
        for item in group.split("|"):
            name = re.split(r"\s|\(", item.strip(), maxsplit=1)[0]
            name = name.split(":", 1)[0]
            name = re.sub(r"\s*\[[^]]+\]", "", name)
            if name:
                alternatives.append(name)
        if alternatives:
            groups.append(alternatives)
    return groups


def resolve(packages: dict[str, dict[str, str]], providers: dict[str, str]) -> list[dict[str, str]]:
    selected: dict[str, dict[str, str]] = {}
    queue = list(TARGETS)
    while queue:
        requested = queue.pop(0)
        real = requested if requested in packages else providers.get(requested)
        if not real or real in selected:
            if not real:
                print(f"warning: unresolved dependency {requested}")
            continue
        package = packages[real]
        selected[real] = package
        for field in ("Pre-Depends", "Depends"):
            for alternatives in dependency_names(package.get(field, "")):
                choice = next((name for name in alternatives if name in packages or name in providers), None)
                if choice:
                    queue.append(choice)
                else:
                    print(f"warning: no candidate for {' | '.join(alternatives)}")
    print(f"resolved {len(selected)} packages")
    return list(selected.values())


def ar_members(data: bytes) -> dict[str, bytes]:
    if not data.startswith(b"!<arch>\n"):
        raise ValueError("not a debian ar archive")
    offset = 8
    result = {}
    while offset + 60 <= len(data):
        header = data[offset : offset + 60]
        name = header[:16].decode("ascii").strip().rstrip("/")
        size = int(header[48:58].decode("ascii").strip())
        offset += 60
        result[name] = data[offset : offset + size]
        offset += size + (size % 2)
    return result


def data_tar(deb: bytes) -> bytes:
    members = ar_members(deb)
    for name, payload in members.items():
        if name == "data.tar.xz":
            return lzma.decompress(payload)
        if name == "data.tar.gz":
            return gzip.decompress(payload)
        if name == "data.tar.zst":
            with zstandard.ZstdDecompressor().stream_reader(io.BytesIO(payload)) as reader:
                return reader.read()
        if name == "data.tar":
            return payload
    raise ValueError("deb has no supported data archive")


def copy_tar_without_end(source, target) -> None:
    """Copy a tar stream while dropping only its trailing zero records."""
    zero = bytes(512)
    pending_zero_records = 0
    while True:
        block = source.read(512)
        if not block:
            break
        if len(block) != 512:
            raise ValueError("tar stream is not aligned to 512-byte records")
        if block == zero:
            pending_zero_records += 1
        else:
            if pending_zero_records:
                target.write(zero * pending_zero_records)
                pending_zero_records = 0
            target.write(block)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--cache", required=True, type=Path)
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)

    packages: dict[str, dict[str, str]] = {}
    providers: dict[str, str] = {}
    for suite in SUITES:
        for component in COMPONENTS:
            url = f"{PORTS}dists/{suite}/{component}/binary-arm64/Packages.gz"
            cache = args.cache / f"{suite}-{component}-Packages.gz"
            if not cache.exists():
                cache.write_bytes(fetch(url))
            for package in parse_control(gzip.decompress(cache.read_bytes()).decode("utf-8")):
                packages[package["Package"]] = package
                for provided in dependency_names(package.get("Provides", "")):
                    if provided:
                        providers[provided[0]] = package["Package"]

    chosen = resolve(packages, providers)
    debs: list[Path] = []
    for package in chosen:
        filename = package["Filename"]
        deb_path = args.cache / Path(filename).name
        if not deb_path.exists():
            deb_path.write_bytes(fetch(PORTS + filename))
        payload = deb_path.read_bytes()
        expected = package.get("SHA256")
        if expected and hashlib.sha256(payload).hexdigest() != expected:
            raise ValueError(f"checksum mismatch for {package['Package']}")
        debs.append(deb_path)
        print(f"overlay {package['Package']} {package.get('Version', '')}", flush=True)

    with args.output.open("wb") as raw_target:
        with gzip.GzipFile(fileobj=raw_target, mode="wb", compresslevel=6, mtime=0) as target:
            with gzip.open(args.input, "rb") as source:
                copy_tar_without_end(source, target)
            for deb_path in debs:
                copy_tar_without_end(io.BytesIO(data_tar(deb_path.read_bytes())), target)
            target.write(bytes(1024))

    digest = hashlib.sha256(args.output.read_bytes()).hexdigest()
    print(json.dumps({"size": args.output.stat().st_size, "sha256": digest, "packages": len(chosen)}))


if __name__ == "__main__":
    main()
