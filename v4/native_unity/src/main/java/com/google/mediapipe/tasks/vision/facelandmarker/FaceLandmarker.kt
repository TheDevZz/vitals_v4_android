package com.google.mediapipe.tasks.vision.facelandmarker

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ErrorListener
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.vitals.sdk.lib.FaceLandmarkerContract
import com.vitals.sdk.lib.Image

class FaceLandmarker private constructor(options: FaceLandmarkerOptions) {
    companion object {
        fun createFromOptions(context: Context, options: FaceLandmarkerOptions): FaceLandmarker {
            return FaceLandmarker(options)
        }
    }

    private val instance: com.vitals.sdk.lib.FaceLandmarker = com.vitals.sdk.lib.FaceLandmarker()

    init {
        instance.create(FaceLandmarkerContract.faceDetectionModelPath, FaceLandmarkerContract.faceLandmarkerModelPath)
    }

    fun detect(mpImage: MPImage): FaceLandmarkerResult {
//        val multiFaceLandmarks = instance.detect(Image(mpImage.bitmap))
        val multiFaceLandmarks = instance.detect(mpImage.bitmap)
        return FaceLandmarkerResult(multiFaceLandmarks)
    }

    fun detectForVideo(mpImage: MPImage, timestampMs: Long): FaceLandmarkerResult {
        return detect(mpImage)
    }

    fun close() {
        instance.release()
    }

    class FaceLandmarkerOptions private constructor() {
        var baseOptions: BaseOptions? = null
        var runningMode: RunningMode = RunningMode.IMAGE
        var numFaces: Int = 1
        var minFaceDetectionConfidence = 0.5f
        var minFacePresenceConfidence = 0.5f
        var minTrackingConfidence = 0.5f
        var outputFaceBlendshapes = false
        var outputFacialTransformationMatrixes = false
        var errorListener: ErrorListener? = null

        companion object {
            fun builder(): Builder {
                return BuilderImp()
            }
        }

        abstract class Builder {
            abstract fun setBaseOptions(baseOptions: BaseOptions): Builder
            abstract fun setRunningMode(runningMode: RunningMode): Builder
            abstract fun setNumFaces(numFaces: Int): Builder
            abstract fun setOutputFaceBlendshapes(value: Boolean): Builder
            abstract fun setErrorListener(listener: ErrorListener): Builder
            abstract fun setErrorListener(listener: (RuntimeException)->Unit): Builder
            abstract fun build(): FaceLandmarkerOptions
        }

        private class BuilderImp: Builder() {
            var baseOptions: BaseOptions? = null
            var runningMode: RunningMode = RunningMode.IMAGE
            var numFaces: Int = 1
            var minFaceDetectionConfidence = 0.5f
            var minFacePresenceConfidence = 0.5f
            var minTrackingConfidence = 0.5f
            var outputFaceBlendshapes = false
            var outputFacialTransformationMatrixes = false
            var errorListener: ErrorListener? = null
//            var resultListener: OutputHandler.ResultListener<FaceLandmarkerResult, MPImage>

            override fun setBaseOptions(baseOptions: BaseOptions): Builder {
                this.baseOptions = baseOptions
                return this
            }

            override fun setRunningMode(runningMode: RunningMode): Builder {
                this.runningMode = runningMode
                return this
            }

            override fun setNumFaces(numFaces: Int): Builder {
                this.numFaces = numFaces
                return this
            }

            override fun setOutputFaceBlendshapes(value: Boolean): Builder {
                this.outputFaceBlendshapes = value
                return this
            }

            override fun setErrorListener(listener: ErrorListener): Builder {
                this.errorListener = listener
                return this
            }

            override fun setErrorListener(listener: (RuntimeException) -> Unit): Builder {
                this.errorListener = object : ErrorListener {
                    override fun onError(e: RuntimeException) = listener(e)
                }
                return this
            }

            override fun build(): FaceLandmarkerOptions {
                return FaceLandmarkerOptions().also {
                    it.baseOptions = baseOptions
                    it.runningMode = runningMode
                    it.numFaces = numFaces
                    it.minFaceDetectionConfidence = minFaceDetectionConfidence
                    it.minFacePresenceConfidence = minFacePresenceConfidence
                    it.minTrackingConfidence = minTrackingConfidence
                    it.outputFaceBlendshapes = outputFaceBlendshapes
                    it.outputFacialTransformationMatrixes = outputFacialTransformationMatrixes
                    it.errorListener = errorListener
                }
            }

        }
    }
}