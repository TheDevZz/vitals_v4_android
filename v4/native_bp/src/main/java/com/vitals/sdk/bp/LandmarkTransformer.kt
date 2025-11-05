package com.vitals.sdk.bp

import android.graphics.PointF

/**
 * 人脸正方形边界信息
 */
data class FaceSquareBounds(
    val centerX: Float,
    val centerY: Float,
    val size: Float // 正方形边长（像素单位）
)

/**
 * 关键点坐标转换器
 * 将基于原始图像宽高比的归一化坐标转换为正方形空间的归一化坐标
 */
class LandmarkTransformer(
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val expandRatio: Float = 1.25f // 扩大比例，默认 125%
) {

    /**
     * 将 MediaPipe 的归一化坐标转换为正方形空间的归一化坐标
     *
     * @param normalizedLandmarks 原始归一化坐标列表（范围 0-1）
     * @return Pair<转换后的正方形归一化坐标, 正方形边界信息>
     */
    fun transformLandmarks(
        normalizedLandmarks: List<PointF>
    ): Pair<List<PointF>, FaceSquareBounds> {

        // 1. 转换为像素坐标
        val pixelPoints = normalizedLandmarks.map { point ->
            PointF(
                point.x * imageWidth,
                point.y * imageHeight
            )
        }

        // 2. 计算包围盒
        val minX = pixelPoints.minOf { it.x }
        val maxX = pixelPoints.maxOf { it.x }
        val minY = pixelPoints.minOf { it.y }
        val maxY = pixelPoints.maxOf { it.y }

        val width = maxX - minX
        val height = maxY - minY

        // 3. 计算正方形参数（以最长边为基准）
        val maxSide = maxOf(width, height)
        val squareSize = maxSide * expandRatio

        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        // 4. 保存正方形边界信息
        val squareBounds = FaceSquareBounds(
            centerX = centerX,
            centerY = centerY,
            size = squareSize
        )

        // 5. 转换为正方形空间的归一化坐标
        val squareHalfSize = squareSize / 2f
        val transformedLandmarks = pixelPoints.map { point ->
            PointF(
                (point.x - centerX + squareHalfSize) / squareSize,
                (point.y - centerY + squareHalfSize) / squareSize
            )
        }

        return Pair(transformedLandmarks, squareBounds)
    }
}