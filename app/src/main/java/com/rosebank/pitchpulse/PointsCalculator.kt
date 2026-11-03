package com.rosebank.pitchpulse

/**
 * FR-006 point rules.
 * 3 for the correct result, 10 for the correct score, 5 for the correct first scorer.
 * Safe-bet streak handling lives in [Repository], not here — this stays pure and unit-tested.
 */
object PointsCalculator {
    fun award(result: Boolean, score: Boolean, scorer: Boolean): Int {
        var pts = 0
        if (result) pts += 3
        if (score) pts += 10
        if (scorer) pts += 5
        return pts
    }
}
