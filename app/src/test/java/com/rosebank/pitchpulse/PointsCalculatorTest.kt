package com.rosebank.pitchpulse

import org.junit.Assert.assertEquals
import org.junit.Test

class PointsCalculatorTest {
    @Test
    fun resultOnly() {
        assertEquals(3, PointsCalculator.award(result = true, score = false, scorer = false))
    }

    @Test
    fun scoreIncludesResult() {
        assertEquals(13, PointsCalculator.award(result = true, score = true, scorer = false))
    }

    @Test
    fun everything() {
        assertEquals(18, PointsCalculator.award(result = true, score = true, scorer = true))
    }

    @Test
    fun miss() {
        assertEquals(0, PointsCalculator.award(result = false, score = false, scorer = false))
    }
}
