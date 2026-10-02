package com.rotacerta.entregador.utils

import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Decodificador de QR/código de barras via ZXing - usado na leitura ao vivo do código de
 * rastreio pela câmera.
 *
 * Trocamos o ML Kit Barcode Scanning por isto porque a biblioteca nativa do ML Kit
 * (libbarhopper_v3.so) não é compatível com a exigência de 16KB de page size do Google
 * Play, e não existe (até agora) uma versão publicada que corrija isso. ZXing é 100%
 * Java/Kotlin, sem biblioteca nativa - não tem esse problema.
 */
object ZxingDecoder {

    private val reader = MultiFormatReader()

    /** Decodifica um frame ao vivo da câmera (CameraX). Retorna null se não achar nada nesse frame. */
    fun decode(imageProxy: ImageProxy): String? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride

        // Alguns aparelhos preenchem cada linha com bytes extras de padding (rowStride > width).
        // Se ignorarmos isso, a imagem "desalinha" e a leitura falha ou fica instável.
        val data = if (rowStride == width) {
            val buffer = plane.buffer
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } else {
            val buffer = plane.buffer
            val rowBytes = ByteArray(rowStride)
            val out = ByteArray(width * height)
            for (row in 0 until height) {
                buffer.position(row * rowStride)
                buffer.get(rowBytes, 0, minOf(rowStride, buffer.remaining()))
                System.arraycopy(rowBytes, 0, out, row * width, width)
            }
            out
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val (rotatedData, rotatedWidth, rotatedHeight) = when (rotation) {
            90 -> Triple(rotateYuv90(data, width, height), height, width)
            270 -> Triple(rotateYuv270(data, width, height), height, width)
            180 -> Triple(rotateYuv180(data, width, height), width, height)
            else -> Triple(data, width, height)
        }

        return try {
            val source = PlanarYUVLuminanceSource(
                rotatedData, rotatedWidth, rotatedHeight, 0, 0, rotatedWidth, rotatedHeight, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            reader.decodeWithState(bitmap).text
        } catch (e: NotFoundException) {
            null // normal: a maioria dos frames não tem código nenhum
        } catch (e: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun rotateYuv90(data: ByteArray, width: Int, height: Int): ByteArray {
        val output = ByteArray(data.size)
        var i = 0
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                output[i++] = data[y * width + x]
            }
        }
        return output
    }

    private fun rotateYuv270(data: ByteArray, width: Int, height: Int): ByteArray {
        val output = ByteArray(data.size)
        var i = 0
        for (x in width - 1 downTo 0) {
            for (y in 0 until height) {
                output[i++] = data[y * width + x]
            }
        }
        return output
    }

    private fun rotateYuv180(data: ByteArray, width: Int, height: Int): ByteArray {
        val output = ByteArray(data.size)
        val size = width * height
        for (i in 0 until size) {
            output[i] = data[size - 1 - i]
        }
        return output
    }
}
