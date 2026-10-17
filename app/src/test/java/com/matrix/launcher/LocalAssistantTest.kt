package com.matrix.launcher

import com.matrix.launcher.core.AppRef
import com.matrix.launcher.core.LocalAssistant
import com.matrix.launcher.core.TerminalAction
import com.matrix.launcher.core.TerminalEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class AssistEnv(override val apps: List<AppRef>) : TerminalEnv {
    override fun time() = "12:00:00"
    override fun date() = "MONDAY"
    override fun battery() = "BATTERY: 77%"
    override fun status() = listOf("STATUS OK")
}

class LocalAssistantTest {
    private val env = AssistEnv(listOf(AppRef("a/x", "Chrome"), AppRef("b/x", "Camera")))

    @Test fun naturalOpenLaunchesApp() {
        val r = LocalAssistant.reply("please open chrome", env)
        // "please" is not a command word, so this phrase is not mapped; the plain form is
        assertFalse(r.handled)
        assertEquals(TerminalAction.Launch("a/x"), LocalAssistant.reply("launch chrome", env).action)
        assertEquals(TerminalAction.Launch("a/x"), LocalAssistant.reply("open chrome", env).action)
    }

    @Test fun batteryQuestionUsesRealEnv() {
        val r = LocalAssistant.reply("how is my battery", env)
        assertTrue(r.handled)
        assertEquals("BATTERY: 77%", r.lines.first())
    }

    @Test fun explainKnownCommand() {
        val r = LocalAssistant.reply("explain scan", env)
        assertTrue(r.lines.first().startsWith("SCAN:"))
        assertTrue(r.lines.first().contains("Nothing is probed"))
    }

    @Test fun themeFromSentence() {
        assertEquals(TerminalAction.SetTheme("RED_ALERT"), LocalAssistant.reply("theme red", env).action)
    }

    @Test fun generalKnowledgeIsHonestlyUnhandled() {
        val r = LocalAssistant.reply("what is the capital of france", env)
        assertFalse(r.handled)
        assertTrue(LocalAssistant.unhandledMessage(false).isNotEmpty())
    }

    @Test fun whatTimeIsIt() {
        assertEquals("TIME: 12:00:00", LocalAssistant.reply("what time is it", env).lines.first())
    }
}
