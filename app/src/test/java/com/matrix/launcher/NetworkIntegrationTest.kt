package com.matrix.launcher

import com.matrix.launcher.net.NetValidation
import com.matrix.launcher.net.NetworkDiscovery
import com.matrix.launcher.net.PortChecker
import com.matrix.launcher.net.PortResult
import com.matrix.launcher.net.PortState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

/** Loopback-only integration tests: they talk to sockets this test opens itself. No external network needed. */
class NetworkIntegrationTest {
    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")

    @Test fun openLoopbackPortIsReachable() = runBlocking {
        ServerSocket(0, 5, loopback).use { server ->
            val r = PortChecker().probe(loopback, server.localPort)
            assertEquals(PortState.REACHABLE, r.state)
            assertNull(r.detail)
        }
    }

    @Test fun closedLoopbackPortIsRefused() = runBlocking {
        val port = ServerSocket(0, 1, loopback).use { it.localPort } // freed again on close
        val r = PortChecker().probe(loopback, port)
        assertEquals(PortState.REFUSED, r.state)
    }

    @Test fun checkReportsEveryRequestedPortOnce() = runBlocking {
        val open = ServerSocket(0, 5, loopback)
        val closed = ServerSocket(0, 1, loopback).use { it.localPort }
        try {
            val results = ArrayList<PortResult>()
            var resolved: String? = null
            val err = PortChecker().check("127.0.0.1", listOf(open.localPort, closed), { resolved = it }, { results.add(it) })
            assertNull(err)
            assertEquals("127.0.0.1", resolved)
            assertEquals(2, results.size)
            assertEquals(PortState.REACHABLE, results.first { it.port == open.localPort }.state)
            assertEquals(PortState.REFUSED, results.first { it.port == closed }.state)
        } finally {
            open.close()
        }
    }

    @Test fun unresolvableHostGivesDnsErrorAndNoResults() = runBlocking {
        val results = ArrayList<PortResult>()
        val err = PortChecker().check("no-such-host.invalid", listOf(80), { }, { results.add(it) })
        assertNotNull(err)
        assertTrue(err!!.startsWith("DNS"))
        assertTrue(results.isEmpty())
    }

    @Test fun discoveryAlwaysListsThisDeviceWithoutProbingItAndNeverInventsFields() = runBlocking {
        val range = NetValidation.parseCidr("127.0.0.0/30")!!
        val self = NetValidation.parseIpv4("127.0.0.1")
        val found = ArrayList<com.matrix.launcher.net.DiscoveredHost>()
        var lastProgress = 0
        NetworkDiscovery().run(range, self, { p, _ -> lastProgress = p }, { found.add(it) })
        assertEquals(2, lastProgress) // 127.0.0.1 and 127.0.0.2
        val me = found.first { it.ip == "127.0.0.1" }
        assertTrue(me.isThisDevice)
        assertNull(me.hostname)
        found.forEach { assertTrue(it.method.isNotBlank()) }
    }
}
