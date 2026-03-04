package com.vitals.sdk.lib

import android.graphics.Bitmap

class FaceLandmarker {
    companion object {
        init {
            System.loadLibrary("vitals_unity")
        }
    }

    private var mNativePtr: Long = 0
    private val mNativeLock = Any()

    private enum class RunningMode {
        IMAGE,
        VIDEO,
    }

    fun create(
        faceDetectionModelPath: String,
        faceLandmarkerModelPath: String,
    ) {
        synchronized(mNativeLock) {
            if (mNativePtr == 0L) {
                mNativePtr = createFaceLandmarker(faceDetectionModelPath, faceLandmarkerModelPath)
            }
        }
    }

    fun release() {
        synchronized(mNativeLock) {
            if (mNativePtr != 0L) {
                releaseFaceLandmarker(mNativePtr)
                mNativePtr = 0L
            }
        }
    }

    fun setNumFaces(count: Int) {
        synchronized(mNativeLock) {
            if (mNativePtr != 0L) {
                nativeSetNumFaces(mNativePtr, count)
            }
        }
    }

    fun detect(image: Image): List<List<NormalizedLandmark>> {
        val multiFaceLandmarksHolderPtr = synchronized(mNativeLock) {
            if (mNativePtr != 0L) {
                nativeDetect(mNativePtr, image.data, image.width, image.height)
            } else {
                0L
            }
        }
        if (multiFaceLandmarksHolderPtr == 0L) {
            return emptyList()
        }
        val multiFaceLandmarks = ArrayList<List<NormalizedLandmark>>()
        var id = 0
        while(true) {
            val array = nativeGetLandmarkArray(multiFaceLandmarksHolderPtr, id++)
            if (array == null || array.isEmpty()) {
                break
            }
            val size: Int = array.size / 2
            val landmarks = ArrayList<NormalizedLandmark>(size)
            for (i in 0 until size) {
                val offset = i * 2
                landmarks.add(NormalizedLandmark(array[offset], array[offset + 1]))
            }
            multiFaceLandmarks.add(landmarks)
        }
        nativeReleaseMultiFaceLandmarksHolder(multiFaceLandmarksHolderPtr)
        return multiFaceLandmarks
    }

    fun detect(bitmap: Bitmap): List<List<NormalizedLandmark>> {
        val multiFaceLandmarksHolderPtr = synchronized(mNativeLock) {
            if (mNativePtr != 0L) {
                nativeDetectBitmap(mNativePtr, bitmap)
            } else {
                0L
            }
        }
        if (multiFaceLandmarksHolderPtr == 0L) {
            return emptyList()
        }
        val multiFaceLandmarks = ArrayList<List<NormalizedLandmark>>()
        var id = 0
        while(true) {
            val array = nativeGetLandmarkArray(multiFaceLandmarksHolderPtr, id++)
            if (array == null || array.isEmpty()) {
                break
            }
            val size: Int = array.size / 2
            val landmarks = ArrayList<NormalizedLandmark>(size)
            for (i in 0 until size) {
                val offset = i * 2
                landmarks.add(NormalizedLandmark(array[offset], array[offset + 1]))
            }
            multiFaceLandmarks.add(landmarks)
        }
        nativeReleaseMultiFaceLandmarksHolder(multiFaceLandmarksHolderPtr)
        return multiFaceLandmarks
    }

    private external fun createFaceLandmarker(
        faceDetectionModelPath: String,
        faceLandmarkerModelPath: String,
    ): Long

    private external fun releaseFaceLandmarker(ptr: Long)

    private external fun nativeSetNumFaces(ptr: Long, numFaces: Int)

    private external fun nativeDetect(ptr: Long, data: ByteArray, width: Int, height: Int): Long

    private external fun nativeDetectBitmap(ptr: Long, bitmap: Bitmap): Long

    private external fun nativeGetLandmarkArray(ptr: Long, id: Int): FloatArray?

    private external fun nativeReleaseMultiFaceLandmarksHolder(ptr: Long)


    data class NormalizedLandmark(var x: Float, var y: Float) {
        fun x() = x
        fun y() = y

        companion object {
            fun create(x: Float, y: Float): NormalizedLandmark {
                return  NormalizedLandmark(x, y)
            }
        }
    }
}