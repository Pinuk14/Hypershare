package com.hypershare.ui.components

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrCodeGenerator {

    fun generateBitMatrix(payload: String, matrixDimension: Int = 256): Array<BooleanArray>? {
        if (payload.isEmpty()) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1)
            }
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, matrixDimension, matrixDimension, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val result = Array(height) { BooleanArray(width) }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    result[y][x] = bitMatrix.get(x, y)
                }
            }
            result
        } catch (e: Exception) {
            null
        }
    }
}
