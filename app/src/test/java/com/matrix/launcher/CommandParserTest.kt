package com.matrix.launcher

import com.matrix.launcher.core.AppRef
import com.matrix.launcher.core.CommandParser
import com.matrix.launcher.core.TerminalAction
import com.matrix.launcher.core.TerminalEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEnv(override val apps: List<AppRef>) : TerminalEnv {
    override fun time() = "12:00:00"
    override fun date() = "MONDAY"
    override fun battery() = "BATTERY: 50%"
    override fun status() = listOf("STATUS OK")
}

class CommandParserTest {
    private val apps = listOf(
        AppRef("a/x", "Chrome"), AppRef("b/x", "Camera"), AppRef("c/x", "Calculator"), AppRef("d/x", "WhatsApp"),
    )
    private val env = FakeEnv(apps)

    @Test fun emptyInputGivesNothing() {
        val r = CommandParser.execute("   ", env)
        assertTrue(r.lines.isEmpty())
        assertEquals(TerminalAction.None, r.action)
    }

    @Test fun helpListsAllCommands() {
        val text = CommandParser.execute("help", env).lines.joinToString("\n")
        CommandParser.COMMANDS.forEach { assertTrue("missing $it", text.contains(it)) }
    }

    @Test fun commandsAreCaseInsensitive() {
        val r = CommandParser.execute("oPeN whatsapp", env)
        assertEquals(TerminalAction.Launch("d/x"), r.action)
    }

    @Test fun ambiguousOpenDoesNotLaunch() {
        val r = CommandParser.execute("open ca", env)
        assertEquals(TerminalAction.None, r.action)
        assertTrue(r.lines.first().startsWith("AMBIGUOUS"))
    }

    @Test fun unknownOpenReportsNoMatch() {
        val r = CommandParser.execute("open nothing", env)
        assertEquals(TerminalAction.None, r.action)
        assertTrue(r.lines.first().startsWith("NO APP"))
    }

    @Test fun unknownCommandIsHandled() {
        val r = CommandParser.execute("rm -rf /", env)
        assertTrue(r.lines.first().startsWith("UNKNOWN COMMAND"))
        assertEquals(TerminalAction.None, r.action)
    }

    @Test fun clearRadarSettingsActions() {
        assertEquals(TerminalAction.Clear, CommandParser.execute("clear", env).action)
        assertEquals(TerminalAction.OpenRadar, CommandParser.execute("radar", env).action)
        assertEquals(TerminalAction.OpenSettings, CommandParser.execute("settings", env).action)
    }

    @Test fun timeDateBatteryStatus() {
        assertEquals("TIME: 12:00:00", CommandParser.execute("time", env).lines.first())
        assertEquals("DATE: MONDAY", CommandParser.execute("date", env).lines.first())
        assertEquals("BATTERY: 50%", CommandParser.execute("battery", env).lines.first())
        assertEquals("STATUS OK", CommandParser.execute("status", env).lines.first())
    }

    @Test fun searchFilters() {
        val r = CommandParser.execute("search ca", env)
        assertEquals("2 MATCH(ES):", r.lines.first())
    }

    @Test fun overlongInputIsTruncated() {
        val r = CommandParser.execute("open " + "x".repeat(5000), env)
        assertTrue(r.lines.first().length < 400)
    }

    @Test fun autocompleteSuggestsCommands() {
        assertEquals(listOf("SEARCH", "SETTINGS"), CommandParser.autocomplete("se"))
        assertTrue(CommandParser.autocomplete("open x").isEmpty())
    }

    @Test fun storageAndNetworkUseEnv() {
        assertEquals("STORAGE: UNAVAILABLE", CommandParser.execute("storage", env).lines.first())
        assertTrue(CommandParser.execute("network", env).lines.any { it.contains("NO TRAFFIC") })
    }

    @Test fun scanIsLabelledSimulated() {
        val r = CommandParser.execute("scan", env)
        assertEquals(TerminalAction.Scan, r.action)
        assertTrue(r.lines.joinToString().contains("SIMULATED"))
    }

    @Test fun themeSwitchesByPartialName() {
        assertEquals(TerminalAction.SetTheme("RED_ALERT"), CommandParser.execute("theme red", env).action)
        assertEquals(TerminalAction.SetTheme("CYAN_CYBER"), CommandParser.execute("theme cyan cyber", env).action)
        assertEquals(TerminalAction.None, CommandParser.execute("theme purple", env).action)
        assertTrue(CommandParser.execute("theme", env).lines.size > CommandParser.THEMES.size)
    }
}
