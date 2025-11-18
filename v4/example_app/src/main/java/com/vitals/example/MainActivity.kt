package com.vitals.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vitals.example.databinding.ActivityMainBinding
import com.vitals.sdk.parcel.ParcelableVitalsSampledData
import com.vitals.sdk.api.Vitals
import com.vitals.sdk.api.VitalsSdkConfig
import com.vitals.sdk.api.VitalsSdkInitCallback
import com.vitals.sdk.api.VitalsSdkInitOption
import com.vitals.sdk.parcel.Credential
import com.vitals.sdk.parcel.SignalData

class MainActivity : AppCompatActivity() {
    lateinit var viewBinding: ActivityMainBinding
    var hasInitialized = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.btnFace.setOnClickListener {
            if (hasInitialized) {
                startActivity(Intent(this, FaceActivity::class.java))
            } else {
                Toast.makeText(this, "sdk未初始化", Toast.LENGTH_SHORT).show()
            }
        }

        viewBinding.btnTest.setOnClickListener {
            // cacheDir.listFiles()?.first()?.let {
            //     DataBridge.readParcelableFromFile<ParcelableVitalsSampledData>(it, classLoader)?.let { data ->
            //         viewBinding.echo.text = "读取到文件数据"
            //
            // }
            startActivity(Intent(this, ResultActivity::class.java))
            ParcelableVitalsSampledData(
                Credential("", System.currentTimeMillis(), ""),
                SignalData(0L, 0L, 0.0, DoubleArray(0), IntArray(0)),
                emptyList(),
                emptyList(),
            )
        }

        setupSdk()
    }

    private fun setupSdk() {
        viewBinding.echo.text = "SDK初始化中"

        val option = VitalsSdkInitOption()
        option.appId = "mfz8msj23z91w1z8"
        option.appSecret = "oegyckv591wf0zf3dd9lnruqt53y0lqo"
        option.outUserId = "1"
        val cfg = VitalsSdkConfig()
        cfg.enableLog = true
        Vitals.getSdkInstance().initialize(this, option, cfg, object : VitalsSdkInitCallback {
            override fun onSuccess() {
                viewBinding.echo.text = "SDK初始化成功"
                hasInitialized = true
            }

            override fun onFailure(errorCode: Int, errMsg: String, t: Throwable?) {
                viewBinding.echo.text = "SDK初始化失败 $errMsg $t"
            }
        })
    }
}