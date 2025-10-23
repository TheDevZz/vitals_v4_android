package com.vitals.sdk.api

import android.os.Parcelable
import com.vitals.sdk.imp.IVitalsSdkImp1
import com.vitals.sdk.imp.VitalsSdkImp
import com.vitals.sdk.internal.ISampler
import com.vitals.sdk.solutions.live.ILiveNativeSolution
import com.vitals.sdk.solutions.live.ILiveSampler

interface VitalsSampler: ISampler, ILiveSampler

interface VitalsSampledData

interface ParcelableVitalsSampledData: VitalsSampledData, Parcelable

interface VitalsAnalyzer

interface VitalsSolution: ILiveNativeSolution

interface VitalsSdk: IVitalsSdkImp1

abstract class IVitals<out T : VitalsSdk>(f : () -> T) {
    private val mInstance by lazy(f)
    fun getSdkInstance(): VitalsSdk = mInstance
}

object Vitals: IVitals<VitalsSdk>({ VitalsSdkImp() })