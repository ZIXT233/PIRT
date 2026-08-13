package io.github.zixt233.pirt.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class HostProcessesTest {
    @Test
    fun parsesRealUidFromProcStatus() {
        val status = """
            Name:	om.example.pirt
            Uid:	10452	10452	10452	10452
            PPid:	1360
        """.trimIndent()
        assertEquals(10452, parseProcRealUid(status))
    }

    @Test
    fun labelsKnownRuntimeCommands() {
        assertEquals("PIRT Agent", hostProcessLabel("node", "/usr/local/bin/node /usr/local/lib/pirt/pirt-control-bridge.mjs"))
        assertEquals("PRoot", hostProcessLabel("libproot_exec.s", "/data/app/x/lib/arm64/libproot_exec.so --kill-on-exit"))
        assertEquals("PowerNukkitX", hostProcessLabel("java", "java -jar PowerNukkitX.jar"))
        assertEquals("PIRT", hostProcessLabel("om.example.pirt", "io.github.zixt233.pirt"))
        assertEquals("Shell", hostProcessLabel("bash", "bash -c sleep 60"))
    }

    @Test
    fun onlyWorkspaceProcessesAreStoppable() {
        val java = HostProcess(300, 1, "java", "java -jar server.jar", HostProcessKind.WORKSPACE)
        val pi = HostProcess(301, 1, "node", "/usr/local/lib/pirt/pirt-control-bridge.mjs", HostProcessKind.PI_RUNTIME)
        val app = HostProcess(302, 1, "om.example.pirt", "io.github.zixt233.pirt", HostProcessKind.APP)
        assertEquals(true, java.stoppable)
        assertEquals(false, pi.stoppable)
        assertEquals(false, app.stoppable)
    }

    @Test
    fun buildsNestedProcessForest() {
        val processes = listOf(
            HostProcess(100, 1, "om.example.pirt", "io.github.zixt233.pirt", HostProcessKind.APP),
            HostProcess(200, 100, "libproot_exec.s", "libproot_exec.so", HostProcessKind.PI_RUNTIME),
            HostProcess(300, 200, "node", "pirt-control-bridge.mjs", HostProcessKind.PI_RUNTIME),
            HostProcess(400, 300, "bash", "bash -c java -jar server.jar", HostProcessKind.WORKSPACE),
            HostProcess(500, 1, "java", "java -jar server.jar", HostProcessKind.WORKSPACE),
        )
        val forest = buildHostProcessForest(processes)
        assertEquals(listOf(100, 500), forest.map { it.process.pid })
        assertEquals(listOf(200), forest[0].children.map { it.process.pid })
        assertEquals(listOf(300), forest[0].children[0].children.map { it.process.pid })
        assertEquals(listOf(400), forest[0].children[0].children[0].children.map { it.process.pid })
        assertEquals(listOf(100, 200, 300, 400, 500), flattenHostProcessTree(forest).map { it.process.pid })
        assertEquals(
            listOf(100, 200, 500),
            flattenVisibleHostProcessForest(forest, collapsed = setOf(200)).map { it.process.pid },
        )
        assertEquals("└ ", hostProcessTreePrefix(1))
    }
}
