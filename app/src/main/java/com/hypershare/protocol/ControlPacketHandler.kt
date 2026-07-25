package com.hypershare.protocol

import com.hypershare.model.Packet
import com.hypershare.model.PacketType

class ControlPacketHandler {

    fun handleControlPacket(packet: Packet, onHello: (String, ByteArray) -> Unit, onAck: (Int) -> Unit) {
        when (packet.header.type) {
            PacketType.HELLO -> {
                onHello(packet.header.sourcePeerId, packet.payload)
            }
            PacketType.ACK -> {
                val ackSeq = packet.header.sequenceNumber
                onAck(ackSeq)
            }
            PacketType.RREQ -> {
                // Route Request logic
            }
            PacketType.RREP -> {
                // Route Reply logic
            }
            PacketType.RERR -> {
                // Route Error logic
            }
            else -> {
                // Not a control packet
            }
        }
    }
}
