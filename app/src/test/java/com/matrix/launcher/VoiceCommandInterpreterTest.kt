package com.matrix.launcher

import com.matrix.launcher.core.VoiceCommandInterpreter
import com.matrix.launcher.core.VoiceIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandInterpreterTest {
    @Test fun openApp() {
        assertEquals(VoiceIntent.OpenApp("whatsapp"), VoiceCommandInterpreter.interpret("Open WhatsApp"))
        assertEquals(VoiceIntent.OpenApp("camera"), VoiceCommandInterpreter.interpret("open camera."))
    }

    @Test fun fixedCommands() {
        assertEquals(VoiceIntent.OpenTerminal, VoiceCommandInterpreter.interpret("Open terminal"))
        assertEquals(VoiceIntent.OpenSettings, VoiceCommandInterpreter.interpret("open settings"))
        assertEquals(VoiceIntent.ShowBattery, VoiceCommandInterpreter.interpret("Show battery"))
    }

    @Test fun search() {
        assertEquals(VoiceIntent.Search(""), VoiceCommandInterpreter.interpret("search applications"))
        assertEquals(VoiceIntent.Search("maps"), VoiceCommandInterpreter.interpret("search for maps"))
    }

    @Test fun unknownAndEmpty() {
        assertTrue(VoiceCommandInterpreter.interpret("sing a song") is VoiceIntent.Unknown)
        assertTrue(VoiceCommandInterpreter.interpret("   ") is VoiceIntent.Unknown)
    }
}
