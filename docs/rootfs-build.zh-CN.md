# PIRT Rootfs 构建指南

PIRT 在 APK 里打包一个 **arm64 Debian rootfs**（`tar.gz` blob）。本文说明如何从公开源重新构建它。

> 当前提供的是可重复执行的构建流程。Debian 由 `debootstrap` 生成 minbase，Node.js 锁定 SHA-256，Pi 锁定顶层包版本；apt 依赖取构建时仓库状态，npm 传递依赖未用仓库内 lockfile 锁定。后续重建可能得到不同的 SHA-256。

## 构建产物

| 输出 | 用途 |
|------|------|
| `pirt-rootfs-*.tar.gz` | 构建脚本产出 |
| `app/src/main/assets/runtime/debian-13.6.0-base-arm64.blob` | 重命名/copy 后的 APK 资产 |
| `manifest.json` / `RuntimeInstaller.kt` | 写入 `version`、`size`、`sha256` |

`pirt-control-bridge.mjs` **不**打进 rootfs；Android 安装时由 `RuntimeInstaller` 从 app assets 复制。

## 构建机要求

- Debian / Ubuntu（x86_64 或 arm64；ARM Mac 可用 Docker `linux/arm64`）
- `sudo`、网络（apt + npm）
- 磁盘 ≥ 8 GB，内存 ≥ 2 GB
- 非 aarch64 主机构建 arm64 rootfs 需 `qemu-user-static`（脚本会自动安装）

## 一键构建

```bash
chmod +x tools/build-rootfs.sh
mkdir -p build
tools/build-rootfs.sh build/pirt-rootfs-arm64.tar.gz 2>&1 | tee build/rootfs-build.log
```

成功结束时脚本 stdout 最后一行是 JSON，例如：

```json
{"version":"13.6.0-pirt-1","size":123456789,"sha256":"abcdef..."}
```

## 构建完成后：更新 Android 资产

```bash
VERSION=$(grep '^PIRT_ROOTFS_VERSION=' tools/rootfs.env | cut -d= -f2)
SHA256=$(sha256sum build/pirt-rootfs-arm64.tar.gz | awk '{print $1}')
SIZE=$(wc -c < build/pirt-rootfs-arm64.tar.gz | tr -d ' ')

cp build/pirt-rootfs-arm64.tar.gz \
  app/src/main/assets/runtime/debian-13.6.0-base-arm64.blob

echo "version=$VERSION size=$SIZE sha256=$SHA256"
```

手动改两处：

1. **`app/src/main/assets/runtime/manifest.json`**
   - `debian.version` → `tools/rootfs.env` 里的 `PIRT_ROOTFS_VERSION`
   - `debian.size` / `debian.sha256` → 上面输出的值

2. **`RuntimeInstaller.kt`**
   - `RuntimeArtifacts.debianArm64` 的 `version`、`size`、`sha256` 同步

## 构建内容（pinned）

版本锁在 `tools/rootfs.env`：

- Debian 13.6（`trixie`）arm64 minbase（`debootstrap --variant=minbase`）
- apt：`xfce4`、`tigervnc-standalone-server`、`novnc`、`websockify`、`xdg-utils`、`git` 等（`--no-install-recommends`）
- Node.js 官方 arm64 tarball
- `@earendil-works/pi-coding-agent`（npm 全局安装）

## 校验 blob

```bash
tar -tzf build/pirt-rootfs-arm64.tar.gz >/dev/null && echo "tar ok"
```

## ARM Mac（Docker）示例

```bash
docker run --rm --privileged \
  -v "$PWD":/pirt -w /pirt \
  -e DEBIAN_MIRROR=http://mirrors.aliyun.com/debian \
  -e DEBIAN_SECURITY=http://mirrors.aliyun.com/debian-security \
  -e PIRT_BUILD_DIR=/var/tmp/pirt-rootfs-work \
  debian:trixie \
  bash -c 'apt-get update -qq && apt-get install -y -qq sudo curl ca-certificates &&
    tools/build-rootfs.sh /pirt/build/pirt-rootfs-arm64.tar.gz'
```
