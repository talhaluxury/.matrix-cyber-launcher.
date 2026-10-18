package com.matrix.launcher

import com.matrix.launcher.net.NetValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetValidationTest {
    @Test fun ipv4Valid() {
        assertEquals(0xC0A80101L, NetValidation.parseIpv4("192.168.1.1"))
        assertEquals(0L, NetValidation.parseIpv4("0.0.0.0"))
        assertEquals(0xFFFFFFFFL, NetValidation.parseIpv4("255.255.255.255"))
        assertEquals("10.20.30.40", NetValidation.formatIpv4(NetValidation.parseIpv4("10.20.30.40")!!))
    }

    @Test fun ipv4Invalid() {
        listOf("256.1.1.1", "1.2.3", "1.2.3.4.5", "", "a.b.c.d", "01.2.3.4", "1.2.3.-4", "1..2.3", " 1.2.3.4", "1.2.3.4 ").forEach {
            assertNull("should reject \"$it\"", NetValidation.parseIpv4(it))
        }
    }

    @Test fun hostnames() {
        assertTrue(NetValidation.isValidHostname("example.com"))
        assertTrue(NetValidation.isValidHostname("localhost"))
        assertTrue(NetValidation.isValidHostname("my-router.lan"))
        assertFalse(NetValidation.isValidHostname("-bad.com"))
        assertFalse(NetValidation.isValidHostname("bad-.com"))
        assertFalse(NetValidation.isValidHostname("a..b"))
        assertFalse(NetValidation.isValidHostname("under_score.com"))
        assertFalse(NetValidation.isValidHostname("has space.com"))
        assertFalse(NetValidation.isValidHostname("999.1.1.1"))
        assertFalse(NetValidation.isValidHostname("x".repeat(64) + ".com"))
        assertFalse(NetValidation.isValidHostname(""))
    }

    @Test fun targets() {
        assertTrue(NetValidation.isValidTarget("192.168.0.1"))
        assertTrue(NetValidation.isValidTarget("router.local"))
        assertFalse(NetValidation.isValidTarget("300.1.1.1"))
        assertFalse(NetValidation.isValidTarget("http://example.com"))
        assertFalse(NetValidation.isValidTarget("example.com/path"))
    }

    @Test fun singlePort() {
        assertEquals(80, NetValidation.parsePort("80"))
        assertEquals(65535, NetValidation.parsePort("65535"))
        assertNull(NetValidation.parsePort("0"))
        assertNull(NetValidation.parsePort("65536"))
        assertNull(NetValidation.parsePort("-1"))
        assertNull(NetValidation.parsePort("8o"))
        assertNull(NetValidation.parsePort(""))
    }

    @Test fun portLists() {
        assertEquals(listOf(22, 80, 443), NetValidation.parsePortList("22,80,443").ports)
        assertEquals(listOf(22, 80), NetValidation.parsePortList("22 80").ports)
        assertEquals(listOf(8000, 8001, 8002, 8003), NetValidation.parsePortList("8000-8003").ports)
        assertEquals(listOf(80), NetValidation.parsePortList("80,80").ports)
        assertEquals(listOf(21, 22, 23), NetValidation.parsePortList("21,22-23").ports)
    }

    @Test fun portListErrors() {
        listOf("", "0", "65536", "abc", "10-5", "1-65", "80,,x", "22,99999").forEach {
            val r = NetValidation.parsePortList(it)
            assertNotNull("should reject \"$it\"", r.error)
            assertTrue(r.ports.isEmpty())
        }
    }

    @Test fun cidrParsingMasksHostBits() {
        val r = NetValidation.parseCidr("192.168.1.77/24")!!
        assertEquals("192.168.1.0/24", r.toString())
        assertEquals(254, r.hostCount)
        val hosts = r.hosts()
        assertEquals(254, hosts.size)
        assertEquals("192.168.1.1", NetValidation.formatIpv4(hosts.first()))
        assertEquals("192.168.1.254", NetValidation.formatIpv4(hosts.last()))
        assertEquals(1022, NetValidation.parseCidr("192.168.0.0/22")!!.hostCount)
        assertEquals(2, NetValidation.parseCidr("10.0.0.0/30")!!.hostCount)
    }

    @Test fun cidrRejectsBadInput() {
        listOf("192.168.1.0", "192.168.1.0/", "192.168.1.0/ab", "192.168.1.0/21", "192.168.1.0/31", "192.168.1.0/32", "300.1.1.1/24", "/24", "10.0.0.0/-1", "10.0.0.0/024").forEach {
            assertNull("should reject \"$it\"", NetValidation.parseCidr(it))
        }
    }

    @Test fun privateRanges() {
        listOf("10.1.2.3", "172.16.0.1", "172.31.255.254", "192.168.5.5", "169.254.1.1").forEach {
            assertTrue(it, NetValidation.isPrivateOrLinkLocal(NetValidation.parseIpv4(it)!!))
        }
        listOf("8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.1.1", "1.1.1.1", "100.64.0.1").forEach {
            assertFalse(it, NetValidation.isPrivateOrLinkLocal(NetValidation.parseIpv4(it)!!))
        }
    }

    @Test fun suggestedRange() {
        val ip = NetValidation.parseIpv4("192.168.1.50")!!
        assertEquals("192.168.1.0/24", NetValidation.suggestRange(ip, 24)!!.toString())
        assertEquals("192.168.1.0/24", NetValidation.suggestRange(ip, 16)!!.toString())
        assertEquals("192.168.1.0/25", NetValidation.suggestRange(ip, 25)!!.toString())
        assertNull(NetValidation.suggestRange(ip, 31))
        assertNull(NetValidation.suggestRange(NetValidation.parseIpv4("8.8.8.8")!!, 24))
    }
}
