package com.vitals.lib

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import com.vitals.sdk.lib.ReusePixelsExtractor
import com.vitals.lib.model.VitalsModelManager

object Port {
    init {
        System.loadLibrary("vitals_unity")
    }

    private val pixelsExtractor: ReusePixelsExtractor by lazy {
        val roiIds = arrayOf(10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
            397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
            172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109)
        ReusePixelsExtractor(roiIds.toIntArray())
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

    fun processPixelsV2(pixels: DoubleArray, shape: IntArray, fps: Double, modelsDir: String): MeasureResult? {
        if (pixels.isEmpty() || shape.isEmpty() || shape.any { it <= 0 }) {
            throw Exception("has empty data")
        }
        val ptr = nativeProcessPixelsV2(pixels, shape, fps, modelsDir)
        return MeasureResult.createOrNull(ptr)
    }

    fun processPixelsV2(pixels: DoubleArray, shape: IntArray, fps: Double, modelsDir: String,
                        age: Int, gender: Int, height: Double, weight: Double): MeasureResult? {
        if (pixels.isEmpty() || shape.isEmpty() || shape.any { it <= 0 }) {
            throw Exception("has empty data")
        }
        val ptr = nativeProcessPixelsV2Fea(pixels, shape, fps, modelsDir, age, gender, height, weight)
        return MeasureResult.createOrNull(ptr)
    }

    private external fun nativeProcessPixelsV2(pixels: DoubleArray, shape: IntArray, fps: Double, modelsDir: String): Long
    private external fun nativeProcessPixelsV2Fea(pixels: DoubleArray, shape: IntArray, fps: Double, modelsDir: String, age: Int, gender: Int, height: Double, weight: Double): Long

    class CombinedPixels(
        var v1: List<DoubleArray>? = null,
        var v2: List<DoubleArray>? = null,
    )

    fun extractCombinedPixels(bitmap: Bitmap, landmark: List<Point>): CombinedPixels {
        val st = System.currentTimeMillis()
//            val pixels = Port.extractCombinedPixels(bitmap, landmark)
        val pixels = Port.CombinedPixels()
//            val ps = PixelsExtractor.extract(Image(bitmap), points)
        val ps = pixelsExtractor.extract(bitmap, landmark)
        pixels.v2 = listOf(ps.map { it.toDouble() }.toDoubleArray())
        val et = System.currentTimeMillis()
//            Log.d("FaceHelper", "extractPixels cost: ${et - st} ms")
        //        pixelsStats.push(TimeStats(st, et))
        return pixels
    }

    fun extractPixels(bitmap: Bitmap, landmark: List<Point>): List<DoubleArray> {
        return extractCombinedPixels(bitmap, landmark).v2!!
    }

    fun copyBPModels(context: Context): String {
//        val modelNameList = arrayListOf(
//            "ep-5_test_loss-1.1068_test_acc-0.4359.pt",
//            "HBP_06302023__model=ResCNN1D3_nclasses=1_pretrained=0_loss15.12446915_epoch3.pt",
//            "LBP_06302023__model=ResCNN1D3_nclasses=1_pretrained=0_loss10.89517215_epoch268.pt",
//            "model=ResCNN1D_nclasses=3_pretrained=0_class0.pt",
//            "model=ResCNN1D_nclasses=3_pretrained=0_class1.pt",
//            "model=ResCNN1D_nclasses=3_pretrained=0_class2.pt",
//        )
//        val modelsDir = File(context.cacheDir, "models")
//        modelsDir.mkdirs()
//        val assets = context.assets
//        modelNameList.forEach {
//            val output = File(modelsDir, it)
//            if (!output.exists()) {
//                assets.open(it).copyTo(File(modelsDir, it).outputStream())
//            }
//        }
//        return modelsDir.path + File.separatorChar
        return VitalsModelManager.prepareModels(context)
    }
}