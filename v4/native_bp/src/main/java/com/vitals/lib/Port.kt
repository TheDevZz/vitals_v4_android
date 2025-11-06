package com.vitals.lib

import android.content.Context
import android.graphics.PointF
import com.vitals.sdk.parcel.BaseFeature
import com.vitals.sdk.parcel.Gender
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer

object Port {
    init {
        System.loadLibrary("vitals_bp")
    }

    class MeasureResult {
        var hr: Double = 0.0
        var hrv: Double = 0.0
        var rr: Double = 0.0
        var spo2: Double = 0.0

        var stress: Double = 0.0

        var hbp: Double = 0.0
        var lbp: Double = 0.0

        var ratio: Double = 0.0

        companion object {
            fun create(ptr: Long): MeasureResult {
                val r = MeasureResult()
                r.hr = getHr(ptr)
                r.hrv = getHrv(ptr)
                r.rr = getRR(ptr)
                r.spo2 = getSpo2(ptr)
                r.stress = getStress(ptr)
                r.hbp = getHBP(ptr)
                r.lbp = getLBP(ptr)
                r.ratio = getRatio(ptr)
                release(ptr)
                return r
            }

            fun createOrNull(ptr: Long): MeasureResult? {
                return if (ptr != 0L) create(ptr) else null
            }

            private external fun getHr(ptr: Long): Double
            private external fun getHrv(ptr: Long): Double
            private external fun getRR(ptr: Long): Double
            private external fun getSpo2(ptr: Long): Double
            private external fun getStress(ptr: Long): Double
            private external fun getHBP(ptr: Long): Double
            private external fun getLBP(ptr: Long): Double
            private external fun getRatio(ptr: Long): Double
            private external fun release(ptr: Long)
        }

        override fun toString(): String {
            return "MeasureResult(hr=$hr, hrv=$hrv, rr=$rr, spo2=$spo2, stress=$stress, hbp=$hbp, lbp=$lbp, ratio=$ratio)"
        }
    }

    fun processPixelsV2(
        pixels: DoubleArray,
        shape: IntArray,
        fps: Double,
        modelsDir: String
    ): MeasureResult? {
        if (pixels.isEmpty() || shape.isEmpty() || shape.any { it <= 0 }) {
            throw Exception("has empty data")
        }
        val ptr = nativeProcessPixelsV2(pixels, shape, fps, modelsDir)
        return MeasureResult.createOrNull(ptr)
    }

    fun processPixelsV2(
        pixels: DoubleArray, shape: IntArray, fps: Double, modelsDir: String,
        age: Int, gender: Int, height: Double, weight: Double
    ): MeasureResult? {
        if (pixels.isEmpty() || shape.isEmpty() || shape.any { it <= 0 }) {
            throw Exception("has empty data")
        }
        val ptr =
            nativeProcessPixelsV2Fea(pixels, shape, fps, modelsDir, age, gender, height, weight)
        return MeasureResult.createOrNull(ptr)
    }

    fun predictBaseFea(landmarks: List<PointF>): BaseFeature {
        val landmarksArray = FloatArray(landmarks.size * 2)
        for (i in landmarks.indices) {
            landmarksArray[i * 2] = landmarks[i].x
            landmarksArray[i * 2 + 1] = landmarks[i].y
        }
        val res = nativePredictBaseFea(landmarksArray, intArrayOf(landmarks.size, 2))
        val gender = res[0].toInt()
        val age = res[1].toInt()
        val bmi = res[2].toInt()
        val height = if (gender == 1) 1.74 else 1.61
        val weight = bmi * height * height
        return BaseFeature(age, Gender.fromValue(gender), height, weight)
    }

    private external fun nativeProcessPixelsV2(
        pixels: DoubleArray,
        shape: IntArray,
        fps: Double,
        modelsDir: String
    ): Long

    private external fun nativeProcessPixelsV2Fea(
        pixels: DoubleArray,
        shape: IntArray,
        fps: Double,
        modelsDir: String,
        age: Int,
        gender: Int,
        height: Double,
        weight: Double
    ): Long

    private external fun nativePredictBaseFea(
        landmarks: FloatArray,
        landmarksShape: IntArray
    ): FloatArray

    // external fun nativeSetupTest(testDir: String): Boolean
    // external fun nativeRunTest(modelsDir: String)

    // Store binary data with tag
    fun storeBinaryData(tag: String, buffer: ByteBuffer) {
        if (!buffer.isDirect) {
            throw IllegalArgumentException("ByteBuffer must be direct")
        }
        nativeStoreBinaryData(tag, buffer, buffer.remaining())
    }

    fun storeBinaryData(tag: String, stream: InputStream) {
        val bytes = stream.readBytes()
        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.put(bytes)
        buffer.flip()
        nativeStoreBinaryData(tag, buffer, bytes.size)
    }

    // Remove binary data by tag
    fun removeBinaryData(tag: String) {
        nativeRemoveBinaryData(tag)
    }

    private external fun nativeStoreBinaryData(tag: String, buffer: ByteBuffer, size: Int)
    private external fun nativeRemoveBinaryData(tag: String)

    // fun copyBPModels(context: Context): String {
    //     val modelNameList = arrayListOf(
    //         "ep-5_test_loss-1.1068_test_acc-0.4359.pt",
    //         "HBP_06302023__model=ResCNN1D3_nclasses=1_pretrained=0_loss15.12446915_epoch3.pt",
    //         "LBP_06302023__model=ResCNN1D3_nclasses=1_pretrained=0_loss10.89517215_epoch268.pt",
    //         "model=ResCNN1D_nclasses=3_pretrained=0_class0.pt",
    //         "model=ResCNN1D_nclasses=3_pretrained=0_class1.pt",
    //         "model=ResCNN1D_nclasses=3_pretrained=0_class2.pt",
    //         "sig2_34_20240325204004925_tmp_28ccd766fed319ae2b93c441647597ad_2.csv",
    //         "bp.pt"
    //     )
    //     val modelsDir = File(context.cacheDir, "models")
    //     modelsDir.mkdirs()
    //     val assets = context.assets
    //     modelNameList.forEach {
    //         val output = File(modelsDir, it)
    //         if (!output.exists()) {
    //             try {
    //                 assets.open(it).copyTo(File(modelsDir, it).outputStream())
    //             } catch (e: Exception) {
    //                 e.printStackTrace()
    //             }
    //         }
    //     }
    //
    //     return modelsDir.path
    // }
}