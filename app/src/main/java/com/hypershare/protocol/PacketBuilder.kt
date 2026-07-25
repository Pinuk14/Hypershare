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
