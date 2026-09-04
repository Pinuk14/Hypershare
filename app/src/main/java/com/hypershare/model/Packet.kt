package com.hypershare.model

enum class PacketType(val code: Byte) {
    HELLO(0x01.toByte()),
    ACK(0x02.toByte()),
    MSG(0x03.toByte()),
    DATA(0x04.toByte()),
    STREAM(0x05.toByte()),
    RREQ(0x06.toByte()),
    RREP(0x07.toByte()),
    RERR(0x08.toByte()),
    READ_ACK(0x09.toByte()),
    CONTACT_ACCEPT(0x0A.toByte()),

    // Group Communication Packet Types (Week 8)
    GROUP_CREATE(0x20.toByte()),
    GROUP_JOIN_ACK(0x21.toByte()),
    GROUP_MSG(0x22.toByte()),
    GROUP_MSG_ACK(0x23.toByte()),
    GROUP_MEMBER_ADD(0x24.toByte()),
    GROUP_MEMBER_REMOVE(0x25.toByte()),
    GROUP_KICKED(0x26.toByte()),
    GROUP_DISSOLVE(0x27.toByte()),
    GROUP_RESTORE(0x28.toByte()),
    GROUP_MSG_READ(0x29.toByte());

    companion object {
        fun fromCode(code: Byte): PacketType? = entries.find { it.code == code }
    }
}

data class PacketHeader(
    val version: Byte = 1,
    val type: PacketType,
    val ttl: Byte = 8,
    val sequenceNumber: Int,
    val sourcePeerId: String,
    val destinationPeerId: String
)

data class Packet(
    val header: PacketHeader,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (header != other.header) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
