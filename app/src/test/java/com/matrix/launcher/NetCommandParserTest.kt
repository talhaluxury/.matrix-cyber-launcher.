package com.matrix.launcher

import com.matrix.launcher.core.AppRef
import com.matrix.launcher.core.CommandParser
import com.matrix.launcher.core.NetRequest
import com.matrix.launcher.core.TerminalAction
import com.matrix.launcher.core.TerminalEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class NetEnv : TerminalEnv {
    override val apps: List<AppRef> = emptyList()
    override fun time() = "12:00:00"
    override fun date() = "MONDAY"
    override fun battery() = "BATTERY: 50%"
    override fun status() = listOf("STATUS OK")
}

class NetCommandParserTest {
    private val env = NetEnv()
    private fun run(s: String) = CommandParser.execute(s, env)
    private fun net(s: String) = (run(s).action as? TerminalAction.Net)?.request

    @Test fun plainNetworkKeepsOldBehaviourAndListsSubcommands() {
        val r = run("network")
        assertEquals(TerminalAction.None, r.action)
        assertEquals("NETWORK: UNAVAILABLE", r.lines.first())
        assertTrue(r.lines.any { it.contains("NO TRAFFIC") })
        assertTrue(r.lines.any { it.contains("NETWORK DISCOVER") })
    }

    @Test fun networkSubcommands() {
        assertEquals(NetRequest.Status, net("network status"))
        assertEquals(NetRequest.Diagnostics, net("NETWORK DIAGNOSTICS"))
        assertEquals(NetRequest.Discover(null), net("network discover"))
        assertEquals(NetRequest.Discover("192.168.1.0/24"), net("network discover 192.168.1.0/24"))
    }

    @Test fun networkBadInputShowsUsageAndStartsNothing() {
        assertEquals(TerminalAction.None, run("network bogus").action)
        assertTrue(run("network bogus").lines.first().startsWith("USAGE"))
        assertEquals(TerminalAction.None, run("network discover 192.168.1.0/16").action)
        assertEquals(TerminalAction.None, run("network discover nonsense").action)
        assertEquals(TerminalAction.None, run("network discover 10.0.0.0/24 extra").action)
    }

    @Test fun dnsNeedsValidHostname() {
        assertTrue(run("dns").lines.contains("TARGET REQUIRED."))
        assertEquals(NetRequest.Dns("example.com"), net("dns example.com"))
        assertEquals(TerminalAction.None, run("dns bad_host!").action)
        assertEquals(TerminalAction.None, run("dns 999.1.1.1").action)
        assertEquals(TerminalAction.None, run("dns two words").action)
    }

    @Test fun portcheckValidation() {
        assertTrue(run("portcheck").lines.contains("TARGET REQUIRED."))
        assertTrue(run("portcheck 192.168.1.1").lines.contains("PORT REQUIRED."))
        assertEquals(NetRequest.PortCheck("192.168.1.1", listOf(80)), net("portcheck 192.168.1.1 80"))
        assertEquals(NetRequest.PortCheck("router.lan", listOf(22, 80, 443)), net("PORTCHECK router.lan 22,80,443"))
        assertEquals(TerminalAction.None, run("portcheck host 99999").action)
        assertEquals(TerminalAction.None, run("portcheck bad_host 80").action)
        assertEquals(TerminalAction.None, run("portcheck 300.1.1.1 80").action)
        assertTrue(run("portcheck host 99999").lines.any { it.startsWith("USAGE") })
    }

    @Test fun scanSubcommandsAndOldScan() {
        assertEquals(TerminalAction.Scan, run("scan").action)
        assertEquals(NetRequest.ScanStatus, net("scan status"))
        assertEquals(NetRequest.ScanCancel, net("scan cancel"))
        assertEquals(TerminalAction.None, run("scan foo").action)
        assertTrue(run("scan foo").lines.first().startsWith("USAGE"))
    }

    @Test fun reportCommand() {
        assertEquals(NetRequest.Report, net("report"))
    }

    @Test fun helpMentionsConfirmation() {
        val text = run("help").lines.joinToString("\n")
        assertTrue(text.contains("PORTCHECK"))
        assertTrue(text.contains("CONFIRMATION"))
    }
}
