package com.hypershare.application

import java.io.ByteArrayOutputStream

class StreamController {

    private val ringBuffer = ByteArrayOutputStream(1024 * 1024) // 1 MB ring buffer

    fun writeFrame(frameData: ByteArray) {
        synchronized(ringBuffer) {
            ringBuffer.write(frameData)
        }
    }

    fun readAvailableBytes(): ByteArray {
        synchronized(ringBuffer) {
            val bytes = ringBuffer.toByteArray()
            ringBuffer.reset()
            return bytes
        }
    }
}
