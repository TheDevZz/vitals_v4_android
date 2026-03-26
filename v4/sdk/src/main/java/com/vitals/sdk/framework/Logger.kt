package com.vitals.sdk.framework

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.TextView
import com.tencent.mars.xlog.Xlog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import com.tencent.mars.xlog.Log as XLog

object Logger: ILogger {

    private var logImp: ILogger? = null

    fun setup(context: Context) {
        logImp = XLogImp(context)
    }

    override fun v(tag: String, msg: String) {
        logImp?.v(tag, msg)
    }

    override fun d(tag: String, msg: String) {
        logImp?.d(tag, msg)
    }

    override fun i(tag: String, msg: String) {
        logImp?.i(tag, msg)
    }

    override fun e(tag: String, msg: String) {
        logImp?.e(tag, msg)
    }

    override fun e(tag: String, msg: String, t: Throwable) {
        logImp?.e(tag, msg, t)
    }
}

internal class SdkXLogImp(context: Context, logDirPath: String? = null): Xlog(), ILogger {

    init {
        setup(context, logDirPath)
    }

    var logDirPath: String = ""
        private set

    fun appenderFlush() {
        XLog.appenderFlush()
    }

    private fun setup(context: Context, logDirPath: String?) {
        System.loadLibrary("c++_shared");
        System.loadLibrary("vxlog");

        val logPath = if (logDirPath.isNullOrEmpty()) {
            SdkManager.getFileManager().getDirPath(context, SdkFileManager.LOG_DIR_NAME)
        } else {
            logDirPath
        }
        this.logDirPath = logPath
        val logFileNamePrefix = "vitalslog"

        XLog.setLogImp(this)
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            XLog.appenderOpen(Xlog.LEVEL_VERBOSE, Xlog.AppednerModeAsync, "", logPath, logFileNamePrefix, 0)
        } else {
            XLog.appenderOpen(Xlog.LEVEL_DEBUG, Xlog.AppednerModeAsync, "", logPath, logFileNamePrefix, 0)
        }

        if (getSignFingerprint(context) == "5EBD9D00B1FB563FBDB9A3AEB465953D5EC8610FE5442BDDC0CC3E7337EF88F0") {
            super.setConsoleLogOpen(0, true)
            i("Logger", "enableConsoleLog")
        }
    }

    private fun getSignFingerprint(context: Context): String {
        var fingerprint = ""
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            val signatures = packageInfo.signatures
            for (signature in signatures) {
                val md = MessageDigest.getInstance("SHA256")
                md.update(signature.toByteArray())
                val digest = md.digest()
                val sb = StringBuilder()
                for (b in digest) {
                    sb.append(String.format("%02X", b))
                }
                fingerprint = sb.toString()
            }
        } catch (e: Exception) {
            // ignore
        }
        return fingerprint
    }

    override fun setConsoleLogOpen(logInstancePtr: Long, isOpen: Boolean) {
        // 拦截父类的实现，禁止向控制台输出日志
    }

    override fun v(tag: String, msg: String) {
        XLog.v(tag, msg)
    }

    override fun d(tag: String, msg: String) {
        XLog.d(tag, msg)
    }

    override fun i(tag: String, msg: String) {
        XLog.i(tag, msg)
    }

    override fun e(tag: String, msg: String) {
        XLog.e(tag, msg)
    }

    override fun e(tag: String, msg: String, t: Throwable) {
        XLog.e(tag, msg + "\n" + t.message + "\n" + t.stackTraceToString())
    }
}

internal class XLogImp(context: Context, logDirPath: String? = null): Xlog(), ILogger {

    init {
        setup(context, logDirPath)
    }

    private fun setup(context: Context, logDirPath: String?) {
        System.loadLibrary("c++_shared");
        System.loadLibrary("vxlog");

        val logPath = if (logDirPath.isNullOrEmpty()) {
            context.externalCacheDir!!.path + "/vitals/log"
        } else {
            logDirPath
        }
        val logFileNamePrefix = "vitalslog"

        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            appenderOpen(Xlog.LEVEL_VERBOSE, Xlog.AppednerModeAsync, "", logPath, logFileNamePrefix, 0)
        } else {
            appenderOpen(Xlog.LEVEL_DEBUG, Xlog.AppednerModeAsync, "", logPath, logFileNamePrefix, 0)
        }
    }

    override fun setConsoleLogOpen(logInstancePtr: Long, isOpen: Boolean) {
        // 拦截父类的实现，禁止向控制台输出日志
    }

    override fun v(tag: String, msg: String) {
        XLog.v(tag, msg)
    }

    override fun d(tag: String, msg: String) {
        XLog.d(tag, msg)
    }

    override fun i(tag: String, msg: String) {
        XLog.i(tag, msg)
    }

    override fun e(tag: String, msg: String) {
        XLog.e(tag, msg)
    }

    override fun e(tag: String, msg: String, t: Throwable) {
        XLog.e(tag, msg + "\n" + t.message + "\n" + t.stackTraceToString())
    }
}

class AndroidLog: ILogger {
    override fun v(tag: String, msg: String) {
        android.util.Log.v(tag, msg)
    }

    override fun d(tag: String, msg: String) {
        android.util.Log.d(tag, msg)
    }

    override fun i(tag: String, msg: String) {
        android.util.Log.i(tag, msg)
    }

    override fun e(tag: String, msg: String) {
        android.util.Log.e(tag, msg)
    }

    override fun e(tag: String, msg: String, t: Throwable) {
        android.util.Log.e(tag, msg, t)
    }
}

abstract class AbsFileLog(path: String): ILogger {
    protected var fw = FileWriter(path, true)
    protected var dateFormat = SimpleDateFormat("HH:mm:ss.sss", Locale.US)

    protected open fun writeLog(log: String) {
        writeLogInner(log)
    }

    protected fun writeLogInner(log: String) {
        fw.write(log)
        fw.write("\n")
        fw.flush()
    }

    private fun writeLog(level: String, tag: String, msg: String) {
        val now = System.currentTimeMillis()
        val t = StringBuilder(tag)
        while (t.length < 20) {
            t.append(" ")
        }
        val tid = StringBuilder()
        tid.append(Thread.currentThread().id)
        while (tid.length < 5) {
            tid.append(" ")
        }
        writeLog("${dateFormat.format(now)} $tid $level $t $msg")
    }

    open fun close() {
        fw.close()
    }

    override fun v(tag: String, msg: String) {
        writeLog("V", tag, msg)
    }

    override fun d(tag: String, msg: String) {
        writeLog("D", tag, msg)
    }

    override fun i(tag: String, msg: String) {
        writeLog("I", tag, msg)
    }

    override fun e(tag: String, msg: String) {
        writeLog("E", tag, msg)
    }

    override fun e(tag: String, msg: String, t: Throwable) {
        e(tag, msg + "\n" + t.message + "\n" + t.stackTraceToString())
    }
}

open class FileLog(path: String): AbsFileLog(path) {

    override fun writeLog(log: String) {
        writeLogSync(log)
    }

    @Synchronized
    protected fun writeLogSync(log: String) {
        writeLogInner(log)
    }
}

class FileLogAsync(path: String): FileLog(path) {

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val queue = LinkedBlockingQueue<String>()
    private var flushing = AtomicBoolean(false)

    override fun writeLog(log: String) {
        queue.add(log)
        flushLog()
    }

    private fun flushLog() {
        if (!flushing.getAndSet(true)) {
            flushLogAsync()
        }
    }

    private fun flushLogAsync() {
        ioScope.launch {
            flushAllLog()
        }
    }

    private fun flushAllLog() {
        synchronized(ioScope) {
            while (true) {
                val log = queue.poll() ?: break
                writeLogSync(log)
            }
            flushing.set(false)
            if (!queue.isEmpty()) {
                flushLog()
            }
        }
    }
}

class ConsoleFileLog(path: String): ILogger {

    private val fileLog = FileLogAsync(path)
    private var consoleLog: AndroidLog? = null

    fun setConsoleLogEnable(enable: Boolean) {
        consoleLog = if (enable) {
            AndroidLog()
        } else {
            null
        }
    }

    override fun v(tag: String, msg: String) {
        consoleLog?.v(tag, msg)
        fileLog.v(tag, msg)
    }

    override fun d(tag: String, msg: String) {
        consoleLog?.d(tag, msg)
        fileLog.d(tag, msg)
    }

    override fun i(tag: String, msg: String) {
        consoleLog?.i(tag, msg)
        fileLog.i(tag, msg)
    }

    override fun e(tag: String, msg: String) {
        consoleLog?.e(tag, msg)
        fileLog.e(tag, msg)
    }

    override fun e(tag: String, msg: String, t: Throwable) {
        consoleLog?.e(tag, msg, t)
        fileLog.e(tag, msg, t)
    }
}

abstract class ITeeLogger(private val logA: ILogger, private val logB: ILogger): ILogger {
    override fun v(tag: String, msg: String) {
        logA.v(tag, msg)
        logB.v(tag, msg)
    }

    override fun d(tag: String, msg: String) {
        logA.d(tag, msg)
        logB.d(tag, msg)
    }

    override fun i(tag: String, msg: String) {
        logA.i(tag, msg)
        logB.i(tag, msg)
    }

    override fun e(tag: String, msg: String) {
        logA.e(tag, msg)
        logB.e(tag, msg)
    }

    override fun e(tag: String, msg: String, t: Throwable) {
        logA.e(tag, msg, t)
        logB.e(tag, msg, t)
    }
}

//interface ITextLogger: ILogger {
//    private fun writeLog(level: String, tag: String, msg: String) {
//        val now = System.currentTimeMillis()
//        val t = StringBuilder(tag)
//        while (t.length < 20) {
//            t.append(" ")
//        }
//        val tid = StringBuilder()
//        tid.append(Thread.currentThread().id)
//        while (tid.length < 5) {
//            tid.append(" ")
//        }
//        writeLog("${dateFormat.format(now)} $tid $level $t $msg")
//    }
//
//    fun writeLog(level: String, tag: String, msg: String) {
//        writeLog()
//    }
//
//    override fun v(tag: String, msg: String) {
//        writeLog("V", tag, msg)
//    }
//
//    override fun d(tag: String, msg: String) {
//        writeLog("D", tag, msg)
//    }
//
//    override fun i(tag: String, msg: String) {
//        writeLog("I", tag, msg)
//    }
//
//    override fun e(tag: String, msg: String) {
//        writeLog("E", tag, msg)
//    }
//
//    override fun e(tag: String, msg: String, t: Throwable) {
//        e(tag, msg + "\n" + t.message + "\n" + t.stackTraceToString())
//    }
//}

//class TextViewLog(textView: TextView, private val logger: ILogger?): ILogger {
//    override fun v(tag: String, msg: String) {
//
//    }
//
//    override fun d(tag: String, msg: String) {
//        logger.v()
//    }
//
//    override fun i(tag: String, msg: String) {
//        TODO("Not yet implemented")
//    }
//
//    override fun e(tag: String, msg: String) {
//        TODO("Not yet implemented")
//    }
//
//    override fun e(tag: String, msg: String, t: Throwable) {
//        TODO("Not yet implemented")
//    }
//
//}

class FakeLog: ILogger {
    override fun v(tag: String, msg: String) {}

    override fun d(tag: String, msg: String) {}

    override fun i(tag: String, msg: String) {}

    override fun e(tag: String, msg: String) {}

    override fun e(tag: String, msg: String, t: Throwable) {}
}