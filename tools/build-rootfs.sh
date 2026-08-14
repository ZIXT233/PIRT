#!/usr/bin/env bash
# Reproducible PIRT arm64 rootfs from Debian 13 (trixie) minbase + pinned apt/npm packages.
# Requires: Debian or another Debian-based Linux environment (or Docker), sudo, curl, tar, debootstrap.
# On non-aarch64 hosts also needs qemu-user-static.
#
# Usage:
#   tools/build-rootfs.sh [output.tar.gz]
#
# Output is a single tar.gz blob for app/src/main/assets/runtime/ (see docs/rootfs-build.md).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
if [[ -f "$SCRIPT_DIR/rootfs.env" ]]; then
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/rootfs.env"
elif [[ -f "$ROOT_DIR/tools/rootfs.env" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT_DIR/tools/rootfs.env"
else
  echo "rootfs.env not found beside build-rootfs.sh or in tools/" >&2
  exit 1
fi

OUTPUT="${1:-$ROOT_DIR/build/pirt-rootfs-${PIRT_ROOTFS_VERSION}-arm64.tar.gz}"
WORK="${PIRT_BUILD_DIR:-$ROOT_DIR/build/rootfs-work}"
ROOTFS="$WORK/rootfs"
CACHE="$WORK/cache"
STAGING="$(dirname "$OUTPUT")"

APT_PACKAGES=(
  ca-certificates
  curl
  git
  python3
  xfce4
  tigervnc-standalone-server
  tigervnc-tools
  dbus-x11
  xfce4-terminal
  xfonts-base
  novnc
  websockify
  xdg-utils
)

log() { printf '==> %s\n' "$*"; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "missing command: $1" >&2; exit 1; }
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

fetch() {
  local url="$1" dest="$2" expected="${3:-}"
  if [[ -f "$dest" && -n "$expected" && "$(sha256_file "$dest")" == "$expected" ]]; then
    log "cache hit $(basename "$dest")"
    return
  fi
  log "download $url"
  curl -fsSL --retry 5 --retry-delay 2 -o "$dest.part" "$url"
  mv "$dest.part" "$dest"
  if [[ -n "$expected" ]]; then
    actual="$(sha256_file "$dest")"
    [[ "$actual" == "$expected" ]] || {
      echo "checksum mismatch for $(basename "$dest"): expected $expected got $actual" >&2
      exit 1
    }
  fi
}

# On aarch64 hosts (e.g. ARM Mac Docker linux/arm64), chroot natively — no qemu.
NATIVE_AARCH64=0
[[ "$(uname -m)" == "aarch64" ]] && NATIVE_AARCH64=1

run_chroot() {
  if [[ "$NATIVE_AARCH64" -eq 1 ]]; then
    sudo chroot "$ROOTFS" "$@"
  else
    sudo chroot "$ROOTFS" /usr/bin/qemu-aarch64-static "$@"
  fi
}

setup_host() {
  need_cmd curl
  need_cmd tar
  need_cmd sudo
  export DEBIAN_FRONTEND=noninteractive
  sudo apt-get update -qq
  local host_pkgs=(ca-certificates debootstrap debian-archive-keyring xz-utils)
  if [[ "$NATIVE_AARCH64" -eq 1 ]]; then
    sudo apt-get install -y -qq "${host_pkgs[@]}" >/dev/null
    log "native aarch64 host — skipping qemu-user-static"
  else
    sudo apt-get install -y -qq "${host_pkgs[@]}" qemu-user-static binfmt-support >/dev/null
    sudo update-binfmts --enable qemu-aarch64 >/dev/null 2>&1 || true
  fi
  need_cmd debootstrap
}

mount_chroot() {
  sudo mount --bind /dev "$ROOTFS/dev"
  sudo mount --bind /proc "$ROOTFS/proc"
  sudo mount --bind /sys "$ROOTFS/sys"
  sudo mount --bind /run "$ROOTFS/run" 2>/dev/null || true
  sudo cp /etc/resolv.conf "$ROOTFS/etc/resolv.conf"
}

umount_chroot() {
  sudo umount -lf "$ROOTFS/run" 2>/dev/null || true
  sudo umount -lf "$ROOTFS/sys" 2>/dev/null || true
  sudo umount -lf "$ROOTFS/proc" 2>/dev/null || true
  sudo umount -lf "$ROOTFS/dev" 2>/dev/null || true
}

bootstrap_debian() {
  log "debootstrap Debian ${DEBIAN_RELEASE} (${DEBIAN_CODENAME}/${DEBIAN_ARCH}) minbase"
  local args=(--arch="$DEBIAN_ARCH" --variant=minbase "$DEBIAN_CODENAME" "$ROOTFS" "$DEBIAN_MIRROR")
  if [[ "$NATIVE_AARCH64" -ne 1 ]]; then
    # First stage only; second stage runs under qemu-aarch64-static.
    sudo debootstrap --foreign "${args[@]}"
    sudo cp /usr/bin/qemu-aarch64-static "$ROOTFS/usr/bin/"
    mount_chroot
    trap umount_chroot EXIT
    run_chroot /debootstrap/debootstrap --second-stage
  else
    sudo debootstrap "${args[@]}"
    mount_chroot
    trap umount_chroot EXIT
  fi
  test -x "$ROOTFS/bin/bash" || { echo "debootstrap rootfs is incomplete" >&2; exit 1; }
}

main() {
  mkdir -p "$CACHE" "$STAGING"
  rm -rf "$ROOTFS"
  mkdir -p "$ROOTFS"

  setup_host
  bootstrap_debian

  log "configure apt sources"
  cat <<EOF | sudo tee "$ROOTFS/etc/apt/sources.list" >/dev/null
deb ${DEBIAN_MIRROR} ${DEBIAN_CODENAME} main contrib non-free non-free-firmware
deb ${DEBIAN_MIRROR} ${DEBIAN_CODENAME}-updates main contrib non-free non-free-firmware
deb ${DEBIAN_SECURITY} ${DEBIAN_CODENAME}-security main contrib non-free non-free-firmware
EOF
  # Skip Translation-* indexes and recommends by default.
  sudo mkdir -p "$ROOTFS/etc/apt/apt.conf.d"
  cat <<'EOF' | sudo tee "$ROOTFS/etc/apt/apt.conf.d/99pirt-build" >/dev/null
Acquire::Languages "none";
APT::Get::Install-Recommends "false";
APT::Get::Install-Suggests "false";
Acquire::http::Pipeline-Depth "10";
EOF

  log "install pinned system packages (Depends only, no Recommends)"
  run_chroot apt-get update -qq
  run_chroot apt-get install -y -qq --no-install-recommends "${APT_PACKAGES[@]}"

  log "install Node.js ${NODE_VERSION} (arm64 official tarball)"
  local node_tgz="$CACHE/node-v${NODE_VERSION}-linux-arm64.tar.xz"
  fetch "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-arm64.tar.xz" \
    "$node_tgz" "$NODE_TARBALL_SHA256"
  sudo tar -xJf "$node_tgz" -C "$ROOTFS/usr/local" --strip-components=1

  log "install ${PI_PACKAGE}@${PI_VERSION}"
  run_chroot npm install -g "${PI_PACKAGE}@${PI_VERSION}" --ignore-scripts --omit=dev

  log "sanity check"
  run_chroot test -x /usr/local/bin/node
  run_chroot test -x /usr/local/bin/pi
  run_chroot test -x /usr/bin/startxfce4
  run_chroot test -x /usr/bin/Xtigervnc
  run_chroot test -x /usr/bin/xdg-open
  run_chroot test -x /usr/bin/xfce4-terminal
  run_chroot test -e /usr/share/novnc/vnc.html
  run_chroot test -x /usr/bin/websockify

  log "trim caches"
  run_chroot apt-get clean
  sudo rm -rf "$ROOTFS/var/lib/apt/lists"/* "$ROOTFS/var/cache/apt/archives"/*
  sudo rm -rf "$ROOTFS/root/.npm" "$ROOTFS/tmp"/*
  sudo mkdir -p "$ROOTFS/tmp" "$ROOTFS/workspace"
  sudo chmod 1777 "$ROOTFS/tmp"
  echo "$PIRT_ROOTFS_VERSION" | sudo tee "$ROOTFS/.pirt-rootfs-version" >/dev/null

  umount_chroot
  trap - EXIT

  log "pack $(basename "$OUTPUT")"
  sudo tar -C "$ROOTFS" --numeric-owner --owner=0 --group=0 -czf "$OUTPUT" .
  sudo chown "$(id -u):$(id -g)" "$OUTPUT" 2>/dev/null || true

  log "verify archive"
  tar -tzf "$OUTPUT" >/dev/null
  test -x "$ROOTFS/bin/bash"

  local digest archive_size
  digest="$(sha256_file "$OUTPUT")"
  archive_size="$(wc -c < "$OUTPUT" | tr -d '[:space:]')"
  printf '{"version":"%s","size":%s,"sha256":"%s"}\n' "$PIRT_ROOTFS_VERSION" "$archive_size" "$digest"
}

main "$@"
