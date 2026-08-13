# Runtime assets

`ubuntu-base-24.04.4-base-arm64.blob` is **not** stored in Git (too large).

Build it on Ubuntu x86_64:

```bash
tools/build-rootfs.sh build/pirt-rootfs-arm64.tar.gz
cp build/pirt-rootfs-arm64.tar.gz app/src/main/assets/runtime/ubuntu-base-24.04.4-base-arm64.blob
```

See [docs/rootfs-build.md](../../../../../docs/rootfs-build.md) or [简体中文](../../../../../docs/rootfs-build.zh-CN.md).
