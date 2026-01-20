package com.vitals.sdk.internal

import com.vitals.sdk.api.FaceResult
import com.vitals.sdk.api.FaceState
import com.vitals.sdk.api.Gender
import com.vitals.sdk.api.SamplerEventListener
import com.vitals.sdk.api.SamplerState
import com.vitals.sdk.api.VitalsRuntimeException
import com.vitals.sdk.api.VitalsSampledData
import com.vitals.sdk.api.VitalsSdk


abstract class AbsBridgeBase<out T : VitalsSdk>(f : () -> T) {
    private val mInstance by lazy(f)
    fun getSdkInstance(): VitalsSdk = mInstance
}

//class Bridge<out T : VitalsSdk>(f:()->T): AbsBridgeBase<T>(f)

interface ISdk {
//    fun getSolution(): VitalsSolution
}

interface ISampler {
    fun setEventListener(listener: SamplerEventListener)
}

interface IAnalyzer {

}

abstract class AbsSampler: ISampler {
    protected var mEventListener: SamplerEventListener? = null

    override fun setEventListener(listener: SamplerEventListener) {
        mEventListener = listener
    }

    protected fun notifySamplerStateChange(state: SamplerState) {
        mEventListener?.onSamplerStateChange(state)
    }

    protected fun notifyFaceStateChange(state: FaceState) {
        mEventListener?.onFaceStateChange(state)
    }

    protected fun notifyProgressChange(progress: Float, remainingTimeMs: Long) {
        mEventListener?.onProgressChange(progress, remainingTimeMs)
    }

    protected fun notifyQualityChange(quality: Int) {
        mEventListener?.onQualityChange(quality)
    }

    protected fun notifySampledData(sampledData: VitalsSampledData) {
        mEventListener?.onSampledData(sampledData)
    }

    protected fun notifyFaceResult(faceResult: FaceResult) {
        mEventListener?.onFaceResult(faceResult)
    }

    protected fun notifyError(e: VitalsRuntimeException) {
        mEventListener?.onError(e)
    }
}

abstract class AbsSolution {

}

class SamplerProxy(private var imp: ISampler) {

}

class AnalyzerProxy(private var imp: IAnalyzer) {

}

class NativeAnalyzer : IAnalyzer {
    fun execute(age: Int, gender: Gender, height: Double, weight: Double) {

    }
}

open class BaseFeatureAnalyzerProxy(private var imp: NativeAnalyzer) {
    fun execute(age: Int, gender: Gender, height: Double, weight: Double) {
        return imp.execute(age, gender, height, weight)
    }
}

