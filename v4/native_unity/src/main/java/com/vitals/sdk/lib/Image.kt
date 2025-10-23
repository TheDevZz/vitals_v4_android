package com.vitals.sdk.lib

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

class Image(val width: Int, val height: Int, val data: ByteArray = ByteArray(width * height * 3)) {

    constructor(image: Image) : this(image.width, image.height, image.data)

    constructor(bitmap: Bitmap) : this(bitmap.width, bitmap.height, bitmap.let {
        return@let if (bitmap.config == Bitmap.Config.ARGB_8888) {
            convertByGetPixels(bitmap)
        } else {
            throw IllegalArgumentException("Only ARGB-configured bitmap is supported, but ${bitmap.config} is not supported")
        }
    })

    companion object {
        fun convertByGetPixels(bitmap: Bitmap): ByteArray {
            val pixelCount = bitmap.width * bitmap.height
            val buffer = IntArray(pixelCount)
            bitmap.getPixels(buffer, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val byteArray = ByteArray(pixelCount * 3)
            // argb to rgb
            for (i in 0 until pixelCount) {
                val dIdx = i * 3
                byteArray[dIdx] = buffer[i].shr(16).and(0xff).toByte()
                byteArray[dIdx + 1] = buffer[i].shr(8).and(0xff).toByte()
                byteArray[dIdx + 2] = buffer[i].and(0xff).toByte()
            }
            return byteArray
        }

        fun convertByIntBuffer(bitmap: Bitmap): ByteArray {
            val pixelCount = bitmap.width * bitmap.height
            val buffer = IntBuffer.allocate(pixelCount)
            bitmap.copyPixelsToBuffer(buffer)
            val byteArray = ByteArray(pixelCount * 3)
//            Log.d("ZZZ", "order: " + buffer.order()) // LITTLE_ENDIAN
            // abgr to rgb
            for (i in 0 until pixelCount) {
                val dIdx = i * 3
                byteArray[dIdx] = buffer[i].and(0xff).toByte()
                byteArray[dIdx + 1] = buffer[i].shr(8).and(0xff).toByte()
                byteArray[dIdx + 2] = buffer[i].shr(16).and(0xff).toByte()
            }
            return byteArray
        }

        fun convertByByteBuffer(bitmap: Bitmap): ByteArray {
            val pixelCount = bitmap.width * bitmap.height
            val buffer = ByteBuffer.allocate(bitmap.byteCount)
            bitmap.copyPixelsToBuffer(buffer)
            val byteArray = ByteArray(pixelCount * 3)
//            Log.d("ZZZ", "order: " + buffer.order()) // BIG_ENDIAN
            // rgba to rgb
            for (i in 0 until pixelCount) {
                val sIdx = i * 4
                val dIdx = i * 3
                byteArray[dIdx] = buffer[sIdx]
                byteArray[dIdx + 1] = buffer[sIdx + 1]
                byteArray[dIdx + 2] = buffer[sIdx + 2]
            }
            return byteArray
        }

        fun convertByByteBufferInt(bitmap: Bitmap): ByteArray {
            val pixelCount = bitmap.width * bitmap.height
            val buffer = ByteBuffer.allocate(bitmap.byteCount)
            bitmap.copyPixelsToBuffer(buffer)
//            Log.d("ZZZ", "order: " + buffer.order()) // BIG_ENDIAN
//            buffer.order(ByteOrder.BIG_ENDIAN) // rgba
//            buffer.order(ByteOrder.LITTLE_ENDIAN) // abgr
            val byteArray = ByteArray(pixelCount * 3)
            buffer.position(0)
            // rgba to rgb
            for (i in 0 until pixelCount) {
                val dIdx = i * 3
                val pixel = buffer.getInt() // rgba
                byteArray[dIdx] = pixel.shr(24).and(0xff).toByte()
                byteArray[dIdx + 1] = pixel.shr(16).and(0xfff).toByte()
                byteArray[dIdx + 2] = pixel.shr(8).and(0xff).toByte()
            }
            return byteArray
        }
    }

}