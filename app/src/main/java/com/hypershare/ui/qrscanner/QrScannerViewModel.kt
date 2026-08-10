package com.hypershare.ui.qrscanner

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.hypershare.db.MessageRepository
import com.hypershare.identity.ContactCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QrScannerUiState(
    val scannedCard: ContactCard? = null,
    val isSignatureValid: Boolean? = null,
    val scanMessage: String? = null,
    val isProcessing: Boolean = false,
    val isTorchEnabled: Boolean = false
)

class QrScannerViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(QrScannerUiState())
    val uiState: StateFlow<QrScannerUiState> = _uiState.asStateFlow()

    private val repository = MessageRepository(app.applicationContext)

    fun toggleTorch() {
        _uiState.value = _uiState.value.copy(isTorchEnabled = !_uiState.value.isTorchEnabled)
    }

    fun onPayloadScanned(rawPayload: String) {
        if (_uiState.value.isProcessing) return
        val trimmed = rawPayload.trim()
        if (trimmed.isEmpty()) return

        _uiState.value = _uiState.value.copy(isProcessing = true)

        viewModelScope.launch {
            val card = ContactCard.jsonToCard(trimmed)
            if (card == null) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isSignatureValid = false,
                    scanMessage = "Invalid HyperShare QR payload format."
                )
                return@launch
            }

            val isValid = card.verifyCardSignature()
            if (isValid) {
                val pubKeyBase64 = java.util.Base64.getEncoder().encodeToString(card.publicKey)
                withContext(Dispatchers.IO) {
                    repository.savePeer(card.userId, card.displayName, isTrusted = true, hasPeerAccepted = true, publicKey = pubKeyBase64)
                }
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    scannedCard = card,
                    isSignatureValid = true,
                    scanMessage = "Identity Verified & Trusted: ${card.displayName}"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    scannedCard = card,
                    isSignatureValid = false,
                    scanMessage = "Signature Verification Failed! Untrusted Card."
                )
            }
        }
    }

    fun decodeGalleryPhoto(uri: Uri) {
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            isSignatureValid = false,
                            scanMessage = "Unable to load image from gallery."
                        )
                    }
                    return@launch
                }

                val width = bitmap.width
                val height = bitmap.height
                val intArray = IntArray(width * height)
                bitmap.getPixels(intArray, 0, width, 0, 0, width, height)

                val source = RGBLuminanceSource(width, height, intArray)
                val binarizer = HybridBinarizer(source)
                val binaryBitmap = BinaryBitmap(binarizer)
                val reader = MultiFormatReader()
                val result = reader.decode(binaryBitmap)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    onPayloadScanned(result.text)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        isSignatureValid = false,
                        scanMessage = "No valid QR code found in selected photo."
                    )
                }
            }
        }
    }

    fun resetScanState() {
        _uiState.value = QrScannerUiState()
    }
}
