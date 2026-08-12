#!/usr/bin/env bash
# Reproducible PIRT arm64 rootfs from official Ubuntu Base + pinned apt/npm packages.
# Requires: Ubuntu/Debian x86_64 host, sudo, curl, tar, qemu-user-static.
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
  xterm
  xfonts-base
  novnc
  websockify
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
  if [[ "$NATIVE_AARCH64" -eq 1 ]]; then
    sudo apt-get install -y -qq ca-certificates >/dev/null
    log "native aarch64 host — skipping qemu-user-static"
  else
    sudo apt-get install -y -qq qemu-user-static binfmt-support ca-certificates >/dev/null
    sudo update-binfmts --enable qemu-aarch64 >/dev/null 2>&1 || true
  fi
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

main() {
  mkdir -p "$CACHE" "$STAGING"
  rm -rf "$ROOTFS"
  mkdir -p "$ROOTFS"

  setup_host

  local base="$CACHE/ubuntu-base-${UBUNTU_RELEASE}-${UBUNTU_ARCH}.tar.gz"
  fetch "$UBUNTU_BASE_URL" "$base" "$UBUNTU_BASE_SHA256"

  log "extract official Ubuntu Base ${UBUNTU_RELEASE} (${UBUNTU_ARCH})"
  tar -xzf "$base" -C "$ROOTFS"
  test -x "$ROOTFS/bin/bash" || { echo "extracted rootfs is incomplete" >&2; exit 1; }

  if [[ "$NATIVE_AARCH64" -ne 1 ]]; then
    sudo cp /usr/bin/qemu-aarch64-static "$ROOTFS/usr/bin/"
  fi
  mount_chroot
  trap umount_chroot EXIT

  log "configure apt sources (ports)"
  cat <<EOF | sudo tee "$ROOTFS/etc/apt/sources.list" >/dev/null
deb ${UBUNTU_PORTS} ${UBUNTU_CODENAME} main restricted universe multiverse
deb ${UBUNTU_PORTS} ${UBUNTU_CODENAME}-updates main restricted universe multiverse
deb ${UBUNTU_PORTS} ${UBUNTU_CODENAME}-security main restricted universe multiverse
EOF
  # Skip Translation-* indexes (tens–hundreds of MB) and recommends by default.
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

  local digest size
  digest="$(sha256_file "$OUTPUT")"
  size="$(wc -c < "$OUTPUT" | tr -d ' ')"
  printf '{"version":"%s","size":%s,"sha256":"%s"}\n' "$PIRT_ROOTFS_VERSION" "$size" "$digest"
}

main "$@"
