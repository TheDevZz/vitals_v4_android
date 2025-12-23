package com.vitals.sdk.solutions.live

interface ISolutionDebugger {
    fun dumpSampledData(
        sampleData: LiveSampledData,
        leftEyeDistances: List<Int>,
        rightEyeDistances: List<Int>,
        leftEyeWidths: List<Int>,
        rightEyeWidths: List<Int>
    )
}
