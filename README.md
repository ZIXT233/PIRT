# PIRT

**Pi Runtime on PRoot — A pocket virtual computer agent.**

English · [简体中文](README.zh-CN.md)

Most mobile agent apps still need a remote computer or cloud workspace. PIRT puts the execution environment on the phone: it bundles Ubuntu on PRoot, runs the Pi coding agent inside it, and gives the agent a real `/workspace` for commands and files.

The same workspace is available to PIRT's shell, local XFCE desktop, and Android's system file manager. You can edit, copy or share the files with ordinary phone apps without first moving them out of a remote machine.

![PIRT detecting its local Ubuntu environment on an Android phone](./screenshot.png)

## What it does

- Runs Ubuntu 24.04 locally on ARM64 Android devices through PRoot; root access is not required.
- Runs Pi and its tools inside that Ubuntu environment instead of forwarding commands to another computer.
- Stores project files in one persistent `/workspace` shared by the agent, shell and XFCE desktop.
- Exposes that workspace to Android's Storage Access Framework, so system file managers and compatible apps can use the original files directly.
- Runs independent background processes that are not tied to a conversation. They can host long-running tasks such as a Minecraft server, and can be viewed or stopped from PIRT's process list.
- Uses an optional floating overlay to keep the local runtime active when Android would otherwise freeze it in the background.
- Keeps Pi's real session history and can resume previous conversations.

The practical difference is where the work happens. A remote-agent workflow needs another machine and a way to transfer or synchronize its files. PIRT executes on the phone and keeps the working files there.

## Advanced use: Android over ADB

Provide the Agent with the pairing code and ports shown by Android Wireless debugging. The Agent can then pair through ADB from the Ubuntu environment and control the phone for device inspection and automation.

## Local runtime controls

- A persistent shell and local XFCE desktop, accessible in a browser through noVNC or with VNC clients such as aVNC
- Independent process creation, status inspection and termination
- Conversation management, context/token usage and HTML export
- Provider sign-in, OpenAI-compatible APIs and model selection
- Completion notifications and notification-bar input

![PIRT and aVNC running side by side in Android split-screen mode](./screenshot-desktop.jpg)

*PIRT and aVNC in Android split-screen mode. The same desktop can also be opened in a browser through noVNC.*

## Requirements

- Android 7.0 or later
- An ARM64 (`arm64-v8a`) device
- Enough free storage for the APK and the extracted Ubuntu environment
- An account or API key for a supported AI provider

PIRT does **not** require root access. The current release is distributed as a large APK because the initial Rootfs image is bundled for offline installation.

## Install

1. Download the latest signed ARM64 APK from [GitHub Releases](https://github.com/ZIXT233/PIRT/releases).
2. Allow your browser or file manager to install unknown apps when Android asks.
3. Install and open PIRT.
4. Wait for the bundled environment to be verified and extracted on first launch.
5. Sign in to an AI provider or add an OpenAI-compatible API, then select a model.

Enable PIRT's floating overlay when a Pi conversation or independent process must continue running while the app is in the background. The overlay is also required for browser-based sign-in to receive the authorization result after switching apps. Device-code sign-in does not depend on it.

## How it works

```text
Android / Jetpack Compose
          │
          ├── conversations, files, notifications and overlay
          │
      PIRT runtime service
          │
          ├── Pi SDK session host
          ├── persistent shell and processes
          └── local XFCE desktop
                    │
             Ubuntu on PRoot
                    │
               /workspace
```

Pi owns the conversation history and agent lifecycle. PIRT owns the Android lifecycle, local runtime, shared workspace and mobile presentation. Technical details are available in [the architecture document](docs/architecture.md).

## Runtime

| Component | Version |
| --- | --- |
| Ubuntu | 24.04.4 LTS |
| Pi coding agent | 0.84.1 |
| Node.js | 22.20.0 |
| Python | 3.12.3 |
| XFCE | 4.18 |
| PRoot | 5.1.107.89 |

The bundled initial Rootfs image is verified with SHA-256 before extraction. The installed environment is writable, so the initial image checksum is not a checksum of the environment after use.

## Status

PIRT is an early project. Back up important files in the workspace before upgrading or experimenting with the runtime. Bug reports and practical feedback are welcome in [Issues](https://github.com/ZIXT233/PIRT/issues).

## Author

Created and maintained by [ZIXT](https://github.com/ZIXT233).
