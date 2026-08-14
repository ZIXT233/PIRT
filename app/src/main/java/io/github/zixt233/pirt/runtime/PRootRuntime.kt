package io.github.zixt233.pirt.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import io.github.zixt233.pirt.model.WorkspaceConfig
import java.io.File

sealed interface RuntimeState {
    data object Ready : RuntimeState
    data class NotInstalled(val reason: String) : RuntimeState
}

data class RuntimePaths(
    val root: File,
    val proot: File,
    val rootfs: File,
    val sessions: File,
    val loader: File,
    val nativeLibraryDir: File,
    val nativeLinkDir: File,
) {
    companion object {
        fun from(context: Context): RuntimePaths {
            val root = File(context.filesDir, "pirt/runtime")
            return RuntimePaths(
                root = root,
                proot = File(context.applicationInfo.nativeLibraryDir, "libproot_exec.so"),
                rootfs = File(root, "debian"),
                sessions = File(root, "debian/root/.pi/pirt-sessions"),
                loader = File(context.applicationInfo.nativeLibraryDir, "libproot_loader.so"),
                nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
                nativeLinkDir = File(root, "native-links"),
            )
        }
    }
}

class PRootRuntime(context: Context) {
    private val appContext = context.applicationContext
    val paths: RuntimePaths = RuntimePaths.from(context)

    fun state(): RuntimeState {
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) {
            return RuntimeState.NotInstalled("当前版本需要 ARM64 手机")
        }
        if (!paths.proot.isFile) {
            return RuntimeState.NotInstalled("应用安装包缺少本地环境组件")
        }
        if (!paths.loader.isFile) {
            return RuntimeState.NotInstalled("应用安装包缺少本地环境加载器")
        }
        if (!prepareNativeLinks()) {
            return RuntimeState.NotInstalled("无法准备本地环境依赖")
        }
        if (!File(paths.rootfs, "bin/bash").isFile) {
            return RuntimeState.NotInstalled("本地开发环境尚未安装")
        }
        // The packaged rootfs is an initial environment, not an app-managed image.
        // APK upgrades must never replace a user's writable Linux installation.
        if (!prepareSupportFiles()) {
            return RuntimeState.NotInstalled("Could not prepare Pi runtime support files")
        }
        if (!File(paths.rootfs, "usr/local/bin/pi").isFile ||
            !File(paths.rootfs, "usr/local/bin/node").isFile ||
            !File(paths.rootfs, "usr/bin/git").isFile ||
            !File(paths.rootfs, "usr/bin/Xtigervnc").isFile ||
            !File(paths.rootfs, "usr/bin/tigervncpasswd").isFile ||
            !File(paths.rootfs, "usr/bin/startxfce4").isFile ||
            !File(paths.rootfs, "usr/bin/websockify").isFile ||
            !File(paths.rootfs, "usr/share/novnc/vnc.html").isFile ||
            !File(paths.rootfs, "usr/bin/xdg-open").isFile ||
            !File(paths.rootfs, "usr/local/lib/pirt/pirt-control-bridge.mjs").isFile
        ) {
            return RuntimeState.NotInstalled("开发工具安装不完整")
        }
        return RuntimeState.Ready
    }

    fun piAgentHostProcess(workspace: WorkspaceConfig): RuntimeProcessSpec {
        check(state() is RuntimeState.Ready) { "Debian runtime is not ready" }
        // The packaged rootfs is built in Docker, so its resolv.conf contains the
        // builder's private DNS server. Keep the guest resolver aligned with the
        // Android network before Node performs OAuth or model API requests.
        refreshNetworkConfiguration()
        paths.proot.setExecutable(true, true)
        val hostWorkspace = workspace(workspace)
        return RuntimeProcessSpec(
            command = buildList {
                add(paths.proot.absolutePath)
                addAll(listOf("--kill-on-exit", "--link2symlink", "-0", "-r", paths.rootfs.absolutePath))
                addAll(listOf("-b", "/dev", "-b", "/proc", "-b", "/sys"))
                addAll(listOf("-b", "${hostWorkspace.absolutePath}:/workspace", "-w", "/workspace"))
                addAll(listOf(
                    "/usr/bin/env", "-i",
                    "HOME=/root",
                    "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                    "LANG=C.UTF-8",
                    "TERM=xterm-256color",
                    "DISPLAY=:$GRAPHICS_DISPLAY",
                    "XDG_RUNTIME_DIR=/tmp/pirt-xdg-agent",
                    "/usr/local/bin/node", "/usr/local/lib/pirt/pirt-control-bridge.mjs",
                ))
            },
            environment = nativeEnvironment(),
        )
    }

    fun terminalProcess(workspace: WorkspaceConfig): RuntimeProcessSpec {
        check(state() is RuntimeState.Ready) { "本地开发环境尚未准备好" }
        val hostWorkspace = workspace(workspace)
        return RuntimeProcessSpec(
            command = listOf(
                paths.proot.absolutePath,
                "--link2symlink", "-0", "-r", paths.rootfs.absolutePath,
                "-b", "/dev", "-b", "/proc", "-b", "/sys",
                "-b", "${hostWorkspace.absolutePath}:/workspace",
                "-w", "/workspace",
                "/usr/bin/env", "-i",
                "HOME=/root",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "LANG=C.UTF-8",
                "TERM=xterm-256color",
                "DISPLAY=:$GRAPHICS_DISPLAY",
                "XDG_RUNTIME_DIR=/tmp/pirt-xdg-workspace",
                "/bin/bash", "--noprofile", "--norc",
            ),
            environment = nativeEnvironment(),
        )
    }

    fun provisionWorkspace(workspace: WorkspaceConfig): Result<Unit> = runCatching {
        check(state() is RuntimeState.Ready) { "Debian runtime is not ready" }
        workspace(workspace)
    }

    fun refreshNetworkConfiguration(): Result<Unit> = runCatching {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val network = checkNotNull(connectivity.activeNetwork) { "Android has no active network" }
        val properties = checkNotNull(connectivity.getLinkProperties(network)) {
            "Android did not provide network properties"
        }
        val dnsServers = properties.dnsServers.mapNotNull { it.hostAddress }
        check(dnsServers.isNotEmpty()) { "Android did not provide a DNS server" }

        val target = File(paths.rootfs, "etc/resolv.conf")
        target.parentFile?.mkdirs()
        val content = buildGuestResolvConf(dnsServers)
        if (!target.isFile || target.readText() != content) {
            target.writeText(content)
        }
    }

    fun graphicsProcess(workspace: WorkspaceConfig, password: String): GraphicsProcessSpec {
        check(state() is RuntimeState.Ready) { "本地开发环境尚未准备好" }
        val hostWorkspace = workspace(workspace)
        val display = GRAPHICS_DISPLAY
        val vncPort = 5900 + display
        val webPort = 15900 + display
        val runtimeDir = "/tmp/pirt-xdg-workspace"
        val passwordFile = "/tmp/pirt-vnc-workspace.passwd"
        val logFile = "/tmp/pirt-vnc-workspace.log"
        val script = """
            set -eu
            export HOME=/root
            export DISPLAY=:$display
            export XDG_RUNTIME_DIR='$runtimeDir'
            mkdir -p "${'$'}XDG_RUNTIME_DIR" /root/.vnc /tmp/.X11-unix
            chmod 700 "${'$'}XDG_RUNTIME_DIR"
            for font_dir in /usr/share/fonts/X11/*; do
              [ -d "${'$'}font_dir" ] && mkfontdir "${'$'}font_dir" >/dev/null 2>&1 || true
            done
            rm -f /tmp/.X${display}-lock /tmp/.X11-unix/X${display} '$passwordFile'
            printf '%s\n' ${shellSingleQuote(password)} | tigervncpasswd -f > '$passwordFile'
            chmod 600 '$passwordFile'
            vnc_pid=
            desktop_pid=
            cleanup() {
              kill "${'$'}desktop_pid" "${'$'}vnc_pid" 2>/dev/null || true
              rm -f '$passwordFile' /tmp/.X${display}-lock /tmp/.X11-unix/X${display}
            }
            trap cleanup EXIT INT TERM
            Xtigervnc :$display -rfbport $vncPort -geometry 1280x720 -depth 24 \
              -SecurityTypes VncAuth -PasswordFile '$passwordFile' -localhost \
              > '$logFile' 2>&1 &
            vnc_pid=${'$'}!
            for attempt in ${'$'}(seq 1 80); do
              [ -S /tmp/.X11-unix/X${display} ] && break
              kill -0 "${'$'}vnc_pid" 2>/dev/null || { cat '$logFile'; exit 1; }
              sleep 0.1
            done
            [ -S /tmp/.X11-unix/X${display} ] || { cat '$logFile'; exit 1; }
            export XDG_CURRENT_DESKTOP=XFCE
            export XDG_SESSION_TYPE=x11
            dbus-run-session -- /bin/bash -c '
              startxfce4 &
              xfce_pid=${'$'}!
              marker=/root/.config/pirt/wallpaper-v2-applied
              if [ ! -e "${'$'}marker" ]; then
                wallpaper=/usr/local/share/pirt/pirt-wallpaper.png
                for attempt in ${'$'}(seq 1 50); do
                  properties=${'$'}(xfconf-query -c xfce4-desktop -l 2>/dev/null | grep "/last-image${'$'}" || true)
                  if [ -z "${'$'}properties" ]; then
                    monitor=${'$'}(xrandr --query 2>/dev/null | awk "/ connected/{print \${'$'}1; exit}")
                    [ -n "${'$'}monitor" ] || monitor=VNC-0
                    properties=/backdrop/screen0/monitor${'$'}monitor/workspace0/last-image
                  fi
                  applied=false
                  while IFS= read -r property; do
                    if [ -n "${'$'}property" ] && { xfconf-query -c xfce4-desktop -p "${'$'}property" -s "${'$'}wallpaper" 2>/dev/null || xfconf-query -c xfce4-desktop -p "${'$'}property" -n -t string -s "${'$'}wallpaper" 2>/dev/null; }; then
                      applied=true
                    fi
                  done <<< "${'$'}properties"
                  if ${'$'}applied; then
                    mkdir -p "${'$'}(dirname "${'$'}marker")"
                    touch "${'$'}marker"
                    break
                  fi
                  sleep 0.1
                done
              fi
              wait "${'$'}xfce_pid"
            ' >> '$logFile' 2>&1 &
            desktop_pid=${'$'}!
            sleep 1
            kill -0 "${'$'}desktop_pid" 2>/dev/null || { cat '$logFile'; exit 1; }
            exec websockify --web=/usr/share/novnc 127.0.0.1:$webPort 127.0.0.1:$vncPort
        """.trimIndent()
        val command = listOf(
            paths.proot.absolutePath,
            "--kill-on-exit", "--link2symlink", "-0", "-r", paths.rootfs.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "${hostWorkspace.absolutePath}:/workspace",
            "-w", "/workspace",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8",
            "/bin/bash", "-lc", script,
        )
        return GraphicsProcessSpec(RuntimeProcessSpec(command, nativeEnvironment()), webPort, vncPort, display)
    }

    fun probe(): Result<String> = runCatching {
        check(state() is RuntimeState.Ready) { "Runtime is not ready" }
        val command = listOf(
            paths.proot.absolutePath,
            "--kill-on-exit", "--link2symlink", "-0", "-r", paths.rootfs.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys", "-w", "/root",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8",
            "/bin/bash", "-lc", "printf 'PIRT_RUNTIME_OK\\n'; uname -m",
        )
        val process = ProcessBuilder(command).apply {
            environment().putAll(nativeEnvironment())
            redirectErrorStream(true)
        }.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0 && "PIRT_RUNTIME_OK" in output) {
            output.ifBlank { "PRoot self-check failed" }
        }
        output.trim()
    }

    private fun workspace(config: WorkspaceConfig): File {
        val host = File(config.rootPath).apply { mkdirs() }
        File(paths.rootfs, "workspace").mkdirs()
        return host
    }

    private fun nativeEnvironment(): Map<String, String> = mapOf(
        "LD_LIBRARY_PATH" to "${paths.nativeLinkDir.absolutePath}:${paths.nativeLibraryDir.absolutePath}",
        "PROOT_LOADER" to paths.loader.absolutePath,
        "PROOT_TMP_DIR" to File(paths.root, "tmp").apply { mkdirs() }.absolutePath,
    )

    private fun prepareNativeLinks(): Boolean = runCatching {
        paths.nativeLinkDir.mkdirs()
        val versionedTalloc = File(paths.nativeLinkDir, "libtalloc.so.2")
        val target = File(paths.nativeLibraryDir, "libtalloc.so").absolutePath
        val currentTarget = runCatching { android.system.Os.readlink(versionedTalloc.absolutePath) }.getOrNull()
        if (currentTarget != target) {
            if (currentTarget != null || versionedTalloc.exists()) {
                check(versionedTalloc.delete()) { "Could not replace stale libtalloc link" }
            }
            android.system.Os.symlink(
                target,
                versionedTalloc.absolutePath,
            )
        }
        true
    }.getOrDefault(false)

    private fun prepareSupportFiles(): Boolean = runCatching {
        listOf("pirt-auth-bridge.mjs", "pirt-session-catalog.mjs").forEach { name ->
            File(paths.rootfs, "usr/local/lib/pirt/$name").delete()
        }
        File(paths.rootfs, "usr/local/lib/pirt/pirt-process-host.mjs").delete()
        listOf("pirt-control-bridge.mjs").forEach { name ->
            val target = File(paths.rootfs, "usr/local/lib/pirt/$name")
            val content = appContext.assets.open("runtime/$name").use { it.readBytes() }
            if (!target.isFile || !target.readBytes().contentEquals(content)) {
                target.parentFile?.mkdirs()
                target.writeBytes(content)
            }
            android.system.Os.chmod(target.absolutePath, 0b111101101)
        }
        val wallpaper = File(paths.rootfs, "usr/local/share/pirt/pirt-wallpaper.png")
        val wallpaperContent = appContext.assets.open("runtime/pirt-wallpaper.png").use { it.readBytes() }
        if (!wallpaper.isFile || !wallpaper.readBytes().contentEquals(wallpaperContent)) {
            wallpaper.parentFile?.mkdirs()
            wallpaper.writeBytes(wallpaperContent)
        }
        android.system.Os.chmod(wallpaper.absolutePath, 0b110100100)
        true
    }.getOrDefault(false)

    private fun shellSingleQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private companion object {
        const val GRAPHICS_DISPLAY = 100
    }
}

internal fun buildGuestResolvConf(dnsServers: List<String>): String = buildString {
    appendLine("# Generated by PIRT from the active Android network.")
    dnsServers.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .take(3)
        .forEach { appendLine("nameserver $it") }
}

data class RuntimeProcessSpec(
    val command: List<String>,
    val environment: Map<String, String>,
)

data class GraphicsProcessSpec(
    val process: RuntimeProcessSpec,
    val webPort: Int,
    val vncPort: Int,
    val display: Int,
)
