package com.blackvueeventos.app

import com.blackvueeventos.app.net.CAMERA_UNREACHABLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhaseMessagesTest {
    @Test
    fun cameraUnreachable_tellsYouToJoinItsWifi() {
        assertEquals(
            "No se alcanza la cámara. Conéctate a su Wi‑Fi e inténtalo.",
            CAMERA_UNREACHABLE,
        )
    }

    @Test
    fun jobFraction_usesFileCountUntilTheCurrentFileSizeIsKnown() {
        assertNull(jobFraction(done = 0, total = 0))
        assertEquals(0f, jobFraction(done = 0, total = 12)!!, 0.001f)
        assertEquals(2f / 12f, jobFraction(done = 2, total = 12)!!, 0.001f)
        assertEquals(0.625f, jobFraction(done = 2, total = 4, fileBytes = 50, fileSize = 100)!!, 0.001f)
        assertEquals(1f, jobFraction(done = 4, total = 4, fileBytes = 999, fileSize = 10)!!, 0.001f)
    }

    @Test
    fun parallelFraction_sumsCompletedFilesAndInFlightPortions() {
        assertNull(parallelFraction(done = 2, total = 0, inFlight = listOf(0.5f)))
        assertEquals(2.5f / 12f, parallelFraction(2, 12, listOf(0.5f, 0f))!!, 0.001f)
        assertEquals(1f, parallelFraction(4, 4, listOf(0.9f))!!, 0.001f)
    }
}
