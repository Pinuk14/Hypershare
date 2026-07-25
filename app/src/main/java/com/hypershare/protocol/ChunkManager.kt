package com.hypershare.protocol

import com.hypershare.model.DataChunk
import java.util.zip.CRC32

object ChunkManager {
    const val CHUNK_SIZE = 64 * 1024 // 64 KB per chunk

    fun splitFile(transferId: String, fileData: ByteArray): List<DataChunk> {
        val totalChunks = (fileData.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        val chunks = mutableListOf<DataChunk>()

        for (i in 0 until totalChunks) {
            val start = i * CHUNK_SIZE
            val end = minOf(start + CHUNK_SIZE, fileData.size)
            val chunkPayload = fileData.copyOfRange(start, end)

            val crc = CRC32()
            crc.update(chunkPayload)

            chunks.add(
                DataChunk(
                    transferId = transferId,
                    chunkIndex = i,
                    totalChunks = totalChunks,
                    payload = chunkPayload,
                    crc32Checksum = crc.value
                )
            )
        }
        return chunks
    }

    fun reassemble(chunks: List<DataChunk>): ByteArray {
        val sorted = chunks.sortedBy { it.chunkIndex }
        val totalSize = sorted.sumOf { it.payload.size }
        val result = ByteArray(totalSize)

        var offset = 0
        for (chunk in sorted) {
            val crc = CRC32()
            crc.update(chunk.payload)
            require(crc.value == chunk.crc32Checksum) { "CRC32 checksum mismatch for chunk ${chunk.chunkIndex}" }

            System.arraycopy(chunk.payload, 0, result, offset, chunk.payload.size)
            offset += chunk.payload.size
        }
        return result
    }
}
