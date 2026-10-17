package com.matrix.launcher

import com.matrix.launcher.core.AppMatcher
import com.matrix.launcher.core.AppRef
import com.matrix.launcher.core.MatchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMatcherTest {
    private val apps = listOf(AppRef("1", "Chrome"), AppRef("2", "Chrome Beta"), AppRef("3", "Camera"), AppRef("4", "Calculator"))

    @Test fun exactMatchWins() {
        val m = AppMatcher.match("chrome", apps)
        assertTrue(m is MatchResult.Single)
        assertEquals("1", (m as MatchResult.Single).app.key)
    }

    @Test fun ambiguousPrefix() {
        assertTrue(AppMatcher.match("ca", apps) is MatchResult.Multiple)
    }

    @Test fun uniqueSubstring() {
        val m = AppMatcher.match("lculat", apps)
        assertTrue(m is MatchResult.Single)
    }

    @Test fun noMatchAndBlank() {
        assertEquals(MatchResult.None, AppMatcher.match("zzz", apps))
        assertEquals(MatchResult.None, AppMatcher.match("  ", apps))
    }
}
