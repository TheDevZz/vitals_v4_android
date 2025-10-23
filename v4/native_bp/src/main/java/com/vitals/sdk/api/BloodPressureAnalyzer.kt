package com.vitals.sdk.api

import android.util.Log
import com.vitals.sdk.bp.Native
import com.vitals.sdk.parcel.ParcelableVitalsSampledData
import java.security.MessageDigest

object BloodPressureAnalyzer {
    data class MeasureResult (
        var systolicBloodPressure: Float = 0f,
        var diastolicBloodPressure: Float = 0f,
    )

    fun analyze(sampledData: ParcelableVitalsSampledData): MeasureResult {
        val timestamp = System.currentTimeMillis()
        if (kotlin.math.abs(timestamp - sampledData.credential.timestamp) > 5 * 60 * 1000) {
            return resultFromCode(1)
        }
        val text = "3d44d922058396773f131a9070f8b88f8fb6b7f2733fadae888be7bf26577c37" + sampledData.credential.timestamp.toString()
        if (hashSHA256(text) != sampledData.credential.sign) {
            return resultFromCode(2)
        }
        Log.d("BPAnalyzer", "signal time: ${sampledData.signalData.startTime} ${sampledData.signalData.endTime}")
        Log.d("BPAnalyzer", "signal fps: ${sampledData.signalData.fps}")
        Log.d("BPAnalyzer", "signal shape: ${sampledData.signalData.shape.joinToString()}")
        Log.d("BPAnalyzer", "signal data size: ${sampledData.signalData.pixels.size}")
        if (sampledData.signalData.fps == 0.0) {
            return resultFromCode(3)
        }
        if (sampledData.signalData.startTime >= sampledData.signalData.endTime
            || sampledData.signalData.startTime == 0L
            || sampledData.signalData.endTime == 0L) {
            return resultFromCode(4)
        }
        if (sampledData.signalData.shape.size != 2) {
            return resultFromCode(5)
        }
        if (sampledData.signalData.shape.reduce { acc, i ->  acc * i} != sampledData.signalData.pixels.size) {
            return resultFromCode(6)
        }
        if (sampledData.pickedLandmarks.isEmpty()) {
            return resultFromCode(7)
        }
        if (sampledData.pickedFrames.isEmpty()) {
            return resultFromCode(8)
        }
        Log.d("BPAnalyzer", "picked size: ${sampledData.pickedLandmarks.size}")
        Log.d("BPAnalyzer", "landmarks size: ${sampledData.pickedLandmarks[0].size}")
        Log.d("BPAnalyzer", "frame wh: ${sampledData.pickedFrames[0].width}, ${sampledData.pickedFrames[0].height}")
        if (sampledData.pickedLandmarks.size != sampledData.pickedFrames.size) {
            return resultFromCode(9)
        }

        if (sampledData.pickedFrames[0].width == 0 || sampledData.pickedFrames[0].height == 0) {
            return resultFromCode(10)
        }
        if (!Native.verifyCredential(timestamp, sampledData.credential.sign)) {
            return resultFromCode(11)
        }
        Thread.sleep(3000)
        return resultFromCode(0)
    }

    private fun resultFromCode(code: Int): MeasureResult {
        var hbp = 120f + code
        var lbp = 80f + code
        return MeasureResult(hbp, lbp)
    }

    private fun hashSHA256(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}