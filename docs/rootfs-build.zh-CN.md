# PIRT Rootfs 构建指南

PIRT 在 APK 里打包一个 **arm64 Ubuntu rootfs**（`tar.gz` blob）。本文说明如何从公开源重新构建它，不采用手工维护的二进制链或 Windows tar 拼接。

> 当前提供的是可重复执行的构建流程，还不是字节级可复现构建。Ubuntu Base 和 Node.js 已锁定 SHA-256，Pi 锁定了顶层包版本；但 Ubuntu apt 依赖仍取构建时仓库状态，npm 传递依赖没有由仓库内 lockfile 锁定，归档元数据也未归一化。因此后续重建可能得到不同的 SHA-256。

## 构建产物

| 输出 | 用途 |
|------|------|
| `pirt-rootfs-*.tar.gz` | 构建脚本产出 |
| `app/src/main/assets/runtime/ubuntu-base-24.04.4-base-arm64.blob` | 重命名/copy 后的 APK 资产 |
| `manifest.json` / `RuntimeInstaller.kt` | 写入 `version`、`size`、`sha256` |

`pirt-control-bridge.mjs` **不**打进 rootfs；Android 安装时由 `RuntimeInstaller` 从 app assets 复制。

## 构建机要求

- Ubuntu / Debian **x86_64**（推荐 24.04）
- `sudo`、网络（apt + npm）
- 磁盘 ≥ 8 GB，内存 ≥ 2 GB
- 构建 **arm64** rootfs 需 `qemu-user-static`（脚本会自动安装）

## 一键构建（远端 Agent 执行）

```bash
# 1. 克隆仓库
git clone https://github.com/ZIXT233/PIRT.git
cd PIRT

# 2. 构建（约 15–30 分钟，视网络而定）
chmod +x tools/build-rootfs.sh
mkdir -p build
tools/build-rootfs.sh build/pirt-rootfs-arm64.tar.gz 2>&1 | tee build/rootfs-build.log
```

成功结束时脚本 stdout 最后一行是 JSON，例如：

```json
{"version":"24.04.4-pirt-1","size":123456789,"sha256":"abcdef..."}
```

## 构建完成后：更新 Android 资产

```bash
VERSION=$(grep PIRT_ROOTFS_VERSION tools/rootfs.env | cut -d= -f2)
SHA256=$(sha256sum build/pirt-rootfs-arm64.tar.gz | awk '{print $1}')
SIZE=$(wc -c < build/pirt-rootfs-arm64.tar.gz | tr -d ' ')

# 复制 blob
cp build/pirt-rootfs-arm64.tar.gz \
  app/src/main/assets/runtime/ubuntu-base-24.04.4-base-arm64.blob

echo "version=$VERSION size=$SIZE sha256=$SHA256"
```

手动改两处 Kotlin/JSON（或用 Agent 改）：

1. **`app/src/main/assets/runtime/manifest.json`**
   - `ubuntu.version` → `tools/rootfs.env` 里的 `PIRT_ROOTFS_VERSION`
   - `ubuntu.size` / `ubuntu.sha256` → 上面输出的值

2. **`app/src/main/java/io/github/zixt233/pirt/runtime/RuntimeInstaller.kt`**
   - `RuntimeArtifacts.ubuntuArm64` 的 `version`、`size`、`sha256` 同步

提交示例：

```bash
git add app/src/main/assets/runtime/ubuntu-base-24.04.4-base-arm64.blob \
        app/src/main/assets/runtime/manifest.json \
        app/src/main/java/io/github/zixt233/pirt/runtime/RuntimeInstaller.kt
git commit -m "build: rootfs ${VERSION} arm64 blob"
git push
```

## 构建内容（pinned）

顶层版本锁在 `tools/rootfs.env`：

- Ubuntu Base 24.04.4 arm64（官方 cdimage，SHA256 校验）
- apt：`xfce4`、`tigervnc-standalone-server`、`novnc`、`websockify`、`git` 等（`--no-install-recommends`）
- Node.js 官方 arm64 tarball
- `@earendil-works/pi-coding-agent`（npm 全局安装）

## 校验 blob

```bash
tar -tzf build/pirt-rootfs-arm64.tar.gz >/dev/null && echo "tar ok"
```

不要用 `tools/validate-rootfs-blob.mjs` 作为唯一依据；以 GNU `tar -tzf` 通过为准。

## 常见问题

**`Could not open '/lib/ld-linux-aarch64.so.1'`**  
说明在不完整 rootfs 里 chroot。不要用旧 blob 叠加 deb，必须从 `build-rootfs.sh` 重编。

**`corrupted tar archive`（Android 安装时）**  
assets 里的 blob 是 Windows tar 拼接产物。删除后用本脚本重建的标准 `tar.gz` 替换。

**构建脚本找不到 `rootfs.env`**  
确保 `tools/rootfs.env` 与 `tools/build-rootfs.sh` 在同一目录；单独拷贝时两个文件一起拷。

## 已废弃

- `tools/build_graphics_rootfs.py`
- `tools/build_graphics_rootfs.mjs`

上述脚本在 Windows 上拼接 tar 流，会产生 GNU tar 无法完整解压的归档。**禁止用于发版。**
