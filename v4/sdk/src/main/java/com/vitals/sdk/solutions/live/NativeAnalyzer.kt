package com.vitals.sdk.solutions.live

import com.vitals.sdk.api.Gender
import com.vitals.sdk.api.MeasureResult
import com.vitals.sdk.api.VitalsAnalyzer
import com.vitals.sdk.framework.ResultOrException
import com.vitals.sdk.framework.SdkManager
import com.vitals.sdk.framework.StatsReporter
import com.vitals.sdk.solutions.live.imp.ActionSolution
import com.vitals.sdk.solutions.live.imp.LiveSolution
import com.vitals.sdk.solutions.live.imp.VitalsLib
import kotlin.math.round

interface INativeAnalyzer {
    fun analyze(sampleData: LiveSampledData, modelsDir: String): ResultOrException<MeasureResult>
    fun analyze(sampleData: LiveSampledData, modelsDir: String,
                age: Int, gender: Gender, height: Double, weight: Double): ResultOrException<MeasureResult>
}

class NativeAnalyzer: VitalsAnalyzer, INativeAnalyzer {
    val TAG = NativeAnalyzer::class.simpleName?:"NA"

    override fun analyze(sampleData: LiveSampledData, modelsDir: String): ResultOrException<MeasureResult> {
        return analyze(sampleData.frameQueue, modelsDir, null, null, null, null)
    }

    override fun analyze(
        sampleData: LiveSampledData,
        modelsDir: String,
        age: Int,
        gender: Gender,
        height: Double,
        weight: Double
    ): ResultOrException<MeasureResult> {
        return analyze(sampleData.frameQueue, modelsDir, age, gender, height, weight)
    }

    fun analyzeFromJson(json: String, modelsDir: String): ResultOrException<MeasureResult> {
        return analyzeFromJson(json, modelsDir, null, null, null, null)
    }

    fun analyzeFromJson(
        json: String,
        modelsDir: String,
        age: Int,
        gender: Gender,
        height: Double,
        weight: Double
    ): ResultOrException<MeasureResult> {
        return analyzeFromJson(json, modelsDir, age, gender, height, weight)
    }

    fun analyzeFromJson(
        json: String,
        modelsDir: String,
        age: Int?,
        gender: Gender?,
        height: Double?,
        weight: Double?
    ): ResultOrException<MeasureResult> {
        return try {
            val jsonData = org.json.JSONObject(json)
            val base64Pixels = jsonData.getString("base64Pixels")
            val pixelsShape = jsonData.getJSONArray("pixelsShape")
            val fps = jsonData.getDouble("fps")
            val bigEndian = jsonData.optBoolean("bigEndian", false)

            val byteArray = android.util.Base64.decode(base64Pixels, android.util.Base64.NO_WRAP)
            val buffer = java.nio.ByteBuffer.wrap(byteArray)
            buffer.order(if (bigEndian) java.nio.ByteOrder.BIG_ENDIAN else java.nio.ByteOrder.LITTLE_ENDIAN)

            val frameCount = pixelsShape.getInt(0)
            val channelCount = pixelsShape.getInt(2)
            val pixelsV2Signal = DoubleArray(frameCount * channelCount)
            for (i in 0 until frameCount * channelCount) {
                pixelsV2Signal[i] = buffer.float.toDouble()
            }

            val shapeV2 = IntArray(2)
            shapeV2[0] = frameCount
            shapeV2[1] = channelCount

            val st = System.currentTimeMillis()
            val analyzeResult = VitalsLib.processPixelsV2(
                pixelsV2Signal, shapeV2, fps, modelsDir,
                age, gender?.value, height, weight
            )
            analyzeResult.exception?.printStackTrace()
            val et = System.currentTimeMillis()
            SdkManager.getLogger()?.d(TAG, "analyzeFromJson: V2 measureResult $analyzeResult , ${et - st} ms")
            StatsReporter.updateAnalyzeCost(et - st)
            analyzeResult
        } catch (e: Exception) {
            e.printStackTrace()
            ResultOrException(null, com.vitals.sdk.framework.VitalsException(com.vitals.sdk.framework.ErrCode.ANALYZER_FAIL, "Failed to parse JSON for analysis", e))
        }
    }

    fun analyze(
        frameQueue: List<LiveSolution.Frame>,
        modelsDir: String,
        age: Int?,
        gender: Gender?,
        height: Double?,
        weight: Double?
    ): ResultOrException<MeasureResult> {
        val fst = frameQueue.first().frameTimestamp
        val fet = frameQueue.last().frameTimestamp
        var fps = frameQueue.size * 1000_000_000L / (fet - fst).toDouble()
        SdkManager.getLogger()?.d(TAG, "frameQueue fps: $fps, $fst, $fet, ${fet - fst}, ${frameQueue.size}")
        fps = round(fps)

        val pixelsV2Signal = ArrayList<Double>(frameQueue.size * 3)
        frameQueue.forEach {
            pixelsV2Signal.addAll(it.pixels!!.v2!![0].asIterable())
        }
        val shapeV2 = IntArray(2)
        shapeV2[0] = frameQueue.size
        shapeV2[1] = 3

        var st: Long
        var et: Long
        st = System.currentTimeMillis()
        val analyzeResult = VitalsLib.processPixelsV2(
            pixelsV2Signal.toDoubleArray(), shapeV2, fps, modelsDir,
            age, gender?.value, height, weight
        )
        analyzeResult.exception?.printStackTrace()
        et = System.currentTimeMillis()
        SdkManager.getLogger()?.d(TAG, "doAnalyze: V2 measureResult $analyzeResult , ${et - st} ms")
        StatsReporter.updateAnalyzeCost(et - st)
        return analyzeResult
    }

    fun analyzeAction(
        frameQueue: List<ActionSolution.Frame>,
        modelsDir: String,
        age: Int,
        gender: Gender,
        height: Double,
        weight: Double
    ): ResultOrException<MeasureResult> {
        val fst = frameQueue.first().frameTimestamp
        val fet = frameQueue.last().frameTimestamp
        var fps = frameQueue.size * 1000_000_000L / (fet - fst).toDouble()
        SdkManager.getLogger()?.d(TAG, "frameQueue fps: $fps, $fst, $fet, ${fet - fst}, ${frameQueue.size}")
        fps = round(fps)

        val pixelsV2Signal = ArrayList<Double>(frameQueue.size * 3)
        frameQueue.forEach {
            pixelsV2Signal.addAll(it.pixels!!.v2!![0].asIterable())
        }
        val shapeV2 = IntArray(2)
        shapeV2[0] = frameQueue.size
        shapeV2[1] = 3

        var st: Long
        var et: Long
        st = System.currentTimeMillis()
        val analyzeResult = VitalsLib.processPixelsV2(
            pixelsV2Signal.toDoubleArray(), shapeV2, fps, modelsDir,
            age, gender.value, height, weight
        )
        analyzeResult.exception?.printStackTrace()
        et = System.currentTimeMillis()
        SdkManager.getLogger()?.d(TAG, "doAnalyze: V2 measureResult $analyzeResult , ${et - st} ms")
        StatsReporter.updateAnalyzeCost(et - st)
        return analyzeResult
    }
}