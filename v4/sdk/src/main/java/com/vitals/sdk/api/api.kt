package com.vitals.sdk.api

import android.os.Parcelable

class AuthorizationException : Exception("SDK authentication failed, please initialize the SDK first or reauthorize.")

data class MeasureResult(
    var heartRate: Float = 0f,
    var heartRateVariability: Float = 0f,
    var respirationRate: Float = 0f,
    var oxygenSaturation: Float = 0f,
    var stress: Float = 0f,
    var systolicBloodPressure: Float = 0f,
    var diastolicBloodPressure: Float = 0f,
    var confidence: Float = 0f,
    var createTime: Long = 0L,
)

enum class Gender(val value: Int) {
    Female(0),
    Male(1),
}

data class BaseFeature(
    var age: Int,
    var gender: Gender,
    var height: Double, // m
    var weight: Double, // kg
)

enum class FaceState {
    OK, // 满足面部采集要求
    NO_FACE, // 没有面部数据
    MULTI_FACE, // 多人面部
    OUT_OF_FRAME, // 面部出框
    TOO_FAR, // 距离太远
    TOO_DARK, // 面部区域太暗
    UNSTEADY, // 画面不稳定
}

enum class SamplerState {
    NOT_STARTED, // 未启动
    WAITING, // 等待画面满足采集要求
    SAMPLING, // 采集中
    FINISHED, // 采集完成
    ERROR, // 采集失败，发生错误
}

enum class SamplerEvent {
    STATE_CHANGE,
    FACE_STATE_CHANGE,
    QUALITY_CHANGE,
    PROGRESS,
}

interface SamplerEventListener {
    fun onFaceStateChange(state: FaceState)
    fun onSamplerStateChange(state: SamplerState)
    fun onProgressChange(progress: Float, remainingTimeMs: Long)
    fun onQualityChange(quality: Int)
    fun onSampledData(sampledData: VitalsSampledData)
    fun onError(e: VitalsRuntimeException)
}

data class VitalsSdkInitOption(
    var appId: String = "",
    var appSecret: String = "",
    var outUserId: String = "",
//    var serverUrl: String = "",
//    var timestamp: Long = 0L,
//    var sign: String = "",
)

data class VitalsSdkConfig(
//    var modelDirPath: String,
    var enableLog: Boolean = true,
    var enableStats: Boolean = false,
    var logDirPath: String? = null,
    var enableDebug: Boolean = false,
)

interface VitalsSdkInitCallback {
    fun onSuccess()
    fun onFailure(errorCode: Int, errMsg: String, t: Throwable?)
}

@Deprecated("")
class VitalsRuntimeException : RuntimeException {
    var errCode: String = ""

    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
    constructor(cause: Throwable): super(cause)
    constructor(errCode: String = "", message: String = "", cause: Throwable? = null): super(message, cause) {
        this.errCode = errCode
    }
}

data class AnalyzeResult (
    var measureResult: MeasureResult?,
    var exception: VitalsRuntimeException?,
)

data class Result<T>(
    val data: T? = null,
    val errorCode: Int = 0,
    val errorMessage: String? = null,
    val throwable: Throwable? = null,
) {
    companion object {
        fun <T> success(data: T): Result<T> {
            return Result(data = data)
        }

        fun <T> error(errorCode: Int, errorMessage: String? = null, throwable: Throwable? = null): Result<T> {
            return Result(errorCode = errorCode, errorMessage = errorMessage, throwable = throwable)
        }
    }
}

typealias ResultCallBack<T> = (Result<T>) -> Unit

//interface ResultCallback<T> {
//    fun onResult(result: Result<T>)
//}