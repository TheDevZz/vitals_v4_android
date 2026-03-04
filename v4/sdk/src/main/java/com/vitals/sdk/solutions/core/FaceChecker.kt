package com.vitals.sdk.solutions.core

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.lang.Float.max
import java.lang.Math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.min
import kotlin.math.sqrt

class Box(var left: Float, var top: Float, var right: Float, var bottom: Float) {
    constructor() : this(0f, 0f, 0f, 0f)

    constructor(box: Box): this(box.left, box.top, box.right, box.bottom)

    val width get() = right - left
    val height get() = bottom - top

    override fun toString(): String {
        return "Box@${this.hashCode()}{ left: $left, top: $top, right: $right, bottom: $bottom }"
    }
}
class FaceChecker {
    companion object {
        const val THRESHOLD_FAR_PROPORTION = 0.5f
        const val THRESHOLD_LIGHTNESS = 0.2f
    }

    enum class FaceOutType {
        FACE_OUT_TYPE_NO_FACE,
        FACE_OUT_TYPE_MULTI_FACE,
        FACE_OUT_TYPE_OUT_BOX,
        FACE_OUT_TYPE_FAR,
        FACE_OUT_TYPE_ANGLE,
        FACE_OUT_TYPE_DARK,
        FACE_OUT_TYPE_SHAKE,
        FACE_OUT_TYPE_PASS,
    }

    class FaceCheckResult() {
        var faceOutType: FaceOutType? = null
        var box : Box? = null
        var farProportion: Float? = null
        var lightness: Float? = null

        fun setFaceOutType(faceOutType: FaceOutType): FaceCheckResult {
            this.faceOutType = faceOutType
            return this
        }

        fun setBox(box: Box): FaceCheckResult {
            this.box = box
            return this
        }

        fun setFarProportion(farProportion: Float): FaceCheckResult {
            this.farProportion = farProportion
            return this
        }

        fun setLightness(lightness: Float): FaceCheckResult {
            this.lightness = lightness
            return this
        }

        override fun toString(): String {
            return "FaceCheckResult@${hashCode()} { " +
                    "faceOutType: $faceOutType, " +
                    "box: $box, " +
                    "farProportion: $farProportion, " +
                    "lightness: $lightness" +
                    "  }"
        }
    }

    private var shakeCheckRecord: Map<String, Float>? = null
    private val actionThresholdMap: MutableMap<String, Float> = mutableMapOf()
    var allowSlowMotion = false

    var actionThreshold: Float = 1f
        set(threshold) {
            val thresholdFixed = if (threshold < 0f) 0f else if (threshold > 1f) 1f else threshold
            field = thresholdFixed
            applyActionThreshold(thresholdFixed)
        }

    init {
        applyActionThreshold(actionThreshold)
    }

    private fun applyActionThreshold(threshold: Float) {
        actionThresholdMap["dh1"] = interpolateThreshold(0.3f, 1f, threshold)
        actionThresholdMap["dv1"] = interpolateThreshold(0.3f, 1f, threshold)
        actionThresholdMap["dc1"] = interpolateThreshold(0.3f, 1f, threshold)
        actionThresholdMap["arc1"] = interpolateThreshold(15f, 90f, threshold)
        actionThresholdMap["dmh1"] = interpolateThreshold(0.3f, 1f, threshold)
        actionThresholdMap["dmv1"] = interpolateThreshold(0.3f, 1f, threshold)
//        android.util.Log.d("FaceCheck", "actionThresholds: [${threshold}] $actionThresholdMap")
    }

    private fun interpolateThreshold(minVal: Float, maxVal: Float, threshold: Float): Float {
        return minVal + (maxVal - minVal) * (1 - threshold)
    }

    fun check(result: FaceLandmarkerResult, inputBitmap: Bitmap): FaceCheckResult {
        val faceCheckResult = FaceCheckResult()

        if (result.faceLandmarks().size == 0) {
            return faceCheckResult.setFaceOutType(FaceOutType.FACE_OUT_TYPE_NO_FACE)
        }

        if (result.faceLandmarks().size > 1) {
            return faceCheckResult.setFaceOutType(FaceOutType.FACE_OUT_TYPE_MULTI_FACE)
        }

        val normalizedLandmarks = result.faceLandmarks()[0]

        var box = Box()
        val outlineIdxs = listOf(1, 234, 127, 93, 21, 103, 109, 10, 338, 332, 251, 356, 454, 323, 288, 365, 378, 148, 152, 377, 149, 136, 58)
        outlineIdxs.forEachIndexed { i, idx ->
            val normalizedLandmark = normalizedLandmarks[idx]
            val x = normalizedLandmark.x()
            val y = normalizedLandmark.y()
            if (i == 0) {
                box = Box(x, y, x, y)
            } else {
                if (x < box.left) {
                    box.left = x
                }
                if (x > box.right) {
                    box.right = x
                }
                if (y < box.top) {
                    box.top = y
                }
                if (y > box.bottom) {
                    box.bottom = y
                }
            }
        }
        faceCheckResult.setBox(box)
        if (box.left < 0 || box.top < 0 || box.right > 1 || box.bottom > 1) {
            return faceCheckResult.setFaceOutType(FaceOutType.FACE_OUT_TYPE_OUT_BOX)
        }

        val farProportion = max(box.right - box.left, box.bottom - box.top)
        faceCheckResult.setFarProportion(farProportion)
        if (farProportion < THRESHOLD_FAR_PROPORTION) {
            return faceCheckResult.setFaceOutType(FaceOutType.FACE_OUT_TYPE_FAR)
        }

        // 判断角度，返回FACE_OUT_TYPE_ANGLE
        val yaw = calculateYaw(normalizedLandmarks, inputBitmap.width, inputBitmap.height)
        if (abs(yaw) > 30f) {
            return faceCheckResult.setFaceOutType(FaceOutType.FACE_OUT_TYPE_ANGLE)
        }

        var sum = 0
        val meteringIdxs = intArrayOf(101, 205, 206, 207, 314, 202, 211, 194, 208, 200, 426, 418, 431, 422, 434, 427, 426, 425, 330)
        meteringIdxs.forEach { idx ->
            val normalizedLandmark = normalizedLandmarks[idx]
            var x = (normalizedLandmark.x() * inputBitmap.width).toInt()
            var y = (normalizedLandmark.y() * inputBitmap.height).toInt()

            x = if (x < 0) 0 else if (x >= inputBitmap.width) inputBitmap.width - 1 else x
            y = if (y < 0) 0 else if (y >= inputBitmap.height) inputBitmap.height - 1 else y

            val color = inputBitmap.getPixel(x, y)
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            sum += r + g + b
        }
        val lightness = sum.toFloat() / 3 / meteringIdxs.size / 255
        faceCheckResult.setLightness(lightness)
        if (lightness < THRESHOLD_LIGHTNESS) {
            return faceCheckResult.setFaceOutType(FaceOutType.FACE_OUT_TYPE_DARK)
        }

        val record = HashMap<String, Float>()

        val shakeCheckPosIdx =  arrayOf(33, 263, 6, 175)
        val shakeCheckPos = shakeCheckPosIdx.map { idx ->
            convertPoint(normalizedLandmarks[idx], inputBitmap.width, inputBitmap.height)
        }

        val dh1 = distance(shakeCheckPos[0], shakeCheckPos[1])
        val dv1 = distance(shakeCheckPos[2], shakeCheckPos[3])

        val c1 = center(shakeCheckPos[0], shakeCheckPos[1])

        val dhy = shakeCheckPos[1].y - shakeCheckPos[0].y
        val dhx = shakeCheckPos[1].x - shakeCheckPos[0].x
        val arc1 = atan(dhy / dhx) / PI.toFloat() * 180f

        val dmh1 = distance(
            convertPoint(normalizedLandmarks[61], inputBitmap.width, inputBitmap.height),
            convertPoint(normalizedLandmarks[91], inputBitmap.width, inputBitmap.height)
        )
        val dmv1 = distance(
            convertPoint(normalizedLandmarks[0], inputBitmap.width, inputBitmap.height),
            convertPoint(normalizedLandmarks[17], inputBitmap.width, inputBitmap.height)
        )

        record["dh1"] = dh1
        record["dv1"] = dv1
        record["c1x"] = c1.x
        record["c1y"] = c1.y
        record["arc1"] = arc1
        record["dmh1"] = dmh1
        record["dmv1"] = dmv1
        var isShake = false
        val shakeCheckRecord = this.shakeCheckRecord
        if (shakeCheckRecord != null) {
//            val refWidth = sqrt((inputBitmap.width * inputBitmap.width + inputBitmap.height * inputBitmap.height).toDouble()).toFloat()
            val refWidth = min(inputBitmap.width, inputBitmap.height)
            val dc1 = distance(
                c1.x,
                c1.y,
                shakeCheckRecord["c1x"]!!,
                shakeCheckRecord["c1y"]!!
            ) / refWidth
            val shakeValues = arrayOf(
                abs(dh1 / shakeCheckRecord["dh1"]!! - 1),
                abs(dv1 / shakeCheckRecord["dv1"]!! - 1),
                dc1,
                abs(arc1 - shakeCheckRecord["arc1"]!!),
//                abs(dmh1 / shakeCheckRecord["dmh1"]!! - 1),
//                abs(dmv1 / shakeCheckRecord["dmv1"]!! - 1),
            )
            val shakeThresholds = floatArrayOf(
                actionThresholdMap["dh1"]!!,
                actionThresholdMap["dv1"]!!,
                actionThresholdMap["dc1"]!!,
                actionThresholdMap["arc1"]!!,
                actionThresholdMap["dmh1"]!!,
                actionThresholdMap["dmv1"]!!,
            )
            val shakeDiffs = shakeValues.mapIndexed { i, v -> v > shakeThresholds[i] }
            shakeDiffs.forEach { isShake = isShake or it }

//            if (isShake || true) {
//                val sb = StringBuilder()
//                sb.append((if (isShake) "FC+++ " else "FC--- "))
//                shakeValues.forEachIndexed { i, it ->
//                    sb.append(if (shakeDiffs[i]) "T:" else "F:")
//                    sb.append("%.2f ".format(it))
//                }
//                android.util.Log.d("FaceCheck", sb.toString())
////                android.util.Log.d("FaceCheck", distance(c1.x, c1.y, shakeCheckRecord["c1x"]!!, shakeCheckRecord["c1y"]!!).toString())
////                android.util.Log.d("FaceCheck", arrayOf(c1.x, c1.y, shakeCheckRecord["c1x"]!!, shakeCheckRecord["c1y"]!!).joinToString())
//            }
        } else {
            this.shakeCheckRecord = record
        }

        if (isShake) {
            this.shakeCheckRecord = record
            return faceCheckResult.setFaceOutType(FaceOutType.FACE_OUT_TYPE_SHAKE)
        } else if (allowSlowMotion) {
            this.shakeCheckRecord = record
        }
        return faceCheckResult.setFaceOutType(FaceOutType.FACE_OUT_TYPE_PASS)
    }

    private fun convertPoint(point: NormalizedLandmark, width: Int, height: Int): PointF {
        val x = point.x() * width
        val y = point.y() * height
        return PointF(x, y)
    }

    private fun distance(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        return distance(p1.x(), p1.y(), p2.x(), p2.y())
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        return distance(p1.x, p1.y, p2.x, p2.y)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun center(p1: PointF, p2: PointF): PointF {
        return PointF(
            (p1.x + p2.x) / 2,
            (p1.y + p2.y) / 2
        )
    }

    private fun center(p1: NormalizedLandmark, p2: NormalizedLandmark): NormalizedLandmark {
        return NormalizedLandmark.create(
            (p1.x() + p2.x()) / 2,
            (p1.y() + p2.y()) / 2,
//            (p1.z() + p2.z()) / 2
        )
    }

    private fun calculateYaw(landmarks: List<NormalizedLandmark>, width: Int, height: Int): Float {
        val leftEye = convertPoint(landmarks[33], width, height)
        val rightEye = convertPoint(landmarks[263], width, height)
        val noseBridge = convertPoint(landmarks[6], width, height)

        val leftDistance = distance(leftEye, noseBridge)
        val rightDistance = distance(rightEye, noseBridge)

        if (leftDistance + rightDistance == 0f) return 0f

        val ratio = (leftDistance - rightDistance) / (leftDistance + rightDistance)
        return ratio * 90f
    }
}
