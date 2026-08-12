package com.example.pirt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class RuntimeInstallerTest {
    private val root = File("build/test-rootfs").absoluteFile

    @Test
    fun resolvesNormalArchiveEntryInsideRoot() {
        assertEquals(
            File(root, "usr/bin/bash"),
            resolveArchiveEntry(root, "usr/bin/bash"),
        )
    }

    @Test
    fun resolvesArchiveRootEntryToRoot() {
        assertEquals(root, resolveArchiveEntry(root, "./"))
    }

    @Test
    fun rejectsParentTraversal() {
        assertThrows(IllegalStateException::class.java) {
            resolveArchiveEntry(root, "../../outside")
        }
    }

    @Test
    fun rejectsUnixAbsolutePath() {
        assertThrows(IllegalStateException::class.java) {
            resolveArchiveEntry(root, "/data/local/tmp/outside")
        }
    }

    @Test
    fun rejectsBackslashRootedPath() {
        assertThrows(IllegalStateException::class.java) {
            resolveArchiveEntry(root, "\\outside")
        }
    }

    @Test
    fun rewritesCapturedRootfsSymlinkToRelativeTarget() {
        val link = File(root, "usr/bin/perlbug")
        assertEquals(
            ".l2s.perlbug.dpkg-new0001",
            normalizeRootfsSymlinkTarget(
                root,
                link,
                "/data/user/0/com.example.pirt/files/pirt/runtime/ubuntu/usr/bin/.l2s.perlbug.dpkg-new0001",
            ),
        )
    }

    @Test
    fun preservesOrdinaryGuestSymlink() {
        assertEquals(
            "/usr/bin/python3",
            normalizeRootfsSymlinkTarget(root, File(root, "usr/bin/python"), "/usr/bin/python3"),
        )
    }
}
