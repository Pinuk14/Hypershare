package com.hypershare.protocol

import com.hypershare.model.DataChunk
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class TransferInitPayload(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val totalChunks: Int,
    val fullCrc32: Long,
    val isViewOnly: Boolean,
    val mimeType: String
) {
    fun toByteArray(): ByteArray {
        val idBytes = transferId.toByteArray(StandardCharsets.UTF_8)
        val nameBytes = fileName.toByteArray(StandardCharsets.UTF_8)
        val mimeBytes = mimeType.toByteArray(StandardCharsets.UTF_8)

        val size = 2 + idBytes.size +
                2 + nameBytes.size +
                8 + // fileSize
                4 + // totalChunks
                8 + // fullCrc32
                1 + // isViewOnly
                2 + mimeBytes.size

        val buffer = ByteBuffer.allocate(size)
        buffer.putShort(idBytes.size.toShort())
        buffer.put(idBytes)
        buffer.putShort(nameBytes.size.toShort())
        buffer.put(nameBytes)
        buffer.putLong(fileSize)
        buffer.putInt(totalChunks)
        buffer.putLong(fullCrc32)
        buffer.put(if (isViewOnly) 1.toByte() else 0.toByte())
        buffer.putShort(mimeBytes.size.toShort())
        buffer.put(mimeBytes)

        return buffer.array()
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): TransferInitPayload {
            val buffer = ByteBuffer.wrap(bytes)

            val idLen = buffer.short.toInt() and 0xFFFF
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val transferId = String(idBytes, StandardCharsets.UTF_8)

            val nameLen = buffer.short.toInt() and 0xFFFF
            val nameBytes = ByteArray(nameLen)
            buffer.get(nameBytes)
            val fileName = String(nameBytes, StandardCharsets.UTF_8)

            val fileSize = buffer.long
            val totalChunks = buffer.int
            val fullCrc32 = buffer.long
            val isViewOnly = buffer.get() == 1.toByte()

            val mimeLen = buffer.short.toInt() and 0xFFFF
            val mimeBytes = ByteArray(mimeLen)
            buffer.get(mimeBytes)
            val mimeType = String(mimeBytes, StandardCharsets.UTF_8)

            return TransferInitPayload(
                transferId = transferId,
                fileName = fileName,
                fileSize = fileSize,
                totalChunks = totalChunks,
                fullCrc32 = fullCrc32,
                isViewOnly = isViewOnly,
                mimeType = mimeType
            )
        }
    }
}

data class ChunkAckPayload(
    val transferId: String,
    val chunkIndex: Int
) {
    fun toByteArray(): ByteArray {
        val idBytes = transferId.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(2 + idBytes.size + 4)
        buffer.putShort(idBytes.size.toShort())
        buffer.put(idBytes)
        buffer.putInt(chunkIndex)
        return buffer.array()
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): ChunkAckPayload {
            val buffer = ByteBuffer.wrap(bytes)
            val idLen = buffer.short.toInt() and 0xFFFF
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val transferId = String(idBytes, StandardCharsets.UTF_8)
            val chunkIndex = buffer.int
            return ChunkAckPayload(transferId, chunkIndex)
        }
    }
}

data class TransferResponsePayload(
    val transferId: String,
    val accepted: Boolean,
    val reason: String = ""
) {
    fun toByteArray(): ByteArray {
        val idBytes = transferId.toByteArray(StandardCharsets.UTF_8)
        val reasonBytes = reason.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(2 + idBytes.size + 1 + 2 + reasonBytes.size)
        buffer.putShort(idBytes.size.toShort())
        buffer.put(idBytes)
        buffer.put(if (accepted) 1.toByte() else 0.toByte())
        buffer.putShort(reasonBytes.size.toShort())
        buffer.put(reasonBytes)
        return buffer.array()
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): TransferResponsePayload {
            val buffer = ByteBuffer.wrap(bytes)
            val idLen = buffer.short.toInt() and 0xFFFF
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val transferId = String(idBytes, StandardCharsets.UTF_8)

            val accepted = buffer.get() == 1.toByte()

            val reasonLen = buffer.short.toInt() and 0xFFFF
            val reasonBytes = ByteArray(reasonLen)
            buffer.get(reasonBytes)
            val reason = String(reasonBytes, StandardCharsets.UTF_8)

            return TransferResponsePayload(transferId, accepted, reason)
        }
    }
}

data class TransferCancelPayload(
    val transferId: String,
    val reason: String
) {
    fun toByteArray(): ByteArray {
        val idBytes = transferId.toByteArray(StandardCharsets.UTF_8)
        val reasonBytes = reason.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(2 + idBytes.size + 2 + reasonBytes.size)
        buffer.putShort(idBytes.size.toShort())
        buffer.put(idBytes)
        buffer.putShort(reasonBytes.size.toShort())
        buffer.put(reasonBytes)
        return buffer.array()
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): TransferCancelPayload {
            val buffer = ByteBuffer.wrap(bytes)
            val idLen = buffer.short.toInt() and 0xFFFF
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val transferId = String(idBytes, StandardCharsets.UTF_8)

            val reasonLen = buffer.short.toInt() and 0xFFFF
            val reasonBytes = ByteArray(reasonLen)
            buffer.get(reasonBytes)
            val reason = String(reasonBytes, StandardCharsets.UTF_8)

            return TransferCancelPayload(transferId, reason)
        }
    }
}

data class TransferChunkPayload(
    val chunk: DataChunk
) {
    fun toByteArray(): ByteArray {
        val idBytes = chunk.transferId.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(2 + idBytes.size + 4 + 4 + 8 + 4 + chunk.payload.size)
        buffer.putShort(idBytes.size.toShort())
        buffer.put(idBytes)
        buffer.putInt(chunk.chunkIndex)
        buffer.putInt(chunk.totalChunks)
        buffer.putLong(chunk.crc32Checksum)
        buffer.putInt(chunk.payload.size)
        buffer.put(chunk.payload)
        return buffer.array()
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): DataChunk {
            val buffer = ByteBuffer.wrap(bytes)
            val idLen = buffer.short.toInt() and 0xFFFF
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val transferId = String(idBytes, StandardCharsets.UTF_8)

            val chunkIndex = buffer.int
            val totalChunks = buffer.int
            val crc32Checksum = buffer.long

            val payloadLen = buffer.int
            val payload = ByteArray(payloadLen)
            buffer.get(payload)

            return DataChunk(
                transferId = transferId,
                chunkIndex = chunkIndex,
                totalChunks = totalChunks,
                payload = payload,
                crc32Checksum = crc32Checksum
            )
        }
    }
}
