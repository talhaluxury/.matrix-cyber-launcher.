package com.matrix.launcher

import com.matrix.launcher.net.DiagKind
import com.matrix.launcher.net.DiagOutcome
import com.matrix.launcher.net.DiagResult
import com.matrix.launcher.net.DiagState
import com.matrix.launcher.net.DiscoveredHost
import com.matrix.launcher.net.DiscoveryState
import com.matrix.launcher.net.LocalNetInfo
import com.matrix.launcher.net.PortResult
import com.matrix.launcher.net.PortState
import com.matrix.launcher.net.PortsState
import com.matrix.launcher.net.ReportFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ReportFormatterTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun emptyStateHasNoContent() {
        assertFalse(ReportFormatter.hasContent(DiscoveryState(), PortsState(), DiagState()))
        val text = ReportFormatter.format(0L, LocalNetInfo.OFFLINE, DiscoveryState(), PortsState(), DiagState(), utc)
        assertFalse(text.contains("DISCOVERY"))
        assertFalse(text.contains("PORT REACHABILITY"))
        assertTrue(text.contains("Nothing was uploaded"))
    }

    @Test fun stampIsDeterministic() {
        assertEquals("1970-01-01 00:00:00", ReportFormatter.stamp(0L, utc))
    }

    @Test fun unknownFieldsAreNeverInvented() {
        val d = DiscoveryState(
            range = "192.168.1.0/24", probed = 254, total = 254, startedAtMs = 1000L, finishedAtMs = 2000L,
            hosts = listOf(DiscoveredHost("192.168.1.9", null, "ICMP/ECHO", 12L, 1500L, false)),
        )
        val text = ReportFormatter.format(5000L, LocalNetInfo.OFFLINE, d, PortsState(), DiagState(), utc)
        assertTrue(text.contains("192.168.1.9"))
        assertTrue(text.contains("host=Unknown"))
        assertTrue(text.contains("mac=Unknown"))
        assertTrue(text.contains("Devices found: 1"))
        assertTrue(text.contains("COMPLETE"))
    }

    @Test fun cancelledScansAreLabelledPartial() {
        val d = DiscoveryState(range = "10.0.0.0/24", probed = 10, total = 254, startedAtMs = 1L, finishedAtMs = 2L, cancelled = true)
        val text = ReportFormatter.format(5000L, LocalNetInfo.OFFLINE, d, PortsState(), DiagState(), utc)
        assertTrue(text.contains("CANCELLED (partial)"))
        assertTrue(text.contains("10 of 254"))
    }

    @Test fun portSummaryCountsStates() {
        val r = listOf(
            PortResult(22, PortState.REFUSED, 3, null),
            PortResult(80, PortState.REACHABLE, 5, null),
            PortResult(443, PortState.REACHABLE, 6, null),
            PortResult(81, PortState.TIMED_OUT, 2500, null),
        )
        assertEquals("4 tested: 2 reachable, 1 refused, 1 timed out", ReportFormatter.summary(r))
        assertEquals("no results", ReportFormatter.summary(emptyList()))
    }

    @Test fun portReportHasDisclaimerAndNoServiceClaims() {
        val p = PortsState(target = "10.0.0.2", resolved = "10.0.0.2", total = 1, startedAtMs = 1L, finishedAtMs = 2L,
            results = listOf(PortResult(80, PortState.REACHABLE, 4, null)))
        val text = ReportFormatter.format(9L, LocalNetInfo.OFFLINE, DiscoveryState(), p, DiagState(), utc)
        assertTrue(text.contains("80"))
        assertTrue(text.contains("REACHABLE"))
        assertTrue(text.contains("NOT determined"))
        assertFalse(text.contains("vulnerab"))
    }

    @Test fun diagnosticsShowValueOnlyWhenPresent() {
        val g = DiagState(history = listOf(
            DiagResult(DiagKind.DNS, DiagOutcome.OK, 23L, "ok", 1000L),
            DiagResult(DiagKind.LATENCY, DiagOutcome.FAIL, null, "no connect", 2000L),
        ))
        val text = ReportFormatter.format(9L, LocalNetInfo.OFFLINE, DiscoveryState(), PortsState(), g, utc)
        assertTrue(text.contains("[23 ms]"))
        val failLine = text.lines().first { it.contains("no connect") }
        assertFalse(failLine.contains(" ms]"))
    }
}
