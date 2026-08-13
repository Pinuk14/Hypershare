package com.hypershare.service

import com.hypershare.model.PacketType
import com.hypershare.protocol.PacketBuilder
import com.hypershare.protocol.PacketParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class LanSocketManagerTest {

    @Test
    fun testHelloPacketBuildAndParse() {
        val senderId = "HYPERSHARE-DEVICE-ALPHA"
        val helloPacket = PacketBuilder.buildHelloPacket(
            sourcePeerId = senderId,
            destPeerId = "192.168.1.105",
            seqNum = 1,
            publicKeyBytes = senderId.toByteArray(StandardCharsets.UTF_8)
        )
        val packetBytes = PacketBuilder.serializePacket(helloPacket)

        val parsedPacket = PacketParser.parsePacket(packetBytes)
        assertEquals(PacketType.HELLO, parsedPacket.header.type)
        assertEquals(senderId, parsedPacket.header.sourcePeerId)
    }

    @Test
    fun testMsgPacketBuildAndParse() {
        val senderId = "HYPERSHARE-DEVICE-ALPHA"
        val receiverId = "192.168.1.105"
        val textPayload = "Hello from Device A over local WiFi!"

        val msgPacket = PacketBuilder.buildMessagePacket(
            sourcePeerId = senderId,
            destPeerId = receiverId,
            seqNum = 2,
            text = textPayload
        )
        val packetBytes = PacketBuilder.serializePacket(msgPacket)

        val parsedPacket = PacketParser.parsePacket(packetBytes)
        assertEquals(PacketType.MSG, parsedPacket.header.type)
        assertEquals(textPayload, String(parsedPacket.payload, StandardCharsets.UTF_8))
    }
}
