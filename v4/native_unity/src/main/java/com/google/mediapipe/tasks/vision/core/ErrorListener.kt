package com.google.mediapipe.tasks.vision.core

interface ErrorListener {
    fun onError(e: RuntimeException)
}