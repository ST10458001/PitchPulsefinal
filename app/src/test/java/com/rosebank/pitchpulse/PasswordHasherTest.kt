package com.rosebank.pitchpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test
    fun samePasswordAndSaltHashTheSame() {
        assertEquals(PasswordHasher.hash("Pitch123", "abc"), PasswordHasher.hash("Pitch123", "abc"))
    }

    @Test
    fun saltChangesTheHash() {
        assertNotEquals(PasswordHasher.hash("Pitch123", "abc"), PasswordHasher.hash("Pitch123", "def"))
    }

    @Test
    fun plainPasswordIsNotTheHash() {
        assertNotEquals("Pitch123", PasswordHasher.hash("Pitch123", "abc"))
    }

    @Test
    fun matchesOnlyTheRightPassword() {
        val salt = "abc"
        val hash = PasswordHasher.hash("Pitch123", salt)
        assertTrue(PasswordHasher.matches("Pitch123", salt, hash))
        assertFalse(PasswordHasher.matches("Pitch124", salt, hash))
    }
}
