package com.hypershare.protocol

import com.hypershare.model.Packet
import com.hypershare.model.PacketHeader
import com.hypershare.model.PacketType
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object PacketBuilder {

    fun buildHelloPacket(sourcePeerId: String, destPeerId: String, seqNum: Int, publicKeyBytes: ByteArray): Packet {
        val header = PacketHeader(
            type = PacketType.HELLO,
            sequenceNumber = seqNum,
            sourcePeerId = sourcePeerId,
            destinationPeerId = destPeerId
        )
        return Packet(header, publicKeyBytes)
    }

    fun buildMessagePacket(sourcePeerId: String, destPeerId: String, seqNum: Int, text: String): Packet {
        val header = PacketHeader(
            type = PacketType.MSG,
            sequenceNumber = seqNum,
            sourcePeerId = sourcePeerId,
            destinationPeerId = destPeerId
        )
        return Packet(header, text.toByteArray(StandardCharsets.UTF_8))
    }

    fun buildDataPacket(sourcePeerId: String, destPeerId: String, seqNum: Int, chunkPayload: ByteArray): Packet {
        val header = PacketHeader(
            type = PacketType.DATA,
            sequenceNumber = seqNum,
            sourcePeerId = sourcePeerId,
            destinationPeerId = destPeerId
        )
        return Packet(header, chunkPayload)
    }

    fun buildTransferInitPacket(
        sourcePeerId: String,
        destPeerId: String,
        seqNum: Int,
        payload: TransferInitPayload
    ): Packet {
        val header = PacketHeader(
            type = PacketType.TRANSFER_INIT,
            sequenceNumber = seqNum,
            sourcePeerId = sourcePeerId,
            destinationPeerId = destPeerId
        )
        return Packet(header, payload.toByteArray())
    }

    fun buildDataChunkPacket(
        sourcePeerId: String,
        destPeerId: String,
        seqNum: Int,
        chunk: com.hypershare.model.DataChunk
    ): Packet {
        val header = PacketHeader(
            type = PacketType.DATA,
            sequenceNumber = seqNum,
            sourcePeerId = sourcePeerId,
            destinationPeerId = destPeerId
        )
        return Packet(header, TransferChunkPayload(chunk).toByteArray())
    }

    fun buildChunkAckPacket(
        sourcePeerId: String,
        destPeerId: String,
        seqNum: Int,
        transferId: String,
        chunkIndex: Int
    ): Packet {
        val header = PacketHeader(
            type = PacketType.TRANSFER_CHUNK_ACK,
            sequenceNumber = seqNum,
            sourcePeerId = sourcePeerId,
            destinationPeerId = destPeerId
        )
        return Packet(header, ChunkAckPayload(transferId, chunkIndex).toByteArray())
    }

    fun buildTransferResponsePacket(
        sourcePeerId: String,
        destPeerId: String,
        seqNum: Int,
        transferId: String,
        accepted: Boolean,
        reason: String = ""
    ): Packet {
        val type = if (accepted) PacketType.TRANSFER_ACCEPT else PacketType.TRANSFER_REJECT
        val header = PacketHeader(
            type = type,
            sequenceNumber = seqNum,
            sourcePeerId = sourcePeerId,
            destinationPeerId = destPeerId
        )
        return Packet(header, TransferResponsePayload(transferId, accepted, reason).toByteArray())
    }

    fun buildTransferCompletePacket(
        sourcePeerId: String,
        destPeerId: String,
        seqNum: Int,
        transferId: String
    ): Packet {
        val header = PacketHeader(
            type = PacketType.TRANSFER_COMPLETE,
            sequenceNumber = seqNum,
            sourcePeerId = sourcePeerId,
            destinationPeerId = destPeerId
        )
        return Packet(header, transferId.toByteArray(StandardCharsets.UTF_8))
    }

    fun buildTransferCancelPacket(
        sourcePeerId: String,
        destPeerId: String,
        seqNum: Int,
        transferId: String,
        reason: String
    ): Packet {
        val header = PacketHeader(
            type = PacketType.TRANSFER_CANCEL,
            sequenceNumber = seqNum,
            sourcePeerId = sourcePeerId,
            destinationPeerId = destPeerId
        )
        return Packet(header, TransferCancelPayload(transferId, reason).toByteArray())
    }

    /**
     * Constructs a group communication packet with a JSON-serialized payload.
     * Used for all GROUP_* packet types (0x20–0x29).
     */
    fun buildGroupPacket(
        sourcePeerId: String,
        destPeerId: String,
        seqNum: Int,
        type: PacketType,
        jsonPayload: String
    ): Packet {
        val header = PacketHeader(
            type = type,
            sequenceNumber = seqNum,
            sourcePeerId = sourcePeerId,
            destinationPeerId = destPeerId
        )
        return Packet(header, jsonPayload.toByteArray(StandardCharsets.UTF_8))
    }

    fun serializePacket(packet: Packet): ByteArray {
        val srcBytes = packet.header.sourcePeerId.toByteArray(StandardCharsets.UTF_8)
        val dstBytes = packet.header.destinationPeerId.toByteArray(StandardCharsets.UTF_8)

        val bufferLength = 1 + 1 + 1 + 4 + 2 + srcBytes.size + 2 + dstBytes.size + 4 + packet.payload.size
        val buffer = ByteBuffer.allocate(bufferLength)

        buffer.put(packet.header.version)
        buffer.put(packet.header.type.code)
        buffer.put(packet.header.ttl)
        buffer.putInt(packet.header.sequenceNumber)

        buffer.putShort(srcBytes.size.toShort())
        buffer.put(srcBytes)

        buffer.putShort(dstBytes.size.toShort())
        buffer.put(dstBytes)

        buffer.putInt(packet.payload.size)
        buffer.put(packet.payload)

        return buffer.array()
    }
}
