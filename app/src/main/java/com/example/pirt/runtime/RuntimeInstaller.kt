package com.example.pirt.runtime

import android.content.Context
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArrayList

data class RuntimeArtifact(
    val version: String,
    val url: String,
    val sha256: String,
    val size: Long,
    val assetPath: String,
)

object RuntimeArtifacts {
    // Built from pinned Ubuntu Base 24.04.4 with the offline PIRT toolchain.
    val ubuntuArm64 = RuntimeArtifact(
        version = "24.04.4-pirt-1",
        url = "https://cdimages.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-arm64.tar.gz",
        sha256 = "455e1a02af0496b4389afcae3f2d78acf132fba42a0c06bae6cfd00dbf0ed5e2",
        size = 326_603_541,
        assetPath = "runtime/ubuntu-base-24.04.4-base-arm64.blob",
    )
}

sealed interface InstallState {
    data object Idle : InstallState
    data class Copying(val copiedBytes: Long, val totalBytes: Long) : InstallState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : InstallState
    data object Verifying : InstallState
    data object Extracting : InstallState
    data object Complete : InstallState
    data class Failed(val message: String) : InstallState
}

/** Installs the bundled and verified Ubuntu rootfs without requiring network access. */
class RuntimeInstaller(private val context: Context, private val paths: RuntimePaths) {
    private val download = File(paths.root, "downloads/ubuntu-${RuntimeArtifacts.ubuntuArm64.version}-arm64.tar.gz")
    private val staging = File(paths.root, "ubuntu.installing")

    fun install(onState: (InstallState) -> Unit): Boolean {
        observers += onState
        onState(latestState)
        if (!running.compareAndSet(false, true)) return false
        Thread({
            fun report(next: InstallState) {
                latestState = next
                observers.forEach { observer -> runCatching { observer(next) } }
                when (next) {
                    InstallState.Extracting -> RuntimeDiagnostics.info(context, "installer", "Extracting ${RuntimeArtifacts.ubuntuArm64.version}")
                    InstallState.Complete -> RuntimeDiagnostics.info(context, "installer", "Installed ${RuntimeArtifacts.ubuntuArm64.version}")
                    else -> Unit
                }
            }
            try {
                paths.root.mkdirs()
                if (!download.isFile || sha256(download) != RuntimeArtifacts.ubuntuArm64.sha256) {
                    copyPackagedArtifact(download, RuntimeArtifacts.ubuntuArm64, ::report)
                }
                report(InstallState.Verifying)
                check(sha256(download) == RuntimeArtifacts.ubuntuArm64.sha256) {
                    "Ubuntu archive checksum mismatch"
                }
                report(InstallState.Extracting)
                recreateStagingDirectory()
                extractTarGzip(download, staging)
                installSupportFiles(staging)
                File(staging, ".pirt-rootfs-version").writeText(RuntimeArtifacts.ubuntuArm64.version)
                if (paths.rootfs.exists()) {
                    preserveUserDirectory("root")
                    preserveUserDirectory("home")
                    deleteTreeInsideRuntime(paths.rootfs)
                }
                check(staging.renameTo(paths.rootfs)) { "Could not promote the installed Ubuntu rootfs" }
                report(InstallState.Complete)
            } catch (error: Exception) {
                RuntimeDiagnostics.error(context, "installer", error.message ?: "Runtime installation failed", error)
                report(InstallState.Failed(error.message ?: "Runtime installation failed"))
            } finally {
                running.set(false)
                observers.clear()
            }
        }, "pirt-runtime-installer").start()
        return true
    }

    companion object {
        private val running = AtomicBoolean(false)
        private val observers = CopyOnWriteArrayList<(InstallState) -> Unit>()
        @Volatile private var latestState: InstallState = InstallState.Idle
    }

    private fun installSupportFiles(rootfs: File) {
        listOf("pirt-control-bridge.mjs").forEach { name ->
            val bridge = File(rootfs, "usr/local/lib/pirt/$name")
            bridge.parentFile?.mkdirsChecked()
            context.assets.open("runtime/$name").use { input ->
                FileOutputStream(bridge, false).use(input::copyTo)
            }
            runCatching { Os.chmod(bridge.absolutePath, 0b111101101) }
        }
    }

    private fun copyPackagedArtifact(
        target: File,
        artifact: RuntimeArtifact,
        onState: (InstallState) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        context.assets.open(artifact.assetPath).use { input ->
            FileOutputStream(target, false).use { fileOutput ->
                BufferedOutputStream(fileOutput).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    var copied = 0L
                    var lastReported = 0L
                    onState(InstallState.Copying(copied, artifact.size))
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (copied - lastReported >= 4L * 1024 * 1024 || copied == artifact.size) {
                            onState(InstallState.Copying(copied, artifact.size))
                            lastReported = copied
                        }
                    }
                    check(copied == artifact.size) { "Packaged Ubuntu archive has an unexpected size" }
                }
            }
        }
    }

    private fun download(target: File, artifact: RuntimeArtifact, onState: (InstallState) -> Unit) {
        target.parentFile?.mkdirs()
        var existing = target.length().takeIf { target.isFile } ?: 0L
        val connection = URL(artifact.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        if (existing > 0) connection.setRequestProperty("Range", "bytes=$existing-")
        connection.connect()
        check(connection.responseCode in 200..299) { "Download failed with HTTP ${connection.responseCode}" }
        val resumed = existing > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
        if (!resumed) existing = 0L
        val responseBytes = connection.contentLengthLong.takeIf { it >= 0 }
        val total = responseBytes?.plus(existing)
        connection.inputStream.use { input ->
            FileOutputStream(target, resumed).use { fileOutput ->
                BufferedOutputStream(fileOutput).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    var downloaded = existing
                    onState(InstallState.Downloading(downloaded, total))
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onState(InstallState.Downloading(downloaded, total))
                    }
                }
            }
        }
        connection.disconnect()
    }

    private fun extractTarGzip(archive: File, destination: File) {
        val deferredHardLinks = mutableListOf<Pair<File, File>>()
        val directoryModes = mutableListOf<Pair<File, Int>>()
        FileInputStream(archive).use { fileInput ->
            GzipCompressorInputStream(BufferedInputStream(fileInput)).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        val output = safeEntryFile(destination, entry.name)
                        when {
                            entry.isDirectory -> {
                                prepareArchiveOutput(output, replaceLeafSymlink = false)
                                output.mkdirsChecked()
                                directoryModes += output to entry.mode
                            }
                            entry.isSymbolicLink -> createSymlink(output, entry.linkName)
                            entry.isLink -> deferredHardLinks += output to safeEntryFile(destination, entry.linkName)
                            entry.isFile -> writeRegularFile(tar, entry, output)
                        }
                    }
                }
            }
        }
        deferredHardLinks.forEach { (link, target) ->
            link.parentFile?.mkdirsChecked()
            prepareArchiveOutput(link)
            checkResolvedPathInside(staging, target)
            if (link.exists() || isSymlink(link)) link.delete()
            try {
                Os.link(target.absolutePath, link.absolutePath)
            } catch (_: Exception) {
                when {
                    isSymlink(target) -> Os.symlink(Os.readlink(target.absolutePath), link.absolutePath)
                    target.isFile -> target.copyTo(link, overwrite = true)
                    else -> error("Hard-link target is unavailable: ${target.absolutePath}")
                }
            }
        }
        directoryModes.asReversed().forEach { (directory, mode) ->
            runCatching { Os.chmod(directory.absolutePath, mode and 0xfff) }
        }
    }

    private fun writeRegularFile(tar: TarArchiveInputStream, entry: TarArchiveEntry, output: File) {
        output.parentFile?.mkdirsChecked()
        prepareArchiveOutput(output)
        FileOutputStream(output, false).use { fileOutput ->
            BufferedOutputStream(fileOutput).use { tar.copyTo(it) }
        }
        runCatching { Os.chmod(output.absolutePath, entry.mode and 0xfff) }
    }

    private fun createSymlink(output: File, target: String) {
        output.parentFile?.mkdirsChecked()
        prepareArchiveOutput(output)
        if (output.exists() || isSymlink(output)) output.delete()
        Os.symlink(normalizeRootfsSymlinkTarget(staging, output, target), output.absolutePath)
    }

    /** Validate parents without following a stale archive symlink at the leaf being replaced. */
    private fun prepareArchiveOutput(output: File, replaceLeafSymlink: Boolean = true) {
        if (output.absoluteFile == staging.absoluteFile) return
        output.parentFile
            ?.takeUnless { it.absoluteFile == staging.absoluteFile }
            ?.let { checkResolvedPathInside(staging, it) }
        if (replaceLeafSymlink && isSymlink(output)) output.delete()
        checkResolvedPathInside(staging, output)
    }

    private fun safeEntryFile(root: File, name: String): File {
        return resolveArchiveEntry(root, name)
    }

    private fun checkResolvedPathInside(root: File, output: File) {
        val resolvedRoot = root.canonicalFile.path.trimEnd(File.separatorChar)
        val resolvedOutput = output.canonicalFile.path
        check(resolvedOutput != resolvedRoot && resolvedOutput.startsWith("$resolvedRoot${File.separator}")) {
            "Archive entry resolves outside the rootfs: $output -> $resolvedOutput"
        }
    }

    private fun recreateStagingDirectory() {
        if (staging.exists()) deleteTreeInsideRuntime(staging)
        staging.mkdirsChecked()
    }

    /** Runtime upgrades replace system files but retain Pi credentials, sessions and user files. */
    private fun preserveUserDirectory(name: String) {
        val source = File(paths.rootfs, name)
        if (!source.exists() && !isSymlink(source)) return
        val destination = File(staging, name)
        copyWithoutFollowingLinks(source, destination)
    }

    private fun copyWithoutFollowingLinks(source: File, destination: File) {
        when {
            isSymlink(source) -> {
                destination.parentFile?.mkdirsChecked()
                if (destination.exists() || isSymlink(destination)) deleteWithoutFollowingLinks(destination)
                Os.symlink(Os.readlink(source.absolutePath), destination.absolutePath)
            }
            source.isDirectory -> {
                if (isSymlink(destination)) deleteWithoutFollowingLinks(destination)
                destination.mkdirsChecked()
                source.listFiles()?.forEach { copyWithoutFollowingLinks(it, File(destination, it.name)) }
            }
            source.isFile -> {
                destination.parentFile?.mkdirsChecked()
                if (isSymlink(destination)) deleteWithoutFollowingLinks(destination)
                source.copyTo(destination, overwrite = true)
            }
        }
    }

    private fun deleteTreeInsideRuntime(target: File) {
        val runtime = paths.root.absoluteFile.path.trimEnd(File.separatorChar)
        val resolved = target.absoluteFile.path
        check(resolved != runtime && resolved.startsWith("$runtime${File.separator}")) {
            "Refusing to delete outside the runtime"
        }
        deleteWithoutFollowingLinks(target)
    }

    private fun deleteWithoutFollowingLinks(target: File) {
        if (!isSymlink(target) && target.isDirectory) {
            // Extracted system directories can be 0555. Their contents cannot be removed until
            // the owning app regains write permission on the directory itself.
            runCatching { Os.chmod(target.absolutePath, 0b111000000) }
            target.listFiles()?.forEach(::deleteWithoutFollowingLinks)
        }
        check(target.delete() || !target.exists()) { "Could not remove ${target.absolutePath}" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun File.mkdirsChecked() {
        check(isDirectory || mkdirs()) { "Could not create $absolutePath" }
    }

    private fun isSymlink(file: File): Boolean = runCatching {
        Os.readlink(file.absolutePath)
        true
    }.getOrDefault(false)
}

internal fun resolveArchiveEntry(root: File, name: String): File {
    check(!name.startsWith('/') && !name.startsWith('\\') && '\\' !in name) {
        "Archive contains an absolute path: $name"
    }
    val components = name.split('/').filter { it.isNotEmpty() && it != "." }
    check(components.none { it == ".." }) { "Archive path escapes the rootfs: $name" }
    return components.fold(root.absoluteFile) { directory, component -> File(directory, component) }
}

/** PRoot's link2symlink may encode the old host rootfs path; make it rootfs-relative again. */
internal fun normalizeRootfsSymlinkTarget(root: File, link: File, target: String): String {
    val normalized = target.replace('\\', '/')
    val marker = "/pirt/runtime/ubuntu/"
    val markerIndex = normalized.indexOf(marker)
    if (markerIndex < 0) return target
    val rootRelativeTarget = normalized.substring(markerIndex + marker.length)
    return File(root, rootRelativeTarget)
        .relativeTo(requireNotNull(link.parentFile))
        .invariantSeparatorsPath
}
