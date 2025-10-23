package com.google.mediapipe.framework.image

import android.graphics.Bitmap

class BitmapImageBuilder(private val bitmap: Bitmap?) {
    fun build(): MPImage {
        return MPImage(bitmap!!)
    }
}