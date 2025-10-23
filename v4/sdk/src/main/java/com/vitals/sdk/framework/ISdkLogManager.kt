package com.vitals.sdk.framework

interface ISdkLogManager {
    fun setLogEnable(enable: Boolean)
    fun getLogEnable(): Boolean

    fun setLogDirPath(path: String)
    fun getLogDirPath(): String

    fun setStatsEnable(enable: Boolean)
    fun getStatsEnable(): Boolean
}