package com.example.ui.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {
    /**
     * Encodes arbitrary text string into a standard, fully scannable QR Code bit matrix using ZXing.
     */
    fun encodeToMatrix(text: String): Array<BooleanArray>? {
        if (text.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val matrix = Array(height) { BooleanArray(width) }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    matrix[y][x] = bitMatrix.get(x, y)
                }
            }
            matrix
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@Composable
fun QrCodeView(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    darkColor: Color = Color(0xFF0F172A),
    lightColor: Color = Color.White
) {
    val matrix = remember(text) { QrCodeGenerator.encodeToMatrix(text) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(lightColor),
        contentAlignment = Alignment.Center
    ) {
        if (matrix != null && matrix.isNotEmpty()) {
            val matrixWidth = matrix[0].size
            val matrixHeight = matrix.size

            Canvas(modifier = Modifier.size(size - 24.dp)) {
                val cellWidth = this.size.width / matrixWidth
                val cellHeight = this.size.height / matrixHeight

                for (r in 0 until matrixHeight) {
                    for (c in 0 until matrixWidth) {
                        if (matrix[r][c]) {
                            drawRect(
                                color = darkColor,
                                topLeft = Offset(c * cellWidth, r * cellHeight),
                                size = Size(cellWidth + 0.3f, cellHeight + 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
