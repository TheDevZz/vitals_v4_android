package com.vitals.sdk.solutions.live

import com.vitals.lib.Port
import com.vitals.sdk.api.Gender
import com.vitals.sdk.api.MeasureResult
import com.vitals.sdk.api.Result
import com.vitals.sdk.api.VitalsSampledData
import com.vitals.sdk.api.VitalsSampler
import com.vitals.sdk.api.VitalsSolution
import com.vitals.sdk.framework.SdkManager
import com.vitals.sdk.internal.CryptoUtils
import com.vitals.sdk.parcel.Credential
import com.vitals.sdk.parcel.ParcelableVitalsSampledData
import com.vitals.sdk.parcel.SignalData
import com.vitals.sdk.solutions.core.SolutionUtils

interface ILiveNativeSolution {
    fun createSampler(): VitalsSampler
    fun analyze(sampleData: VitalsSampledData, age: Int, gender: Gender, height: Double, weight: Double): Result<MeasureResult>
    fun genParcelableSampledData(sampleData: VitalsSampledData): ParcelableVitalsSampledData
}

class LiveNativeSolution : VitalsSolution, ILiveNativeSolution {
    override fun createSampler(): VitalsSampler {
        return SdkManager.ensureAuth { LiveSampler() }
    }

    override fun analyze(
        sampleData: VitalsSampledData,
        age: Int,
        gender: Gender,
        height: Double,
        weight: Double
    ): Result<MeasureResult> {
        val modelsDir = Port.copyBPModels(SdkManager.getContext())
        val analyzeResult = NativeAnalyzer().analyze(
            sampleData as LiveSampledData,
            modelsDir,
            age,
            gender,
            height,
            weight
        )
        analyzeResult.data?.let {
            SdkManager.getNetService().uploadResult(it) {
                // nothing to do
            }
            SolutionUtils.roundMeasureResult(it)
        }
        return analyzeResult.convert()
    }

    override fun genParcelableSampledData(sampleData: VitalsSampledData): ParcelableVitalsSampledData {
        val liveSampledData = sampleData as LiveSampledData
        val frameQueue = liveSampledData.frameQueue
        val fst = frameQueue.first().frameTimestamp
        val fet = frameQueue.last().frameTimestamp
        var fps = frameQueue.size * 1000_000_000L / (fet - fst).toDouble()
        val pixelsV2Signal: DoubleArray = frameQueue.flatMap {
            it.pixels!!.v2!![0].asIterable()
        }.toDoubleArray()
        val shapeV2 = IntArray(2)
        shapeV2[0] = frameQueue.size
        shapeV2[1] = 3
        val signalData = SignalData(fst, fet, fps, pixelsV2Signal, shapeV2)

        val timestamp = System.currentTimeMillis()
        val text = SdkManager.secretHashKey + timestamp.toString()
        val sign = CryptoUtils.hashSHA256(text)

        val credential = Credential(timestamp, sign)

        val parcelableVitalsSampledData = ParcelableVitalsSampledData(
            credential,
            signalData,
            liveSampledData.pickedLandmarks,
            liveSampledData.pickedFrames,
        )
        return parcelableVitalsSampledData
    }

}