package com.vitals.example

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.vitals.sdk.api.BloodPressureAnalyzer
import com.vitals.sdk.parcel.ParcelableVitalsSampledData

class BloodPressureAnalyzerService : Service() {
    
    companion object {
        private const val TAG = "BloodPressureAnalyzerService"
    }

    private val binder = object : IBloodPressureAnalyzerService.Stub() {
        @Throws(RemoteException::class)
        override fun analyzeBloodPressure(sampledData: ParcelableVitalsSampledData): BloodPressureMeasureResult {
            Log.d(TAG, "收到血压分析请求")
            
            try {
                // 直接调用BloodPressureAnalyzer进行分析
                val result = BloodPressureAnalyzer.analyze(sampledData)
                
                // 转换为AIDL可用的结果对象
                val measureResult = BloodPressureMeasureResult(
                    result.systolicBloodPressure,
                    result.diastolicBloodPressure
                )
                
                Log.d(TAG, "血压分析完成: 收缩压=${measureResult.systolicBloodPressure}, 舒张压=${measureResult.diastolicBloodPressure}")
                return measureResult
                
            } catch (e: Exception) {
                Log.e(TAG, "血压分析失败", e)
                // 返回默认值
                return BloodPressureMeasureResult(0f, 0f)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "服务绑定")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "血压分析服务创建")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "血压分析服务销毁")
    }
}
