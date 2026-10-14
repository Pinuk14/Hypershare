package com.hypershare.protocol

import com.hypershare.model.DataChunk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32

class ChunkManagerStreamingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `computeStreamMetadata calculates exact size and CRC32 for streaming data`() {
        val testData = ByteArray(200 * 1024) { (it % 256).toByte() }
        val stream = ByteArrayInputStream(testData)

        val (size, crc) = ChunkManager.computeStreamMetadata(stream)

        assertEquals(testData.size.toLong(), size)

        val expectedCrc = CRC32().apply { update(testData) }.value
        assertEquals(expectedCrc, crc)
    }

    @Test
    fun `readNextChunk accurately splits stream into 64KB chunks with correct checksums`() {
        // 150 KB test data -> 2 full 64KB chunks + 1 partial 22KB chunk (total 3 chunks)
        val testData = ByteArray(150 * 1024) { (it % 127).toByte() }
        val stream = ByteArrayInputStream(testData)
        val transferId = "tx-123"
        val totalChunks = 3

        val chunk0 = ChunkManager.readNextChunk(stream, transferId, 0, totalChunks)
        assertNotNull(chunk0)
        assertEquals(0, chunk0!!.chunkIndex)
        assertEquals(ChunkManager.CHUNK_SIZE, chunk0.payload.size)

        val crc0 = CRC32().apply { update(chunk0.payload) }.value
        assertEquals(crc0, chunk0.crc32Checksum)

        val chunk1 = ChunkManager.readNextChunk(stream, transferId, 1, totalChunks)
        assertNotNull(chunk1)
        assertEquals(1, chunk1!!.chunkIndex)
        assertEquals(ChunkManager.CHUNK_SIZE, chunk1.payload.size)

        val chunk2 = ChunkManager.readNextChunk(stream, transferId, 2, totalChunks)
        assertNotNull(chunk2)
        assertEquals(2, chunk2!!.chunkIndex)
        assertEquals(150 * 1024 - 2 * ChunkManager.CHUNK_SIZE, chunk2.payload.size)

        // EOF check
        val chunk3 = ChunkManager.readNextChunk(stream, transferId, 3, totalChunks)
        assertNull(chunk3)
    }

    @Test
    fun `writeChunkDirect writes chunks to target offsets out-of-order and verifies full file`() {
        val testData = ByteArray(160 * 1024) { (it * 3 % 255).toByte() }
        val chunks = ChunkManager.splitFile("test-transfer", testData)

        val outputFile = tempFolder.newFile("reassembled.bin")
        val raf = RandomAccessFile(outputFile, "rw")

        // Write out of order: chunk 2, chunk 0, chunk 1
        ChunkManager.writeChunkDirect(raf, chunks[2])
        ChunkManager.writeChunkDirect(raf, chunks[0])
        ChunkManager.writeChunkDirect(raf, chunks[1])
        raf.close()

        val expectedCrc = CRC32().apply { update(testData) }.value
        assertTrue("CRC32 of reassembled file should match", ChunkManager.verifyFileChecksum(outputFile, expectedCrc))

        val readBack = outputFile.readBytes()
        assertArrayEquals("Content of reassembled file must match original", testData, readBack)
    }

    @Test
    fun `verifyFileChecksum detects corruptions`() {
        val testData = "Hypershare Industry Grade File Transfer".toByteArray(Charsets.UTF_8)
        val file = tempFolder.newFile("corrupt_test.txt").apply { writeBytes(testData) }
        val expectedCrc = CRC32().apply { update(testData) }.value

        assertTrue(ChunkManager.verifyFileChecksum(file, expectedCrc))
        assertFalse(ChunkManager.verifyFileChecksum(file, expectedCrc + 999))
    }
}
