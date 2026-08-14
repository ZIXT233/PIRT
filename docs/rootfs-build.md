# Rootfs build

PIRT ships an arm64 Debian rootfs as a single `tar.gz` blob inside the APK assets.

> This is a repeatable build recipe, not a byte-for-byte reproducible build. Node.js is checksum-pinned and the Pi package version is pinned. Apt dependencies come from the current Debian repository state.

## What gets built

`tools/build-rootfs.sh` runs `debootstrap --variant=minbase` for Debian 13.6 (`trixie`/`arm64`), then installs:

- Git, Python 3, `xdg-utils` (`xdg-open`)
- Minimal XFCE (`xfce4`, `--no-install-recommends`)
- TigerVNC, noVNC, websockify
- Node.js (pinned tarball from nodejs.org)
- Pi coding agent (`@earendil-works/pi-coding-agent`, pinned version)

Pinned top-level versions live in `tools/rootfs.env`. Android-side bridge scripts (`pirt-control-bridge.mjs`) are **not** baked into the blob.

## Host requirements

- Debian or another Debian-based Linux environment (x86_64 or arm64; ARM Mac via Docker `linux/arm64` works)
- `sudo`, `curl`, `tar`, `debootstrap`, network for apt/npm
- ~8 GB free disk, ~2 GB RAM
- Non-aarch64 hosts need `qemu-user-static` (installed by the script)

## Build

```bash
chmod +x tools/build-rootfs.sh
tools/build-rootfs.sh build/pirt-rootfs-arm64.tar.gz
```

The script prints JSON with `size` and `sha256`. Update:

1. `app/src/main/assets/runtime/manifest.json` (`debian.version`, `size`, `sha256`)
2. `RuntimeInstaller.kt` (`RuntimeArtifacts.debianArm64`)
3. Copy/rename the artifact to `app/src/main/assets/runtime/debian-13.6.0-base-arm64.blob`

## Deprecated

Do **not** use `build_graphics_rootfs.py` / `.mjs` tar concatenation. Use `build-rootfs.sh` instead.
