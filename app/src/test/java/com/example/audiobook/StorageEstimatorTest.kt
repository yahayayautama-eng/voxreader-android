package com.example.audiobook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageEstimatorTest {
    @Test
    fun `requires estimated output plus safety margin`() {
        assertTrue(StorageEstimator.hasRoom(400, 100, 250))
        assertFalse(StorageEstimator.hasRoom(349, 100, 250))
    }
}
