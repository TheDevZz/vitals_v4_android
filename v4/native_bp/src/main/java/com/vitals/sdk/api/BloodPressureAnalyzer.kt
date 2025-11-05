package com.vitals.sdk.api

import android.content.Context
import com.vitals.lib.Port
import com.vitals.sdk.bp.LandmarkTransformer
import com.vitals.sdk.bp.Native
import com.vitals.sdk.internal.CryptoUtils
import com.vitals.sdk.parcel.ParcelableVitalsSampledData
import java.io.InputStream
import kotlin.math.roundToInt

object BloodPressureAnalyzer {
    data class MeasureResult (
        var systolicBloodPressure: Float = 0f,
        var diastolicBloodPressure: Float = 0f,
    )

    fun analyze(context: Context, sampledData: ParcelableVitalsSampledData): MeasureResult {
        val timestamp = System.currentTimeMillis()
        if (kotlin.math.abs(timestamp - sampledData.credential.timestamp) > 5 * 60 * 1000) {
            throw Exception("certificate expiration")
        }
        if (!verifySign(sampledData)) {
            throw Exception("invalid credentials")
        }
        // Log.d("BPAnalyzer", "signal time: ${sampledData.signalData.startTime} ${sampledData.signalData.endTime}")
        // Log.d("BPAnalyzer", "signal fps: ${sampledData.signalData.fps}")
        // Log.d("BPAnalyzer", "signal shape: ${sampledData.signalData.shape.joinToString()}")
        // Log.d("BPAnalyzer", "signal data size: ${sampledData.signalData.pixels.size}")
        if (sampledData.signalData.fps == 0.0) {
            throw Exception("invalid signal data: invalid fps")
        }
        if (sampledData.signalData.startTime >= sampledData.signalData.endTime
            || sampledData.signalData.startTime == 0L
            || sampledData.signalData.endTime == 0L) {
            throw Exception("invalid signal data: invalid time")
        }
        if (sampledData.signalData.shape.size != 2) {
            throw Exception("invalid signal data: invalid shape")
        }
        if (sampledData.signalData.shape.reduce { acc, i ->  acc * i} != sampledData.signalData.pixels.size) {
            throw Exception("invalid signal data: shape and pixel size mismatch")
        }
        if (sampledData.pickedLandmarks.isEmpty()) {
            throw Exception("invalid sampled data: no picked landmarks")
        }
        if (sampledData.pickedFrames.isEmpty()) {
            throw Exception("invalid sampled data: no picked frames")
        }
        // Log.d("BPAnalyzer", "picked size: ${sampledData.pickedLandmarks.size}")
        // Log.d("BPAnalyzer", "landmarks size: ${sampledData.pickedLandmarks[0].size}")
        // Log.d("BPAnalyzer", "frame wh: ${sampledData.pickedFrames[0].width}, ${sampledData.pickedFrames[0].height}")
        if (sampledData.pickedLandmarks.size != sampledData.pickedFrames.size) {
            throw Exception("invalid sampled data: picked size mismatch")
        }

        if (sampledData.pickedFrames[0].width == 0 || sampledData.pickedFrames[0].height == 0) {
            throw Exception("invalid sampled data: invalid picked frame size")
        }

        val appId = sampledData.credential.appId

        val age: Int
        val gender: Int
        val height: Double
        val weight: Double

        val baseFea = sampledData.baseFeature
        if (baseFea != null) {
            age = baseFea.age
            gender = baseFea.gender.value
            height = baseFea.height
            weight = baseFea.weight
            // Log.d("BPAnalyzer", "age: $age, gender: $gender, height: $height, weight: $weight")
        } else {
            context.assets.open("age_merged_model_0.json.bin").use {
                Port.storeBinaryData("./age_merged_model_0.json", decryptStream(it, appId))
            }
            context.assets.open("bmi_merged_model_0.json.bin").use {
                Port.storeBinaryData("./bmi_merged_model_0.json", decryptStream(it, appId))
            }
            context.assets.open("gender_merged_model_0.json.bin").use {
                Port.storeBinaryData("./gender_merged_model_0.json", decryptStream(it, appId))
            }

            val imgW = sampledData.pickedFrames[0].width
            val imgH = sampledData.pickedFrames[0].height
            val transformer = if (imgW != imgH) LandmarkTransformer(imgW, imgH) else null

            val baseFeatures = sampledData.pickedLandmarks.map { landmark ->
                Port.predictBaseFea(transformer?.transformLandmarks(landmark)?.first ?: landmark)
            }

            Port.removeBinaryData("./age_merged_model_0.json")
            Port.removeBinaryData("./bmi_merged_model_0.json")
            Port.removeBinaryData("./gender_merged_model_0.json")

            age = baseFeatures.map { it.age }.average().roundToInt()
            gender = baseFeatures.map { it.gender.value }.average().roundToInt()
            height = baseFeatures.map { it.height }.average()
            weight = baseFeatures.map { it.weight }.average()
        }


        context.assets.open("bp.pt.bin").use {
            Port.storeBinaryData("bp.pt", decryptStream(it, appId))
        }
        val measureResult = sampledData.signalData.run {
            Port.processPixelsV2(pixels, shape, fps, ".", age, gender, height, weight)
        }
        Port.removeBinaryData("bp.pt")
        return if (measureResult == null) {
            throw Exception("analyze blood pressure failed")
        } else {
            MeasureResult(
                systolicBloodPressure = measureResult.hbp.roundToInt().toFloat(),
                diastolicBloodPressure = measureResult.lbp.roundToInt().toFloat()
            )
        }
    }

    private fun verifySign(sampledData: ParcelableVitalsSampledData): Boolean {
        val appId = sampledData.credential.appId
        val timestamp = sampledData.credential.timestamp
        val sign = sampledData.credential.sign
        val hashKeyHash = Native.getKeyHash(appId)
        val expSign = CryptoUtils.hashSHA256(appId + hashKeyHash + timestamp.toString())
        return expSign == sign
    }

    private fun decryptStream(inputStream: InputStream, appId: String): InputStream {
        val aesKey = Native.getKey(appId)
        return CryptoUtils.decryptStream(inputStream, aesKey)
    }
}