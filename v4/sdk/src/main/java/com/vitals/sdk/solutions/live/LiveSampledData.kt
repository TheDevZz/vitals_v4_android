package com.vitals.sdk.solutions.live

import android.graphics.Bitmap
import android.graphics.PointF
import com.vitals.sdk.api.VitalsSampledData
import com.vitals.sdk.solutions.live.imp.LiveSolution

class LiveSampledData(
    val frameQueue: List<LiveSolution.Frame>,
    val pickedLandmarks: List<List<PointF>>,
    val pickedFrames: List<Bitmap>,
) : VitalsSampledData {

}
