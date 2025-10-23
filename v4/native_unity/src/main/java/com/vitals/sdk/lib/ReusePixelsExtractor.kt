package com.vitals.sdk.lib

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

class ReusePixelsExtractor(private val roiIds: IntArray) {
    private var sharePos = Array<Pos>(roiIds.size) { Pos() }
    private var shareIntArray = IntArray(0)

    @Synchronized
    fun extract(image: Bitmap, landmark: List<Point>): List<Float> {
        for (i in roiIds.indices) {
            sharePos[i].assign(landmark[roiIds[i]])
        }
        return extract(image, sharePos)
//        return extract(image,
//            roiIds.map { landmark[it] }.map { Pos(it) }.toTypedArray()
//        )
    }

    private fun extract(image: Bitmap, contour: Array<Pos>): List<Float> {
        var bottomIdx = 0
        var topIdx = 0
        contour.forEachIndexed { i, point ->
            if (point.fy > contour[bottomIdx].fy) {
                bottomIdx = i
            }
            if (point.fy < contour[topIdx].fy) {
                topIdx = i
            }
        }
        val start: Int
        val end: Int
        if (topIdx <= bottomIdx) {
            start = topIdx
            end = bottomIdx
        } else {
            start = bottomIdx
            end = topIdx
        }
        // inds1 = inds[start: end + 1]
        // inds2 = inds[end: ] + inds[: start + 1]
        // inds2 = inds2[: : -1]
        val insideSize = abs(end - start)
        val outsideSize = contour.size - insideSize

        val emptyPos = Pos()
        val insidePoints = Array<Pos>(insideSize + 1) { emptyPos }
        val outsidePoints = Array<Pos>(outsideSize + 1) { emptyPos }
        System.arraycopy(contour, start, insidePoints, 0, end + 1 - start)
        val tailSize = contour.size - end
        System.arraycopy(contour, end, outsidePoints, 0, tailSize)
        System.arraycopy(contour, 0, outsidePoints, tailSize, start + 1)

        if (insidePoints[0] == contour[topIdx]) {
            outsidePoints.reverse()
        } else {
            insidePoints.reverse()
        }

        val emptySegment = Segment(emptyPos, emptyPos)
        val insideSegment = Array<Segment>(insideSize) { emptySegment } // insideSize = insidePoints.size - 1
        val outsideSegment = Array<Segment>(outsideSize) { emptySegment }

        for (i in 0 until insideSize) {
            insideSegment[i] = Segment(insidePoints[i], insidePoints[i + 1])
        }
        for (i in 0 until outsideSize) {
            outsideSegment[i] = Segment(outsidePoints[i], outsidePoints[i + 1])
        }

        val topY: Int = ceil(contour[topIdx].fy).toInt()
        val bottomY: Int = ceil(contour[bottomIdx].fy).toInt()

        val yRange = topY until bottomY

        val x1List = IntArray(bottomY) { -1 }
        val x2List = IntArray(bottomY) { -1 }

        fun interpolation(yRange: IntRange, segments: Array<Segment>, xList: IntArray) {
            var segIdx = 0
            for (y in yRange) {
                if (y < 0 || y >= image.height) {
                    continue // 出画
                }
                var seg = segments[segIdx]
                while (!(y >= seg.p1.fy && y <= seg.p2.fy)) {
                    if (++segIdx >= segments.size) {
                        return
                    }
                    seg = segments[segIdx]
                }
                val fx: Float = if (seg.slope == 0f) {
                    seg.p2.fx
                } else {
                    (y - seg.p1.fy) / seg.slope + seg.p1.fx
                }
                val x: Int = fx.roundToInt()
                xList[y] = x
            }
        }
        interpolation(yRange, insideSegment, x1List)
        interpolation(yRange, outsideSegment, x2List)

        val pixelCount = image.width * image.height
        if (shareIntArray.size != pixelCount) {
            shareIntArray = IntArray(pixelCount)
        }
        val buffer = shareIntArray
        image.getPixels(buffer, 0, image.width, 0, 0, image.width, image.height)

        val rgbSums = arrayOf(0, 0, 0).toIntArray()
        var count = 0
        for (y in yRange) {
            if (y < 0 || y >= image.height) {
                continue // 出画
            }
            var x1 = x1List[y]
            var x2 = x2List[y]
            if (x1 == -1 || x2 == -1) {
                continue
            }
            if (x1 > x2) {
                val t = x1
                x1 = x2
                x2 = t
            }
//            count += x2 + 1 - x1
            val yOffsetIdx = y * image.width
            for (x in x1..x2) {
                if (x < 0 || x >= image.width) {
                    continue // 出画
                }
                ++count
                val pixelOffsetIdx = yOffsetIdx + x
                val pixel = buffer[pixelOffsetIdx]
                rgbSums[0] += (pixel shr 16) and 0xff
                rgbSums[1] += (pixel shr 8) and 0xff
                rgbSums[2] += pixel and 0xff
            }
        }
        return rgbSums.map { it.toFloat() / count }
    }

    class Pos(var fx: Float, var fy: Float) {
        constructor(): this(0f, 0f)
        constructor(p: PointF): this(p.x, p.y)
        constructor(p: Point): this(p.x.toFloat(), p.y.toFloat())

        fun assign(point: Point) {
            fx = point.x.toFloat()
            fy = point.y.toFloat()
        }

        fun assign(point: PointF) {
            fx = point.x
            fy = point.y
        }
    }

    private class Segment(val p1: Pos, val p2: Pos) {
        val slope: Float = run {
            var dx = p2.fx - p1.fx
            if (dx == 0f) {
                dx = Float.MIN_VALUE
            }
            (p2.fy - p1.fy) / dx
        }
    }
}