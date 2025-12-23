package com.vitals.sdk.solutions.live.imp

import com.vitals.sdk.solutions.live.ISolutionDebugger
import com.vitals.sdk.solutions.live.LiveSampledData

class FakeSolutionDebugger : ISolutionDebugger {
    override fun dumpSampledData(
        sampleData: LiveSampledData,
        leftEyeDistances: List<Int>,
        rightEyeDistances: List<Int>,
        leftEyeWidths: List<Int>,
        rightEyeWidths: List<Int>
    ) {
        // No-op
    }
}
