package com.vitals.sdk.framework

import android.content.Context
import android.content.SharedPreferences
import com.vitals.sdk.api.AuthorizationException
import com.vitals.sdk.api.Vitals
import com.vitals.sdk.imp.NetService
import kotlin.io.path.Path
import kotlin.io.path.pathString

object SdkManager: ISdkManager {
    val sdkVersion = "4.0.0"

    var appId: String = ""
    var secretHashKey: String = ""

    private val sdkInstance: ISdkManager by lazy {
        Vitals.getSdkInstance() as ISdkManager
    }

    override fun getLogger(): ILogger? {
        return sdkInstance.getLogger()
    }

    override fun checkAuth(): Boolean {
        return sdkInstance.checkAuth()
    }

    override fun getContext(): Context {
        return sdkInstance.getContext()
    }

    val sp: SharedPreferences by lazy {
        getContext().getSharedPreferences("vitals", Context.MODE_PRIVATE)
    }

    fun getSdkDirName(): String {
        return "vitals"
    }

    fun <T> ensureAuth(action: () -> T): T {
        if (checkAuth()) {
            return action.invoke()
        } else {
            throw AuthorizationException()
        }
    }

    fun getDefaultLogDir(context: Context): String {
        return Path(context.applicationContext.externalCacheDir!!.path, getSdkDirName(), "log").pathString
    }

    private val crashHandler = CrashHandler()
    fun getCrashHandler(): CrashHandler {
        return crashHandler
    }

    private val fileManager = SdkFileManager()
    fun getFileManager(): SdkFileManager {
        return fileManager
    }

    private val netService = NetService()
    fun getNetService(): NetService = netService

    fun handleInitialized() {
        try {
            crashHandler.upload()
        } catch (e: Exception) {
            crashHandler.processException(e)
        }
    }
}
