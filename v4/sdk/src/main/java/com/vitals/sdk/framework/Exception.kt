package com.vitals.sdk.framework

import com.vitals.sdk.api.Result
import com.vitals.sdk.api.VitalsRuntimeException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class CrashHandler {
//    fun processException(e: VitalsException) {
//        val stringWriter = StringWriter()
//        val printWriter = PrintWriter(stringWriter, true)
//        e.printStackTrace(printWriter)
//    }

    private fun getExceptionDesc(e: Throwable): String {
        val sb = StringBuilder()
        if (e is VitalsException) {
            sb.append("VitalsException: [${e.errCode}] ${e.message}")
        } else {
            sb.append(e.toString())
        }
        e.cause?.let {
            sb.appendLine()
            sb.append("Caused by: ")
            sb.append(getExceptionDesc(it))
        }
        return sb.toString()
    }

    fun processException(e: Throwable) {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter, true)
        printWriter.println(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss", Locale.US).format(System.currentTimeMillis()))
        printWriter.println(getExceptionDesc(e))
        printWriter.println()
        e.printStackTrace(printWriter)
        val content = stringWriter.toString()
        SdkManager.getLogger()?.d("CrashHandler", "processException: $content")
        val filePath = SdkManager.getFileManager().genFilePath(SdkManager.getContext(), SdkFileManager.FileType.TOMBSTONE)
        FileEncryptor().write(content, filePath)
        upload()
    }

    fun upload() {
        val ctx = SdkManager.getContext()
        val fileMgr = SdkManager.getFileManager()
        val tmbsFiles = File(fileMgr.getDirPath(ctx, SdkFileManager.TOMBSTONE_DIR_NAME)).listFiles()
        if (tmbsFiles.isNullOrEmpty()) {
            return
        }
        val zipFile = File(fileMgr.getTempPath(ctx, "${System.currentTimeMillis()}.zip"))
        val zos = ZipOutputStream(FileOutputStream(zipFile))
        tmbsFiles.forEach {
            addEntry(SdkFileManager.TOMBSTONE_DIR_NAME + "/" + it.name, it, zos)
        }
        val logFiles = File(fileMgr.getDirPath(ctx, SdkFileManager.LOG_DIR_NAME)).listFiles()
        logFiles?.forEach {
            addEntry(SdkFileManager.LOG_DIR_NAME + "/" + it.name, it, zos)
        }
        zos.close()
        SdkManager.getNetService().uploadLogFile(listOf(zipFile.path)) {
            SdkManager.getLogger()?.d("CrashHandler", "upload: data=$it")
            if (it.data == true) {
                tmbsFiles.forEach { file ->
                    file.delete()
                }
            }
            zipFile.delete()
        }
    }

    private fun addEntry(entryName: String, src: File, zos: ZipOutputStream) {
        zos.putNextEntry(ZipEntry(entryName))
        val fis = FileInputStream(src)
        fis.copyTo(zos)
        fis.close()
        zos.flush()
        zos.closeEntry()
    }
}


enum class ErrType(val value: String) {
    NO_ERROR("0"), // 无错误
    PARAMETER("P"), // 参数错误
    BUSINESS("B"), // 业务错误
    NETWORK("N"), // 网络错误
    SERVER_API("A"), // 服务端API错误
    SYSTEM("S"), // 系统错误
    THIRD_PARTY("T"), // 来自三方库的错误
    FILE_IO("F"), // 文件IO错误
    DATABASE("D"), // 数据库错误
    OTHER("O"), // 其他错误
    UNKNOWN("U"), // 未知错误
    RESOURCE("R"), // 资源错误
}

const val appCode = "12"

const val moduleInit = 10
const val moduleInitModel = 11
const val moduleAuth = 20
const val moduleLogin = 30
const val moduleSample = 40
const val moduleSampleCamera = 41
const val moduleAnalyze = 50
const val moduleAnalyzeTorch = 51
const val moduleNetwork = 1

enum class ErrCode(
    val moduleCode: Int,
    val errNo: Int,
    val errType: ErrType,
) {

    NO_ERROR(0, 0, ErrType.NO_ERROR),
    UNREACHABLE_ERROR(0, 1, ErrType.BUSINESS),

    AUTH_ILLEGAL_ARGUMENT   (moduleAuth, 1, ErrType.PARAMETER),
    AUTH_NET_ERR            (moduleAuth, 2, ErrType.NETWORK),
    AUTH_BAD_STATUS         (moduleAuth, 3, ErrType.SERVER_API),
    AUTH_DENIED             (moduleAuth, 4, ErrType.SERVER_API),
    AUTH_UNKNOWN_ERR        (moduleAuth, 9, ErrType.UNKNOWN),
    MISS_MODEL              (moduleInitModel, 1, ErrType.PARAMETER),
    OPEN_FRONT_CAMERA_FAIL  (moduleSampleCamera, 1, ErrType.SYSTEM),
    CAMERA_STATE_ERROR      (moduleSampleCamera, 2, ErrType.SYSTEM),
    OTHER_CAMERA_ERROR      (moduleSampleCamera, 3, ErrType.SYSTEM),
    OPEN_MODEL_FAIL         (moduleAnalyzeTorch, 1, ErrType.FILE_IO),
    RUN_MODEL_FAIL          (moduleAnalyzeTorch, 2, ErrType.FILE_IO),
    ANALYZER_FAIL           (moduleAnalyze, 1, ErrType.UNKNOWN),

    NET_FAIL                (moduleNetwork, 1, ErrType.UNKNOWN),
    NET_INVALID_STATUS_CODE (moduleNetwork, 2, ErrType.UNKNOWN),
    NET_INVALID_RESP_CODE   (moduleNetwork, 3, ErrType.UNKNOWN),
//    NET_INVALID_RESPONSE    (moduleNetwork, 4, ErrType.UNKNOWN),
    NET_INVALID_BODY        (moduleNetwork, 5, ErrType.UNKNOWN),
    NET_INVALID_DATA        (moduleNetwork, 6, ErrType.UNKNOWN),
    NET_PARSE_BODY_FAIL     (moduleNetwork, 7, ErrType.UNKNOWN),
    ;

    init {
        if (errNo !in 0..9) {
            throw Exception("invalid errNo")
        }
        if (moduleCode !in 0..999) {
            throw Exception("invalid moduleCode")
        }
    }

    fun code(): Int {
        return moduleCode * 10 + errNo
    }

    fun stringCode(): String {
        var strNo = errNo.toString()
        var strModule = moduleCode.toString()
        while (strModule.length < 3) {
            strModule = "0$strModule"
        }
        return strModule + strNo
    }

    fun outsideCode(): String {
        return appCode + errType.value + stringCode()
    }
}

class VitalsException(
    var errCode: ErrCode = ErrCode.NO_ERROR,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause) {

    fun convert(): VitalsRuntimeException {
        return VitalsRuntimeException(errCode.stringCode(), message?:"", cause)
    }
}

data class ResultOrException<T>(
    var data: T? = null,
    var exception: VitalsException? = null,
) {
    companion object {
        fun <T> success(data: T): ResultOrException<T> {
            return ResultOrException(data = data)
        }

        fun <T> error(exception: VitalsException): ResultOrException<T> {
            return ResultOrException(exception = exception)
        }

        fun <T> error(
            errCode: ErrCode = ErrCode.NO_ERROR,
            message: String? = null,
            cause: Throwable? = null
        ): ResultOrException<T> {
            return ResultOrException(exception = VitalsException(errCode, message, cause))
        }

        fun <T> error(
            errCode: ErrCode = ErrCode.NO_ERROR,
            cause: Throwable? = null
        ): ResultOrException<T> {
            return ResultOrException(exception = VitalsException(errCode, cause?.message, cause))
        }
    }

    fun convert(): Result<T> {
        return data?.let {
            Result.success(it)
        } ?: exception?.let {
            Result.error(it.errCode.code(), it.message, it.cause)
        } ?: Result.error(ErrCode.UNREACHABLE_ERROR.code())
    }
}

typealias ResultOrExceptionCallback<T> = (ResultOrException<T>) -> Unit
typealias RoECallback<T> = ResultOrExceptionCallback<T>
