package com.intercom.ride.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkQualityEstimatorTest {
    @Test fun grades() {
        assertEquals(LinkQuality.UNKNOWN, LinkQualityEstimator.estimate(-1, 0f))
        assertEquals(LinkQuality.EXCELLENT, LinkQualityEstimator.estimate(40, 0f))
        assertEquals(LinkQuality.GOOD, LinkQualityEstimator.estimate(120, 2f))
        assertEquals(LinkQuality.FAIR, LinkQualityEstimator.estimate(250, 5f))
        assertEquals(LinkQuality.POOR, LinkQualityEstimator.estimate(500, 0f))
        assertEquals(LinkQuality.POOR, LinkQualityEstimator.estimate(20, 0f, pongStale = true))
    }
}
