package com.vitals.sdk.solutions.live.imp

import android.util.Base64
import com.vitals.sdk.framework.SdkManager
import com.vitals.sdk.framework.SdkXLogImp
import com.vitals.sdk.solutions.live.ISolutionDebugger
import com.vitals.sdk.solutions.live.LiveSampledData
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
        val frameQueue = sampleData.frameQueue
        if (frameQueue.isEmpty()) return

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

        val signalStr = jsonData.toString()
        chunkLog(signalStr, "signal")
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
