package com.vitals.example

import androidx.appcompat.app.AppCompatActivity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.vitals.example.databinding.ActivityResultBinding
import com.vitals.sdk.api.Gender
import com.vitals.sdk.api.MeasureResult
import com.vitals.sdk.api.Result
import com.vitals.sdk.api.Vitals
import com.vitals.sdk.api.VitalsSampledData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ResultActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityResultBinding

    private lateinit var sampledData: VitalsSampledData
    
    private var bloodPressureService: IBloodPressureAnalyzerService? = null
    private var serviceConnected = false
    
    companion object {
        private const val TAG = "ResultActivity"
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "血压分析服务连接成功")
            viewBinding.resultText.append("\n血压分析服务连接成功\n")
            bloodPressureService = IBloodPressureAnalyzerService.Stub.asInterface(service)
            serviceConnected = true
            // 服务连接成功后开始分析
            startBloodPressureAnalysis()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "血压分析服务连接断开")
            viewBinding.resultText.append("\n血压分析服务连接断开\n")
            bloodPressureService = null
            serviceConnected = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        DataBridge.sampledData?.let {
            sampledData = it
            DataBridge.sampledData = null
            GlobalScope.launch {
                val analyzeResult = Vitals.getSdkInstance().getSolution().analyze(sampledData, 30, Gender.Male, 1.75, 60.0)
                runOnUiThread {
                    showMeasureResult(analyzeResult)
                }
            }
            bindBloodPressureService()
        } ?: finish()
    }

    private fun bindBloodPressureService() {
        val intent = Intent(this, BloodPressureAnalyzerService::class.java)
        val bindResult = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "绑定血压分析服务: $bindResult")
        viewBinding.resultText.append("\n绑定血压分析服务: $bindResult\n")
    }

    private fun startBloodPressureAnalysis() {
        if (sampledData != null && serviceConnected) {
            GlobalScope.launch {
                try {
                    val parcelableSampledData = Vitals.getSdkInstance().getSolution().genParcelableSampledData(sampledData)
                    Log.d(TAG, "调用远程服务进行血压分析")
                    val result = bloodPressureService?.analyzeBloodPressure(parcelableSampledData)
                    
                    runOnUiThread {
                        if (result != null) {
                            showServiceMeasureResult(result)
                        } else {
                            viewBinding.resultText.append("\n血压分析失败\n")
                        }
                    }
                } catch (e: RemoteException) {
                    Log.e(TAG, "远程服务调用失败", e)
                    runOnUiThread {
                        viewBinding.resultText.append("\n远程服务调用失败: ${e.message}\n")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "血压分析异常", e)
                    runOnUiThread {
                        viewBinding.resultText.append("\n血压分析异常: ${e.message}\n")
                    }
                }
            }
        }
    }

    private fun showServiceMeasureResult(result: BloodPressureMeasureResult) {
        viewBinding.resultText.append(
            "跨进程服务血压分析结果：\n" +
            "收缩压：" + result.systolicBloodPressure + " mmHg\n" +
            "舒张压：" + result.diastolicBloodPressure + " mmHg\n"
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceConnected) {
            unbindService(serviceConnection)
            serviceConnected = false
        }
    }

    private fun showMeasureResult(analyzeResult: Result<MeasureResult>) {
        val measureResult = analyzeResult.data
        if (measureResult != null) {
            viewBinding.resultText.append(
                "\n心率：" + measureResult.heartRate +
                "\n心率变异性：" + measureResult.heartRateVariability +
                "\n呼吸频率：" + measureResult.respirationRate +
                "\n血氧饱和度：" + measureResult.oxygenSaturation +
                "\n心理压力：" + measureResult.stress +
                "\n收缩压：" + measureResult.systolicBloodPressure +
                "\n舒张压：" + measureResult.diastolicBloodPressure +
                "\n置信度：" + measureResult.confidence
            )
        } else {
            viewBinding.resultText.append(analyzeResult.errorMessage)
        }
    }
}