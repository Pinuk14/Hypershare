package com.hypershare.model

data class DataChunk(
    val transferId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val payload: ByteArray,
    val crc32Checksum: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataChunk

        if (transferId != other.transferId) return false
        if (chunkIndex != other.chunkIndex) return false
        if (totalChunks != other.totalChunks) return false
        if (!payload.contentEquals(other.payload)) return false
        if (crc32Checksum != other.crc32Checksum) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transferId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + crc32Checksum.hashCode()
        return result
    }
}
