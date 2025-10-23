package com.google.mediapipe.tasks.vision.facelandmarker

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class FaceLandmarkerResult(private val faceLandmarks: List<List<NormalizedLandmark>>) {
    fun faceLandmarks(): List<List<NormalizedLandmark>> {
        return faceLandmarks
    }
}
