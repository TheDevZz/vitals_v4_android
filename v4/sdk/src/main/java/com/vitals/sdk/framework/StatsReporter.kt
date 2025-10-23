package com.vitals.sdk.framework

import android.os.Build
import android.util.Size
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

typealias ReportListener = (StatsReporter.StatsReport) -> Unit

object StatsReporter {
    var statsReportBuilder: StatsReportBuilder? = null

    private var listenerList = ArrayList<ReportListener>()

    fun addReportListener(listener: ReportListener) {
        listenerList.add(listener)
    }

    fun removeReportListener(listener: ReportListener) {
        listenerList.remove(listener)
    }

    fun clearReportListener() {
        listenerList.clear()
    }

    fun notifyStatsReport(statsReport: StatsReport) {
        listenerList.forEach { it.invoke(statsReport) }
    }

    fun newStatsReportBuilder() {
        statsReportBuilder = StatsReportBuilder()
    }

    fun reset() {
        statsReportBuilder = null
    }

    fun pushFrameStats(timeList: List<Long>) {
        statsReportBuilder?.apply {
            frameStatsList.addAll(timeList)
        }
    }

    fun pushClipStats(timeList: List<Long>) {
        statsReportBuilder?.apply {
            clipStatsList.addAll(timeList)
        }
    }

    fun pushFaceStats(timeList: List<Long>) {
        statsReportBuilder?.apply {
            faceStatsList.addAll(timeList)
        }
    }

    fun pushPixelsStats(timeList: List<Long>) {
        statsReportBuilder?.apply {
            pixelsStatsList.addAll(timeList)
        }
    }

    fun pushPixelsStats(time: Long) {
        statsReportBuilder?.apply {
            pixelsStatsList.add(time)
        }
    }

    fun updateFrameSize(frameSize: Size) {
        statsReportBuilder?.frameSize = frameSize
    }

    fun updateClipSize(clipSize: Size) {
        statsReportBuilder?.clipSize = clipSize
    }

    fun buildStatsReport(): StatsReport? {
        statsReportBuilder?.apply {
            return build()
        }
        return null
    }

    fun updateAnalyzeCost(cost: Long) {
        statsReportBuilder?.analyzeCost = cost
    }

    class StatsReport {
        var framePeriodStats: PeriodStats? = null
        var frameStats: CostStats? = null
        var clipStats: CostStats? = null
        var faceStats: CostStats? = null
        var pixelsStats: CostStats? = null
        var frameSize: Size? = null
        var clipSize: Size? = null

        var analyzeCost: Long? = null

        fun text(): String {
            val sb = StringBuilder()

            sb.appendLine("frameSize $frameSize / clipSize $clipSize")

            framePeriodStats?.apply {
                frameStats?.let { sb.append("frameStats: adequate=${adequate} stable=${stable}") }  ?: sb.append("Unknown")
                sb.appendLine()
            }

            sb.appendLine("The Format Of Stats: <mean> | <min> | <max> = [ [<cost>, <count>]... ]")
            sb.append("frameStats: ")
            frameStats?.let { sb.append(costStatsToString(it)) }  ?: sb.append("Unknown")
            sb.appendLine()

            sb.append("clipStats: ")
            clipStats?.let{ sb.append(costStatsToString(it)) } ?: sb.append("Unknown")
            sb.appendLine()

            sb.append("faceStats: ")
            faceStats?.let{ sb.append(costStatsToString(it)) } ?: sb.append("Unknown")
            sb.appendLine()

            sb.append("pixelsStats: ")
            pixelsStats?.let{ sb.append(costStatsToString(it)) } ?: sb.append("Unknown")
            sb.appendLine()

            analyzeCost?.let { sb.appendLine("analyzeCost(ms): $analyzeCost") }

            return sb.toString()
        }

        fun textReport(): String {
            val sb = StringBuilder()
            frameSize?.let {
                if (it.width <= 720 && it.height <= 720 && it.width > 255 && it.height > 255) {
                    sb.appendLine("画面尺寸满足要求，${frameSize}，[255~720p]")
                } else {
                    sb.appendLine("画面尺寸不满足要求，${frameSize}，[255~720p]")
                }
            }
            framePeriodStats?.let {
                val fps = 1000.0 / frameStats!!.mean
                val fpsStr = "%.2f".format(fps)
                if (it.adequate) {
                    sb.appendLine("帧率满足要求，$fpsStr [30fps]")
                } else {
                    sb.appendLine("帧率不满足要求，$fpsStr [30fps]")
                }
                if (it.stable) {
                    sb.appendLine("帧率稳定")
                } else {
                    sb.appendLine("帧率不稳定")
                }
            }
            if (frameSize != null && clipSize != null) {
                if (frameSize == clipSize) {
                    sb.appendLine("无需裁剪帧")
                } else {
                    sb.appendLine("需裁剪帧，${frameSize} -> ${clipSize}")
                }
            }
            faceStats?.let {
                if (it.timeList.isNotEmpty()) {
                    val mean = it.timeList.average()
                    if (mean < 50) {
                        sb.appendLine("设备满足人脸识别性能要求，性能较好 [<50ms]")
                    } else if (mean < 100) {
                        sb.appendLine("设备满足人脸识别性能要求，性能一般 [50~100ms]")
                    } else if (mean < 200) {
                        sb.appendLine("设备不满足人脸识别性能要求，比较吃力 [100~200ms]")
                    } else {
                        sb.appendLine("设备无法满足人脸识别性能要求，性能过低 [>200ms]")
                    }
                }
            }
            pixelsStats?.let {
                if (it.timeList.isNotEmpty()) {
                    val mean = it.timeList.average()
                    if (mean < 10) {
                        sb.appendLine("设备提取Pixels很快 [<10ms]")
                    } else if (mean < 20) {
                        sb.appendLine("设备提取Pixels较快 [10~20ms]")
                    } else if (mean < 30) {
                        sb.appendLine("设备提取Pixels较慢 [20~30ms]")
                    } else if(mean < 60) {
                        sb.appendLine("设备提取Pixels太慢 [30~60ms]")
                    } else {
                        sb.appendLine("设备无法满足提取Pixels的性能要求，性能过低 [>60ms]")
                    }
                    val costOfPixels = mean / frameSize!!.let { it.width * it.height }
                    sb.appendLine("单位像素耗时: ${"%.2f".format(costOfPixels * 10_000)} 毫秒每万像素")
                }
            }
            return sb.toString()
        }

        fun textPyCode(): String {
            val pyCode = StringBuilder()
            pyCode.appendLine("# pyCode")
            pyCode.appendLine("# ${Build.BRAND}_${Build.MODEL}")
            pyCode.appendLine("frameStats=${costStatsToListString(frameStats)}")
            pyCode.appendLine("clipStats=${costStatsToListString(clipStats)}")
            pyCode.appendLine("faceStats=${costStatsToListString(faceStats)}")
            pyCode.appendLine("pixelsStats=${costStatsToListString(pixelsStats)}")
            pyCode.appendLine()
            return pyCode.toString()
        }

        fun costStatsToString(costStats: CostStats): String {
            val sb = StringBuilder()
            costStats.apply {
                sb.append("$mean | $min | $max")
                sb.append(" = [")
                sortByCost().forEach {
                    sb.append("[${it.key}, ${it.value}], ")
                }
                sb.append("]")
            }
            return sb.toString()
        }

        fun costStatsToListString(costStats: CostStats?): String {
            if (costStats == null) {
                return "[]"
            }
            return costStats.sortByCost().joinToString(", ", "[", "]") {
                return@joinToString "[${it.key}, ${it.value}]"
            }
        }
    }

    class StatsReportBuilder {
        var frameStatsList = ArrayList<Long>()
        var clipStatsList = ArrayList<Long>()
        var faceStatsList = ArrayList<Long>()
        var pixelsStatsList = ArrayList<Long>()
        var frameSize: Size? = null
        var clipSize: Size? = null

        var analyzeCost: Long? = null

        fun build(): StatsReport {
            val statsReport = StatsReport()
            statsReport.frameSize = frameSize
            statsReport.clipSize = clipSize

            statsReport.frameStats = CostStats(frameStatsList)
            statsReport.clipStats = CostStats(clipStatsList)
            statsReport.faceStats = CostStats(faceStatsList)
            statsReport.pixelsStats = CostStats(pixelsStatsList)

            statsReport.framePeriodStats = PeriodStats(
                (frameStatsList.average() - 33.3).absoluteValue < 1,
                if (frameStatsList.isEmpty()) false else (frameStatsList.max() / 33.3) < 1.5
            )

            statsReport.analyzeCost = analyzeCost

            return statsReport
        }

        private fun reduceStatsList(statsList: ArrayList<PeriodStats>): PeriodStats? {
            if (statsList.isEmpty()) {
                return null
            }
            val periodStats = PeriodStats(adequate = true, stable = true)
            statsList.forEach {
                periodStats.adequate = periodStats.adequate && it.adequate
                periodStats.stable = periodStats.stable && it.stable
            }
            return periodStats
        }
    }

    class CostStats(
        var timeList: List<Long>,
    ) {
        var mean: Long = 0
        var min: Long = 0
        var max: Long = 0

        init {
            if (timeList.isNotEmpty()) {
                mean = timeList.average().roundToLong()
                min = timeList.min()
                max = timeList.max()
            }
        }

        fun getCostFrequencyEntries(): Set<Map.Entry<Long, Int>> {
            val map = HashMap<Long, Int>()
            timeList.forEach {
                val count = map[it] ?: 0
                map[it] = count + 1
            }
            return map.entries
        }

        fun sortByCost(): List<Map.Entry<Long, Int>> {
            return getCostFrequencyEntries().sortedBy { it.key }
        }

        fun sortByFrequency(): List<Map.Entry<Long, Int>> {
            return getCostFrequencyEntries().sortedByDescending { it.value }
        }
    }

    class PeriodStats(
        var adequate: Boolean,
        var stable: Boolean,
    )
}