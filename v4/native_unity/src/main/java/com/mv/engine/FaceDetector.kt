package com.mv.engine

import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.annotation.Keep

@Keep
class FaceDetector : Component() {

    @Keep
    private var nativeHandler: Long
    private val mNativeLock = Any()

    init {
        nativeHandler = createInstance()
    }

    override fun createInstance(): Long = allocate()

    fun loadModel(assetsManager: AssetManager): Int {
        synchronized(mNativeLock) {
            return if (nativeHandler != 0L) {
                nativeLoadModel(assetsManager)
            } else {
                -1
            }
        }
    }

    fun detect(bitmap: Bitmap): List<FaceBox> {
        synchronized(mNativeLock) {
            if (nativeHandler == 0L) return emptyList()
            return when (bitmap.config) {
                Bitmap.Config.ARGB_8888 -> nativeDetectBitmap(bitmap) ?: emptyList()
                else -> throw IllegalArgumentException("Invalid bitmap config value")
            }
        }
    }

    fun detect(
        yuv: ByteArray,
        previewWidth: Int,
        previewHeight: Int,
        orientation: Int
    ): List<FaceBox> {
        if (previewWidth * previewHeight * 3 / 2 != yuv.size) {
            throw IllegalArgumentException("Invalid yuv data")
        }
        synchronized(mNativeLock) {
            if (nativeHandler == 0L) return emptyList()
            return nativeDetectYuv(yuv, previewWidth, previewHeight, orientation)
        }
    }

    override fun destroy() {
        synchronized(mNativeLock) {
            if (nativeHandler != 0L) {
                deallocate()
                nativeHandler = 0L
            }
        }
    }

    //////////////////////////////// Native ////////////////////////////////////
    @Keep
    private external fun allocate(): Long

    @Keep
    private external fun deallocate()

    @Keep
    private external fun nativeLoadModel(assetsManager: AssetManager): Int

    @Keep
    private external fun nativeDetectBitmap(bitmap: Bitmap): List<FaceBox>?

    @Keep
    private external fun nativeDetectYuv(
        yuv: ByteArray,
        previewWidth: Int,
        previewHeight: Int,
        orientation: Int
    ): List<FaceBox>


}