package com.hypershare.protocol

import com.hypershare.model.Packet
import com.hypershare.model.PacketHeader
import com.hypershare.model.PacketType
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object PacketParser {

    fun parsePacket(bytes: ByteArray): Packet {
        val buffer = ByteBuffer.wrap(bytes)

        val version = buffer.get()
        val typeCode = buffer.get()
        val ttl = buffer.get()
        val seqNum = buffer.int

        val packetType = PacketType.fromCode(typeCode)
            ?: throw IllegalArgumentException("Unknown packet type code: $typeCode")

        // Use and(0xFFFF) to treat the 2-byte length as unsigned — prevents
        // NegativeArraySizeException when the high-bit of the short is set.
        val srcLen = buffer.short.toInt() and 0xFFFF
        val srcBytes = ByteArray(srcLen)
        buffer.get(srcBytes)
        val srcPeerId = String(srcBytes, StandardCharsets.UTF_8)

        val dstLen = buffer.short.toInt() and 0xFFFF
        val dstBytes = ByteArray(dstLen)
        buffer.get(dstBytes)
        val dstPeerId = String(dstBytes, StandardCharsets.UTF_8)

        val payloadLen = buffer.int
        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        val header = PacketHeader(
            version = version,
            type = packetType,
            ttl = ttl,
            sequenceNumber = seqNum,
            sourcePeerId = srcPeerId,
            destinationPeerId = dstPeerId
        )

        return Packet(header, payload)
    }
}
