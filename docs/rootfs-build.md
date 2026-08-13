# Rootfs build

PIRT ships an arm64 Ubuntu rootfs as a single `tar.gz` blob inside the APK assets. This document describes how to rebuild it from public sources instead of maintaining a hand-modified binary chain.

> This is currently a repeatable build recipe, not a byte-for-byte reproducible build. Ubuntu Base and Node.js are checksum-pinned, and the Pi package version is pinned. Ubuntu apt dependencies still come from the current repository state, npm transitive dependencies are not locked by a committed lockfile, and archive metadata is not normalized. A later rebuild can therefore produce a different SHA-256.

## What gets built

Starting from the official [Ubuntu Base 24.04 arm64](https://cdimages.ubuntu.com/ubuntu-base/releases/24.04/release/) tarball (SHA256 verified), `tools/build-rootfs.sh` uses `qemu-user-static` + `chroot` to install:

- Git, Python 3
- Minimal XFCE (`xfce4`, `--no-install-recommends`)
- TigerVNC, noVNC, websockify
- Node.js (pinned tarball from nodejs.org)
- Pi coding agent (`@earendil-works/pi-coding-agent`, pinned version)

Pinned top-level versions live in `tools/rootfs.env`. Android-side bridge scripts (`pirt-control-bridge.mjs`) are **not** baked into the blob; `RuntimeInstaller` copies them from app assets during install.

## Host requirements

- Ubuntu/Debian **x86_64** (24.04 tested; must match `noble`/`arm64` ports)
- `sudo`, `curl`, `tar`, network for apt/npm during build
- ~8 GB free disk, ~2 GB RAM

## Build

```bash
chmod +x tools/build-rootfs.sh
tools/build-rootfs.sh build/pirt-rootfs-arm64.tar.gz
```

The script prints JSON with `size` and `sha256`. Update:

1. `app/src/main/assets/runtime/manifest.json` (`ubuntu.version`, `size`, `sha256`)
2. `app/src/main/java/io/github/zixt233/pirt/runtime/RuntimeInstaller.kt` (`RuntimeArtifacts.ubuntuArm64`)
3. Copy/rename the artifact to `app/src/main/assets/runtime/ubuntu-base-24.04.4-base-arm64.blob`

## Remote build example

```bash
scp tools/rootfs.env tools/build-rootfs.sh ubuntu@your-host:/tmp/pirt/
ssh ubuntu@your-host 'cd /tmp/pirt && bash build-rootfs.sh /tmp/pirt-rootfs.tar.gz'
scp ubuntu@your-host:/tmp/pirt-rootfs.tar.gz build/
```

## Deprecated

Do **not** use `build_graphics_rootfs.py` / `.mjs` tar concatenation on Windows. It produces concatenated tar streams that GNU `tar` and some readers treat as truncated/corrupt. Use `build-rootfs.sh` instead.
