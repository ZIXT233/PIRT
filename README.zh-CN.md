# PIRT

**掌上虚拟电脑Agent**

[English](README.md) · 简体中文

很多手机 Agent 应用仍然需要远程电脑或云端工作区。PIRT 把执行环境放在手机上：应用内置运行于 PRoot 的 Ubuntu，在其中运行 Pi Coding Agent，并给 Agent 提供可以真实执行命令和读写文件的 `/workspace`。

PIRT 的 Shell、本地 XFCE 图形桌面和 Android 系统文件管理器访问的是同一个工作区。你可以直接用手机应用编辑、复制或分享这些文件，不需要先从远程电脑下载。

<p align="center">
  <img src="./screenshot.png" width="360" alt="PIRT 在 Android 手机上识别其本地 Ubuntu 环境">
</p>

## 它具体做了什么

- 通过 PRoot 在 ARM64 Android 手机上运行 Ubuntu 24.04，不需要 Root 权限。
- Pi 与工具都在这个 Ubuntu 环境内运行，命令不会转发到另一台电脑执行。
- Agent、Shell 和 XFCE 图形桌面共同使用持久化的 `/workspace`。
- 通过 Android 存储访问框架把工作区暴露给系统文件管理器和兼容应用，操作的是原文件，不是额外副本。
- 可以启动不依附于会话的后台独立进程，例如长期运行 Minecraft 服务端；可以在 PIRT 的进程列表中查看或停止。
- 可选悬浮窗用于保持本地运行环境活跃，避免应用切到后台后被 Android 冻结。
- 使用 Pi 的真实会话历史，可以恢复之前的对话。

它与远程 Agent 工作流最直接的区别是执行位置。远程方案需要另一台电脑，并且需要传输或同步文件；PIRT 直接在手机上执行，工作文件也留在手机上。

## 高阶用法：通过 ADB 操作 Android

把 Android 无线调试显示的配对码和端口提供给 Agent，Agent 即可从 Ubuntu 环境通过 ADB 完成配对，进而检查和操控手机。

## 本地环境控制

- 持久 Shell 和本地 XFCE 桌面；既可通过浏览器 noVNC 访问，也可使用 aVNC 等 VNC 客户端
- 创建、查看和停止后台独立进程
- 会话管理、上下文与 Token 使用量及 HTML 导出
- 服务商登录、OpenAI 兼容 API 和模型选择
- AI 回复完成通知和通知栏输入

<p align="center">
  <img src="./screenshot-desktop.jpg" width="360" alt="PIRT 与 aVNC 在 Android 分屏模式下同时运行">
  <br>
  <sub>PIRT 与 aVNC 分屏运行；同一桌面也可以通过浏览器 noVNC 打开。</sub>
</p>

## 运行要求

- Android 7.0 或更高版本
- ARM64（`arm64-v8a`）设备
- 足够容纳 APK 与解压后 Ubuntu 环境的存储空间
- 支持的 AI 服务商账号或 API Key

PIRT **不需要 Root 权限**。当前版本将初始 Rootfs 镜像内置在 APK 中，以便离线初始化，因此安装包体积较大。

## 安装

1. 从 [GitHub Releases](https://github.com/ZIXT233/PIRT/releases) 下载最新的已签名 ARM64 APK。
2. Android 提示时，允许浏览器或文件管理器“安装未知应用”。
3. 安装并打开 PIRT。
4. 首次启动时，等待应用校验并解压内置环境。
5. 登录 AI 服务商或添加 OpenAI 兼容 API，然后选择模型。

Pi 会话或独立进程需要在应用后台继续运行时，应开启 PIRT 悬浮窗，防止本地运行环境被 Android 冻结。浏览器授权登录在切换应用后接收登录结果也依赖悬浮窗；设备码登录不依赖它。

## 工作原理

```text
Android / Jetpack Compose
          │
          ├── 会话、文件、通知与悬浮窗
          │
      PIRT Runtime Service
          │
          ├── Pi SDK 会话宿主
          ├── 持久 Shell 与进程
          └── 本地 XFCE 图形桌面
                    │
             Ubuntu on PRoot
                    │
               /workspace
```

Pi 负责会话历史与 Agent 生命周期；PIRT 负责 Android 生命周期、本地运行环境、共享工作区和移动端呈现。技术细节可查看[架构文档](docs/architecture.zh-CN.md)。

## 运行环境

| 组件 | 版本 |
| --- | --- |
| Ubuntu | 24.04.4 LTS |
| Pi Coding Agent | 0.84.1 |
| Node.js | 22.20.0 |
| Python | 3.12.3 |
| XFCE | 4.18 |
| PRoot | 5.1.107.89 |

应用会在解压前使用 SHA-256 校验内置的初始 Rootfs 镜像。安装后的环境允许写入，因此这个校验值不代表使用过程中 Rootfs 始终保持不变。可以按照 [Rootfs 构建文档](docs/rootfs-build.zh-CN.md) 从公开源重新构建镜像。

## 项目状态

PIRT 仍处于早期阶段。在升级或尝试修改运行环境前，请备份工作区中的重要文件。欢迎通过 [Issues](https://github.com/ZIXT233/PIRT/issues) 提交问题与实际使用反馈。

## 作者

由 [ZIXT](https://github.com/ZIXT233) 发起并维护。
