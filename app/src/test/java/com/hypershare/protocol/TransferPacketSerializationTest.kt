package com.hypershare.protocol

import com.hypershare.model.DataChunk
import com.hypershare.model.PacketType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferPacketSerializationTest {

    @Test
    fun `transfer packet types enum mapping is correct`() {
        assertEquals(0x10.toByte(), PacketType.TRANSFER_INIT.code)
        assertEquals(0x11.toByte(), PacketType.TRANSFER_COMPLETE.code)
        assertEquals(0x14.toByte(), PacketType.TRANSFER_ACCEPT.code)
        assertEquals(0x15.toByte(), PacketType.TRANSFER_REJECT.code)
        assertEquals(0x16.toByte(), PacketType.TRANSFER_CHUNK_ACK.code)
        assertEquals(0x17.toByte(), PacketType.TRANSFER_CANCEL.code)
    }

    @Test
    fun `TransferInitPayload round-trip serialization preserves all metadata`() {
        val original = TransferInitPayload(
            transferId = "transfer-abc-123",
            fileName = "movie_presentation_4k.mp4",
            fileSize = 482590200L,
            totalChunks = 7364,
            fullCrc32 = 0xDEADBEEFL,
            isViewOnly = true,
            mimeType = "video/mp4"
        )

        val bytes = original.toByteArray()
        val parsed = TransferInitPayload.fromByteArray(bytes)

        assertEquals(original.transferId, parsed.transferId)
        assertEquals(original.fileName, parsed.fileName)
        assertEquals(original.fileSize, parsed.fileSize)
        assertEquals(original.totalChunks, parsed.totalChunks)
        assertEquals(original.fullCrc32, parsed.fullCrc32)
        assertEquals(original.isViewOnly, parsed.isViewOnly)
        assertEquals(original.mimeType, parsed.mimeType)
    }

    @Test
    fun `ChunkAckPayload round-trip serialization preserves transferId and chunkIndex`() {
        val original = ChunkAckPayload(transferId = "tx-999", chunkIndex = 42)
        val bytes = original.toByteArray()
        val parsed = ChunkAckPayload.fromByteArray(bytes)

        assertEquals("tx-999", parsed.transferId)
        assertEquals(42, parsed.chunkIndex)
    }

    @Test
    fun `TransferResponsePayload round-trip serialization works for accept and reject`() {
        val accept = TransferResponsePayload(transferId = "tx-1", accepted = true)
        val parsedAccept = TransferResponsePayload.fromByteArray(accept.toByteArray())
        assertTrue(parsedAccept.accepted)
        assertEquals("tx-1", parsedAccept.transferId)

        val reject = TransferResponsePayload(transferId = "tx-2", accepted = false, reason = "Storage full")
        val parsedReject = TransferResponsePayload.fromByteArray(reject.toByteArray())
        assertEquals(false, parsedReject.accepted)
        assertEquals("Storage full", parsedReject.reason)
    }

    @Test
    fun `TransferChunkPayload round-trip serialization preserves binary payload and CRC32`() {
        val testPayload = ByteArray(1024) { (it % 100).toByte() }
        val chunk = DataChunk(
            transferId = "tx-chunk-test",
            chunkIndex = 15,
            totalChunks = 50,
            payload = testPayload,
            crc32Checksum = 123456789L
        )

        val bytes = TransferChunkPayload(chunk).toByteArray()
        val parsedChunk = TransferChunkPayload.fromByteArray(bytes)

        assertEquals(chunk.transferId, parsedChunk.transferId)
        assertEquals(chunk.chunkIndex, parsedChunk.chunkIndex)
        assertEquals(chunk.totalChunks, parsedChunk.totalChunks)
        assertEquals(chunk.crc32Checksum, parsedChunk.crc32Checksum)
        assertArrayEquals(chunk.payload, parsedChunk.payload)
    }

    @Test
    fun `PacketBuilder and PacketParser serialize and parse TRANSFER_INIT packet`() {
        val payload = TransferInitPayload(
            transferId = "tx-full-packet",
            fileName = "report.pdf",
            fileSize = 1048576L,
            totalChunks = 16,
            fullCrc32 = 987654321L,
            isViewOnly = false,
            mimeType = "application/pdf"
        )

        val packet = PacketBuilder.buildTransferInitPacket(
            sourcePeerId = "peer-alpha",
            destPeerId = "peer-beta",
            seqNum = 101,
            payload = payload
        )

        val serialized = PacketBuilder.serializePacket(packet)
        val parsed = PacketParser.parsePacket(serialized)

        assertEquals(PacketType.TRANSFER_INIT, parsed.header.type)
        assertEquals("peer-alpha", parsed.header.sourcePeerId)
        assertEquals("peer-beta", parsed.header.destinationPeerId)
        assertEquals(101, parsed.header.sequenceNumber)

        val parsedPayload = TransferInitPayload.fromByteArray(parsed.payload)
        assertEquals("report.pdf", parsedPayload.fileName)
        assertEquals(1048576L, parsedPayload.fileSize)
    }
}
