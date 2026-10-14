package com.hypershare.service

import com.hypershare.application.TransferManager
import com.hypershare.model.TransferJob
import com.hypershare.model.TransferState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferMetricsTest {

    @Test
    fun `formatSpeed produces clean human readable speeds`() {
        val manager = TransferManager.getInstance()

        assertEquals("500 B/s", manager.formatSpeed(500))
        assertEquals("1.0 KB/s", manager.formatSpeed(1024))
        assertEquals("2.5 KB/s", manager.formatSpeed(2560))
        assertEquals("1.0 MB/s", manager.formatSpeed(1024 * 1024))
        assertEquals("15.5 MB/s", manager.formatSpeed((15.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `TransferJob progress computes accurately from 0 to 1`() {
        val jobZero = TransferJob(
            id = "1",
            sourcePeerId = "a",
            destinationPeerId = "b",
            fileName = "test.txt",
            fileSize = 1000L,
            bytesTransferred = 0L
        )
        assertEquals(0f, jobZero.progress, 0.001f)

        val jobHalf = jobZero.copy(bytesTransferred = 500L)
        assertEquals(0.5f, jobHalf.progress, 0.001f)

        val jobFull = jobZero.copy(bytesTransferred = 1000L)
        assertEquals(1.0f, jobFull.progress, 0.001f)
    }

    @Test
    fun `average and peak speed calculation simulation`() {
        val totalBytes = 100L * 1024L * 1024L // 100 MB
        val durationMs = 6250L // 6.25 seconds

        val avgSpeedBps = (totalBytes * 1000L) / durationMs
        // 100 MB in 6.25s = 16 MB/s (16,777,216 B/s)
        assertEquals(16777216L, avgSpeedBps)
        val formatted = TransferManager.getInstance().formatSpeed(avgSpeedBps)
        assertEquals("16.0 MB/s", formatted)
    }
}
