package com.vitals.sdk.solutions.core

import com.vitals.sdk.api.MeasureResult
import kotlin.math.round
import kotlin.math.roundToInt

object SolutionUtils {
    fun calcQuality(faceCheckResult: FaceChecker.FaceCheckResult): Int {
        val quality: Int
        if (faceCheckResult.faceOutType == FaceChecker.FaceOutType.FACE_OUT_TYPE_NO_FACE) {
            quality = 0
        } else if (faceCheckResult.faceOutType != FaceChecker.FaceOutType.FACE_OUT_TYPE_PASS && faceCheckResult.faceOutType != FaceChecker.FaceOutType.FACE_OUT_TYPE_SHAKE) {
            quality = 1
        } else {
            val lightRange = arrayOf(0.1f, 0.7f) // 映射到环境质量评分的亮度范围
            val lightScore: Float = faceCheckResult.lightness ?: lightRange[0]
            val rangeGap = (lightRange[1] - lightRange[0]) / 4
            val level = ((lightScore - lightRange[0]).coerceAtLeast(0f) / rangeGap).roundToInt()
            quality = 1 + level
        }
        return quality
    }

    fun roundMeasureResult(measureResult: MeasureResult): MeasureResult {
        measureResult.heartRate = round(measureResult.heartRate)
        measureResult.heartRateVariability = round(measureResult.heartRateVariability)
        measureResult.respirationRate = round(measureResult.respirationRate)
        measureResult.oxygenSaturation = round(measureResult.oxygenSaturation)

        measureResult.systolicBloodPressure = round(measureResult.systolicBloodPressure)
        measureResult.diastolicBloodPressure = round(measureResult.diastolicBloodPressure)

        measureResult.stress = round(measureResult.stress * 10) / 10

        measureResult.confidence = round(measureResult.confidence * 100) / 100

        return measureResult
    }
}