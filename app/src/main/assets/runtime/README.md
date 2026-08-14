# Runtime assets

`debian-13.6.0-base-arm64.blob` is **not** stored in Git (too large).

Build it (Debian/Ubuntu host or ARM Mac Docker `linux/arm64`):

```bash
tools/build-rootfs.sh build/pirt-rootfs-arm64.tar.gz
cp build/pirt-rootfs-arm64.tar.gz app/src/main/assets/runtime/debian-13.6.0-base-arm64.blob
```

See [docs/rootfs-build.zh-CN.md](../../../../../../docs/rootfs-build.zh-CN.md).
