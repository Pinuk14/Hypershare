package com.hypershare.protocol

import com.hypershare.model.PacketType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Unit tests verifying serialization, parsing, and enum mapping
 * for group packet types 0x20–0x29.
 */
class GroupPacketSerializationTest {

    // --- Enum Mapping Tests ---

    @Test
    fun `GROUP_CREATE maps to code 0x20`() {
        assertEquals(0x20.toByte(), PacketType.GROUP_CREATE.code)
    }

    @Test
    fun `GROUP_JOIN_ACK maps to code 0x21`() {
        assertEquals(0x21.toByte(), PacketType.GROUP_JOIN_ACK.code)
    }

    @Test
    fun `GROUP_MSG maps to code 0x22`() {
        assertEquals(0x22.toByte(), PacketType.GROUP_MSG.code)
    }

    @Test
    fun `GROUP_MSG_ACK maps to code 0x23`() {
        assertEquals(0x23.toByte(), PacketType.GROUP_MSG_ACK.code)
    }

    @Test
    fun `GROUP_MEMBER_ADD maps to code 0x24`() {
        assertEquals(0x24.toByte(), PacketType.GROUP_MEMBER_ADD.code)
    }

    @Test
    fun `GROUP_MEMBER_REMOVE maps to code 0x25`() {
        assertEquals(0x25.toByte(), PacketType.GROUP_MEMBER_REMOVE.code)
    }

    @Test
    fun `GROUP_KICKED maps to code 0x26`() {
        assertEquals(0x26.toByte(), PacketType.GROUP_KICKED.code)
    }

    @Test
    fun `GROUP_DISSOLVE maps to code 0x27`() {
        assertEquals(0x27.toByte(), PacketType.GROUP_DISSOLVE.code)
    }

    @Test
    fun `GROUP_RESTORE maps to code 0x28`() {
        assertEquals(0x28.toByte(), PacketType.GROUP_RESTORE.code)
    }

    @Test
    fun `GROUP_MSG_READ maps to code 0x29`() {
        assertEquals(0x29.toByte(), PacketType.GROUP_MSG_READ.code)
    }

    // --- fromCode reverse mapping ---

    @Test
    fun `fromCode 0x20 returns GROUP_CREATE`() {
        assertEquals(PacketType.GROUP_CREATE, PacketType.fromCode(0x20.toByte()))
    }

    @Test
    fun `fromCode 0x29 returns GROUP_MSG_READ`() {
        assertEquals(PacketType.GROUP_MSG_READ, PacketType.fromCode(0x29.toByte()))
    }

    @Test
    fun `fromCode for all group types returns non-null`() {
        for (code in 0x20..0x29) {
            assertNotNull(
                "fromCode(0x${code.toString(16)}) should return a valid PacketType",
                PacketType.fromCode(code.toByte())
            )
        }
    }

    // --- Serialization roundtrip tests ---

    @Test
    fun `buildGroupPacket creates valid packet with JSON payload`() {
        val json = """{"groupId":"test-123","groupName":"TestGroup"}"""
        val packet = PacketBuilder.buildGroupPacket(
            sourcePeerId = "source-abc",
            destPeerId = "dest-xyz",
            seqNum = 42,
            type = PacketType.GROUP_CREATE,
            jsonPayload = json
        )

        assertEquals(PacketType.GROUP_CREATE, packet.header.type)
        assertEquals("source-abc", packet.header.sourcePeerId)
        assertEquals("dest-xyz", packet.header.destinationPeerId)
        assertEquals(42, packet.header.sequenceNumber)
        assertEquals(json, String(packet.payload, StandardCharsets.UTF_8))
    }

    @Test
    fun `serializePacket and parsePacket roundtrip for GROUP_MSG`() {
        val json = """{"groupId":"g1","messageId":"m1","senderId":"s1","senderDisplayName":"Alice","timestamp":1000,"text":"Hello group!"}"""
        val originalPacket = PacketBuilder.buildGroupPacket(
            sourcePeerId = "peer-a",
            destPeerId = "peer-b",
            seqNum = 100,
            type = PacketType.GROUP_MSG,
            jsonPayload = json
        )

        val serialized = PacketBuilder.serializePacket(originalPacket)
        val parsed = PacketParser.parsePacket(serialized)

        assertEquals(PacketType.GROUP_MSG, parsed.header.type)
        assertEquals("peer-a", parsed.header.sourcePeerId)
        assertEquals("peer-b", parsed.header.destinationPeerId)
        assertEquals(100, parsed.header.sequenceNumber)
        assertEquals(json, String(parsed.payload, StandardCharsets.UTF_8))
    }

    @Test
    fun `serializePacket and parsePacket roundtrip for GROUP_KICKED`() {
        val json = """{"groupId":"kicked-group","reason":"Removed by admin"}"""
        val packet = PacketBuilder.buildGroupPacket(
            sourcePeerId = "admin-id",
            destPeerId = "kicked-user",
            seqNum = 55,
            type = PacketType.GROUP_KICKED,
            jsonPayload = json
        )

        val serialized = PacketBuilder.serializePacket(packet)
        val parsed = PacketParser.parsePacket(serialized)

        assertEquals(PacketType.GROUP_KICKED, parsed.header.type)
        assertArrayEquals(packet.payload, parsed.payload)
    }

    @Test
    fun `serializePacket and parsePacket roundtrip for GROUP_RESTORE`() {
        val json = """{"groupId":"restore-group","activeMemberIds":["u1","u2","u3"]}"""
        val packet = PacketBuilder.buildGroupPacket(
            sourcePeerId = "restorer",
            destPeerId = "restored-peer",
            seqNum = 999,
            type = PacketType.GROUP_RESTORE,
            jsonPayload = json
        )

        val serialized = PacketBuilder.serializePacket(packet)
        val parsed = PacketParser.parsePacket(serialized)

        assertEquals(PacketType.GROUP_RESTORE, parsed.header.type)
        assertEquals(json, String(parsed.payload, StandardCharsets.UTF_8))
    }

    @Test
    fun `all group packet types survive serialize-parse roundtrip`() {
        val groupTypes = listOf(
            PacketType.GROUP_CREATE,
            PacketType.GROUP_JOIN_ACK,
            PacketType.GROUP_MSG,
            PacketType.GROUP_MSG_ACK,
            PacketType.GROUP_MEMBER_ADD,
            PacketType.GROUP_MEMBER_REMOVE,
            PacketType.GROUP_KICKED,
            PacketType.GROUP_DISSOLVE,
            PacketType.GROUP_RESTORE,
            PacketType.GROUP_MSG_READ
        )

        for ((i, type) in groupTypes.withIndex()) {
            val packet = PacketBuilder.buildGroupPacket(
                sourcePeerId = "src",
                destPeerId = "dst",
                seqNum = i,
                type = type,
                jsonPayload = """{"test":$i}"""
            )
            val parsed = PacketParser.parsePacket(PacketBuilder.serializePacket(packet))
            assertEquals("Roundtrip failed for $type", type, parsed.header.type)
        }
    }
}
