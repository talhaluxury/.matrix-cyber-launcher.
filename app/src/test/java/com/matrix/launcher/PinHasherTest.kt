package com.matrix.launcher

import com.matrix.launcher.core.PinHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test fun validPinRules() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin(""))
    }

    @Test fun verifyAcceptsRightPinOnly() {
        val salt = PinHasher.newSalt()
        val h = PinHasher.hash("4821", salt)
        assertTrue(PinHasher.verify("4821", salt, h))
        assertFalse(PinHasher.verify("4822", salt, h))
    }

    @Test fun saltsDifferAndChangeTheHash() {
        val s1 = PinHasher.newSalt()
        val s2 = PinHasher.newSalt()
        assertNotEquals(s1, s2)
        assertNotEquals(PinHasher.hash("1111", s1), PinHasher.hash("1111", s2))
        assertEquals(64, PinHasher.hash("1111", s1).length)
    }
}
