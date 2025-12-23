package com.vitals.sdk.solutions.live

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.vitals.sdk.api.FaceState
import com.vitals.sdk.api.SamplerState
import com.vitals.sdk.api.VitalsSampler
import com.vitals.sdk.framework.SdkManager
import com.vitals.sdk.framework.VitalsException
import com.vitals.sdk.internal.AbsSampler
import com.vitals.sdk.solutions.core.FaceChecker
import com.vitals.sdk.solutions.core.FaceChecker.FaceOutType
import com.vitals.sdk.solutions.core.SolutionUtils
import com.vitals.sdk.solutions.live.imp.LiveSolution

interface ILiveSampler {
    fun bindToLifecycle(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView)
    fun reset()
    val samplerState: SamplerState
    val faceState: FaceState?
}

class LiveSampler: AbsSampler(), VitalsSampler {
    override var samplerState: SamplerState = SamplerState.NOT_STARTED
        private set
    override var faceState: FaceState? = null
        private set

    private var preFaceOutType: FaceOutType? = null
    private var mSolution: LiveSolution? = null
    private var mActionThreshold: Float = 1f

    override fun bindToLifecycle(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
       SdkManager.getLogger()?.let { LiveSolution.setLogImp(it) }
        val liveSolution = LiveSolution(context, lifecycleOwner)
        mSolution = liveSolution
        liveSolution.actionThreshold = mActionThreshold
        liveSolution.setup(previewView) { event, data ->
            when(event) {
                LiveSolution.Event.STATE_CHANGE -> {
                    data?.let {
                        val eventData = it as LiveSolution.StateChangeEvent
                        val samplerState = when(eventData.to) {
                            LiveSolution.State.IDLE,
                            LiveSolution.State.KEEP,
                            LiveSolution.State.RESTART,
                            LiveSolution.State.PAUSE,
                                -> SamplerState.WAITING
                            LiveSolution.State.START,
                            LiveSolution.State.HANDLE,
                            LiveSolution.State.END,
                                -> SamplerState.SAMPLING
                            LiveSolution.State.ANALYZE,
                                -> SamplerState.FINISHED
                            LiveSolution.State.ERROR ->
                                SamplerState.ERROR
                        }
                        if (this.samplerState != samplerState) {
                            this.samplerState = samplerState
                            notifySamplerStateChange(samplerState)
                        }
                    }
                }
                LiveSolution.Event.FACE_RESULT -> {
                    data?.let {
                        val faceCheckResult = it as FaceChecker.FaceCheckResult
                        val faceOutType = faceCheckResult.faceOutType
                        if (faceOutType != preFaceOutType) {
                            preFaceOutType = faceOutType
                            val faceState = when(faceOutType) {
                                FaceOutType.FACE_OUT_TYPE_NO_FACE -> FaceState.NO_FACE
                                FaceOutType.FACE_OUT_TYPE_MULTI_FACE -> FaceState.MULTI_FACE
                                FaceOutType.FACE_OUT_TYPE_OUT_BOX -> FaceState.OUT_OF_FRAME
                                FaceOutType.FACE_OUT_TYPE_FAR -> FaceState.TOO_FAR
                                FaceOutType.FACE_OUT_TYPE_DARK -> FaceState.TOO_DARK
                                FaceOutType.FACE_OUT_TYPE_SHAKE -> FaceState.UNSTEADY
                                FaceOutType.FACE_OUT_TYPE_PASS -> FaceState.OK
                                else -> FaceState.NO_FACE
                            }
                            if (this.faceState != faceState) {
                                this.faceState = faceState
                                notifyFaceStateChange(faceState)
                            }
                        }
                        val quality = SolutionUtils.calcQuality(faceCheckResult)
                        notifyQualityChange(quality)
                    }
                }
                LiveSolution.Event.PROGRESS -> {
                    data?.let {
                        val progressData = data as LiveSolution.ProgressEvent
                        notifyProgressChange(progressData.progress, progressData.remainingTimeMs)
                    }
                }
                LiveSolution.Event.COLLECT_RESULT -> {
                    data?.let {
                        val sampledData = data as LiveSampledData
                        notifySampledData(sampledData)
                    }
                }
                LiveSolution.Event.ERROR -> {
                    data?.let {
                        val exception = data as VitalsException
                        notifyError(exception.convert())
                        SdkManager.getCrashHandler().processException(exception)
                    }
                }
            }
        }
    }

    override fun reset() {
        mSolution?.reset()
    }
}