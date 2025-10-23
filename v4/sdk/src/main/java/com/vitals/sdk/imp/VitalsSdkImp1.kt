package com.vitals.sdk.imp

import android.content.Context
import com.vitals.sdk.api.VitalsSdkInitOption
import com.vitals.sdk.api.VitalsSdkConfig
import com.vitals.sdk.api.VitalsSdkInitCallback
import com.vitals.sdk.api.VitalsSolution
import com.vitals.sdk.framework.AbsSdkBase
import com.vitals.sdk.framework.ActivateManager
import com.vitals.sdk.framework.DeviceInfoManager
import com.vitals.sdk.framework.ErrCode
import com.vitals.sdk.framework.ILogger
import com.vitals.sdk.framework.IdentityManager
import com.vitals.sdk.framework.SdkManager
import com.vitals.sdk.framework.SdkXLogImp
import com.vitals.sdk.framework.VitalsException
import com.vitals.sdk.internal.CryptoUtils
import com.vitals.sdk.lib.FaceLandmarkerContract
import com.vitals.sdk.solutions.live.LiveNativeSolution
import com.vitals.sdk.solutions.live.imp.VitalsLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest

interface IVitalsSdkImp1 {
    fun initialize(
        context: Context,
        vitalsSdkInitOption: VitalsSdkInitOption,
        vitalsSdkConfig: VitalsSdkConfig,
        callback: VitalsSdkInitCallback? = null
    )
    fun getSolution(): VitalsSolution // TODO: move to ISkd in internal/base.kt
}

abstract class VitalsSdkImp1 : AbsSdkBase(), IVitalsSdkImp1 {
    private var mLogger: ILogger? = null
    private var authPass = false

    override fun initialize(
        context: Context,
        vitalsSdkInitOption: VitalsSdkInitOption,
        vitalsSdkConfig: VitalsSdkConfig,
        callback: VitalsSdkInitCallback?
    ) {
        setContext(context)
        SdkManager.getNetService().serverUrl = "https://api.utours.cn/open-service/face-detect/sdk"

        if (vitalsSdkConfig.enableLog) {
            var logDir = vitalsSdkConfig.logDirPath
            if (logDir.isNullOrBlank()) {
                logDir = SdkManager.getDefaultLogDir(context)
            }
            mLogger = SdkXLogImp(context, logDir)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val identityManager = IdentityManager(SdkManager.sp)
            val activateManager = ActivateManager(SdkManager.sp)
            activateManager.executeActivation(context,
                SdkManager.getNetService().serverUrl + "/activation/save",
                vitalsSdkInitOption.appId,
                vitalsSdkInitOption.outUserId,
                identityManager.getUUID(),
                SdkManager.sdkVersion
            )
            val deviceInfoManager = DeviceInfoManager(SdkManager.sp)
            deviceInfoManager.executeReport(context,
                SdkManager.getNetService().serverUrl + "/device/save",
                identityManager.getUUID(),
                vitalsSdkInitOption.outUserId,
            )

            val modelFiles = prepareModelFromAssets(context, arrayOf(context.cacheDir.path, "vitals", "models").joinToString(File.separator))
            FaceLandmarkerContract.faceDetectionModelPath = modelFiles[0]
            FaceLandmarkerContract.faceLandmarkerModelPath = modelFiles[1]

//        val checkResult = VitalsLib.checkModels(vitalsSdkConfig.modelDirPath)
//        if (!checkResult.ok) {
//            callback?.onFailure(ErrCode.MISS_MODEL.code(),
//                "Miss model in path \"${vitalsSdkConfig.modelDirPath}\": ${checkResult.missList.joinToString(", ")}",
//                null)
//            return
//        }

            var err: VitalsException? = null
            try {
//                val r = OnlineAuth().authorize(vitalsSdkInitOption)
                val r = PrivateOnlineAuth().authorize(vitalsSdkInitOption)
//                val r = true
                authPass = r
                SdkManager.getNetService().token = SdkManager.sp.getString("token", "")?:""
                setAppSecretHash(vitalsSdkInitOption.appSecret)
                SdkManager.handleInitialized()
            } catch (e: VitalsException) {
                err = e
            } catch (t: Throwable) {
                err = VitalsException(ErrCode.AUTH_UNKNOWN_ERR, "error occurs", t)
            }
            if (err != null) {
                SdkManager.getCrashHandler().processException(err)
            }
            MainScope().launch {
                if (err != null) {
                    callback?.onFailure(err.errCode.code(), "SDK initialize fail: " + err.message, err.cause)
                } else {
                    callback?.onSuccess()
                }
            }
        }

//        val testDir = File(context.applicationContext.externalCac  heDir, "test")
//        testDir.mkdirs()
//
//        Port.setupTest(testDir.path)
    }

    private fun setAppSecretHash(appSecret: String) {
        SdkManager.appSecretHash = CryptoUtils.hashSHA256(appSecret)
    }

    fun prepareModelFromAssets(context: Context, modelDir: String): List<String> {
        File(modelDir).mkdirs()
        return arrayListOf(
            "face_detection_short_range.tflite",
            "face_landmark.tflite",
        ).map { filename ->
            File(modelDir, filename).let { file ->
                if (!file.exists()) {
                    file.outputStream().use { os ->
                        context.assets.open(filename).copyTo(os)
                    }
                }
                file.path
            }
        }
    }

    override fun getSolution(): VitalsSolution {
        return LiveNativeSolution()
    }

    override fun getLogger(): ILogger? {
        return mLogger
    }

    override fun checkAuth(): Boolean {
        return authPass
    }

}