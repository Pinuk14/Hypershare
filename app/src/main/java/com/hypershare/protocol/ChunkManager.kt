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

    /**
     * Streams through an InputStream to compute total byte size and CRC32 checksum
     * in 64 KB buffers without loading the whole file into RAM.
     */
    fun computeStreamMetadata(inputStream: java.io.InputStream): Pair<Long, Long> {
        val crc = CRC32()
        val buffer = ByteArray(CHUNK_SIZE)
        var totalBytes = 0L
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            crc.update(buffer, 0, bytesRead)
            totalBytes += bytesRead
        }
        return Pair(totalBytes, crc.value)
    }

    /**
     * Reads the next chunk from an InputStream.
     * Returns null when end-of-stream is reached.
     */
    fun readNextChunk(
        inputStream: java.io.InputStream,
        transferId: String,
        chunkIndex: Int,
        totalChunks: Int
    ): DataChunk? {
        val buffer = ByteArray(CHUNK_SIZE)
        var totalRead = 0

        while (totalRead < CHUNK_SIZE) {
            val count = inputStream.read(buffer, totalRead, CHUNK_SIZE - totalRead)
            if (count == -1) break
            totalRead += count
        }

        if (totalRead == 0) return null

        val payload = if (totalRead == CHUNK_SIZE) buffer else buffer.copyOf(totalRead)
        val crc = CRC32()
        crc.update(payload)

        return DataChunk(
            transferId = transferId,
            chunkIndex = chunkIndex,
            totalChunks = totalChunks,
            payload = payload,
            crc32Checksum = crc.value
        )
    }

    /**
     * Writes a chunk directly to disk at offset chunkIndex * CHUNK_SIZE using RandomAccessFile.
     * Prevents keeping multi-megabyte files in memory and allows out-of-order chunk assembly.
     */
    fun writeChunkDirect(raf: java.io.RandomAccessFile, chunk: DataChunk) {
        val expectedOffset = chunk.chunkIndex.toLong() * CHUNK_SIZE
        raf.seek(expectedOffset)
        raf.write(chunk.payload)
    }

    /**
     * Verifies the CRC32 checksum of a completed file on disk.
     */
    fun verifyFileChecksum(file: java.io.File, expectedChecksum: Long): Boolean {
        if (!file.exists() || !file.isFile) return false
        val crc = CRC32()
        val buffer = ByteArray(CHUNK_SIZE)
        file.inputStream().use { stream ->
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                crc.update(buffer, 0, read)
            }
        }
        return crc.value == expectedChecksum
    }
}
