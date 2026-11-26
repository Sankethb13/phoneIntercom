package com.intercom.ride.core

object LinkQualityEstimator {
    /** Maps round-trip time and audio packet loss to a user-facing quality grade. */
    fun estimate(rttMs: Int, lossPct: Float, pongStale: Boolean = false): LinkQuality = when {
        pongStale -> LinkQuality.POOR
        rttMs < 0 -> LinkQuality.UNKNOWN
        rttMs < 80 && lossPct < 1f -> LinkQuality.EXCELLENT
        rttMs < 150 && lossPct < 3f -> LinkQuality.GOOD
        rttMs < 300 && lossPct < 8f -> LinkQuality.FAIR
        else -> LinkQuality.POOR
    }
}
