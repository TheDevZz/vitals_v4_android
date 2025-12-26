package com.vitals.sdk.solutions.live.imp

import android.util.Base64
import com.vitals.sdk.api.Gender
import com.vitals.sdk.api.MeasureResult
import com.vitals.sdk.framework.SdkManager
import com.vitals.sdk.framework.SdkXLogImp
import com.vitals.sdk.solutions.live.ISolutionDebugger
import com.vitals.sdk.solutions.live.LiveSampledData
import com.vitals.sdk.solutions.live.NativeAnalyzer
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.round

class SolutionDebugger : ISolutionDebugger {
    private val TAG = "VitalsDev"

    override fun dumpSampledData(
        sampleData: LiveSampledData,
        leftEyeDistances: List<Int>,
        rightEyeDistances: List<Int>,
        leftEyeWidths: List<Int>,
        rightEyeWidths: List<Int>
    ) {
        // Logging StringBuilder Logic from LiveSolution
        dumpStringBuilderLog(sampleData, leftEyeDistances, rightEyeDistances, leftEyeWidths, rightEyeWidths)

        // Logging JSON/Base64 Logic from NativeAnalyzer
        dumpSignalLog(sampleData)

        // Test JSON Analysis (Verification)
//        try {
//            val modelsDir = com.vitals.lib.Port.copyBPModels(SdkManager.getContext())
//            testJsonAnalysis(sampleData, modelsDir)
//        } catch (e: Exception) {
//            SdkManager.getLogger()?.e(TAG, "testJsonAnalysis failed", e)
//        }

        val logger = SdkManager.getLogger()
        if (logger is SdkXLogImp) {
            logger.appenderFlush()

            Thread {
                Thread.sleep(1000)
                SdkManager.getCrashHandler().upload(true)
            }.start()
        }
    }

    private fun dumpStringBuilderLog(
        sampleData: LiveSampledData,
        leftEyeDistances: List<Int>,
        rightEyeDistances: List<Int>,
        leftEyeWidths: List<Int>,
        rightEyeWidths: List<Int>
    ) {
        SdkManager.getLogger()?.d(TAG, "liveness size: ${sampleData.livenessConfidences.size}")
        SdkManager.getLogger()?.d(TAG, "eye size: ${sampleData.leftEyeRatios.size}")

        val sb = StringBuilder()
        sampleData.pickedFrames.getOrNull(0)?.let { bitmap ->
            sb.appendLine("${bitmap.width}x${bitmap.height}")
            sampleData.pickedLandmarks.getOrNull(0)?.joinToString(",") {
                "${round(it.x * bitmap.width).toInt()},${round(it.y * bitmap.height).toInt()}"
            }?.let {
                sb.appendLine(it)
            }
        }
        sb.appendLine(sampleData.livenessConfidences.joinToString(","))
        sb.appendLine(leftEyeDistances.joinToString(","))
        sb.appendLine(rightEyeDistances.joinToString(","))
        sb.appendLine(leftEyeWidths.joinToString(","))
        sb.appendLine(rightEyeWidths.joinToString(","))
        sb.appendLine(sampleData.leftEyeRatios.joinToString(",") { "%.2f".format(it) })
        sb.appendLine(sampleData.rightEyeRatios.joinToString(",") { "%.2f".format(it) })

        val b64 = Base64Deflater.compressToBase64(sb.toString())
        chunkLog(b64, "b64")
    }

    private fun dumpSignalLog(sampleData: LiveSampledData) {
        val signalStr = buildSignalJson(sampleData)
        if (signalStr.isEmpty()) return
        chunkLog(signalStr, "signal")
    }

    private fun buildSignalJson(sampleData: LiveSampledData): String {
        val frameQueue = sampleData.frameQueue
        if (frameQueue.isEmpty()) return ""

        val fst = frameQueue.first().frameTimestamp
        val fet = frameQueue.last().frameTimestamp
        var fps = frameQueue.size * 1000_000_000L / (fet - fst).toDouble()
        fps = round(fps)

        val duration = fet - fst
        val shapeV2For3D = IntArray(3)
        shapeV2For3D[0] = frameQueue.size
        shapeV2For3D[1] = 1
        shapeV2For3D[2] = 3

        val byteArray = ByteArray(frameQueue.size * 3 * java.lang.Float.BYTES)
        val buffer = ByteBuffer.wrap(byteArray)
        frameQueue.forEach {
            val ps = it.pixels!!.v2!![0]
            buffer.putFloat(ps[0].toFloat())
            buffer.putFloat(ps[1].toFloat())
            buffer.putFloat(ps[2].toFloat())
        }
        val base64Pixels = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        val jsonData = JSONObject()
        jsonData.put("base64Pixels", base64Pixels)
        jsonData.put("pixelsShape", JSONArray(shapeV2For3D))
        jsonData.put("bigEndian", buffer.order() == ByteOrder.BIG_ENDIAN)
        jsonData.put("startTime", fst)
        jsonData.put("endTime", fet)
        jsonData.put("duration", duration)
        jsonData.put("fps", fps)

        return jsonData.toString()
    }

    fun testJsonAnalysis(
        sampleData: LiveSampledData,
        modelsDir: String,
        age: Int? = null,
        gender: Gender? = null,
        height: Double? = null,
        weight: Double? = null
    ) {
        val analyzer: NativeAnalyzer = NativeAnalyzer()

        // 1. Original Analysis
        val res1 = if (age != null && gender != null && height != null && weight != null) {
            analyzer.analyze(sampleData, modelsDir, age, gender, height, weight)
        } else {
            analyzer.analyze(sampleData, modelsDir)
        }

        // 2. JSON Analysis
        val json = buildSignalJson(sampleData)
        val res2 = if (age != null && gender != null && height != null && weight != null) {
            analyzer.analyzeFromJson(json, modelsDir, age, gender, height, weight)
        } else {
            analyzer.analyzeFromJson(json, modelsDir)
        }

        // 3. Comparison
        val logger = SdkManager.getLogger()
        logger?.d(TAG, "testJsonAnalysis: res1=$res1")
        logger?.d(TAG, "testJsonAnalysis: res2=$res2")

        if (res1.data == res2.data) {
            logger?.d(TAG, "testJsonAnalysis: SUCCESS - Outputs match!")
        } else {
            logger?.e(TAG, "testJsonAnalysis: FAILED - Outputs do not match!")
            if (res1.data != null && res2.data != null) {
                compareMeasureResults(res1.data!!, res2.data!!)
            }
        }
    }

    private fun compareMeasureResults(r1: MeasureResult, r2: MeasureResult) {
        val logger = SdkManager.getLogger()
        if (r1.heartRate != r2.heartRate) logger?.e(TAG, "heartRate mismatch: ${r1.heartRate} vs ${r2.heartRate}")
        if (r1.heartRateVariability != r2.heartRateVariability) logger?.e(TAG, "hrv mismatch: ${r1.heartRateVariability} vs ${r2.heartRateVariability}")
        if (r1.respirationRate != r2.respirationRate) logger?.e(TAG, "rr mismatch: ${r1.respirationRate} vs ${r2.respirationRate}")
        if (r1.oxygenSaturation != r2.oxygenSaturation) logger?.e(TAG, "spo2 mismatch: ${r1.oxygenSaturation} vs ${r2.oxygenSaturation}")
        if (r1.stress != r2.stress) logger?.e(TAG, "stress mismatch: ${r1.stress} vs ${r2.stress}")
        if (r1.systolicBloodPressure != r2.systolicBloodPressure) logger?.e(TAG, "hbp mismatch: ${r1.systolicBloodPressure} vs ${r2.systolicBloodPressure}")
        if (r1.diastolicBloodPressure != r2.diastolicBloodPressure) logger?.e(TAG, "lbp mismatch: ${r1.diastolicBloodPressure} vs ${r2.diastolicBloodPressure}")
        if (r1.confidence != r2.confidence) logger?.e(TAG, "confidence mismatch: ${r1.confidence} vs ${r2.confidence}")
    }

    private fun chunkLog(text: String, label: String) {
        val chunkSize = 4000
        val total = text.length
        SdkManager.getLogger()?.d(TAG, "chunkLength[$label]: $total")

        var idx = 0
        var part = 1
        val totalParts = (total + chunkSize - 1) / chunkSize
        while (idx < total) {
            val end = (idx + chunkSize).coerceAtMost(total)
            SdkManager.getLogger()?.d(TAG, "chunkData[$label][$part/$totalParts]: ${text.substring(idx, end)}")
            idx = end
            part++
        }
    }
}
