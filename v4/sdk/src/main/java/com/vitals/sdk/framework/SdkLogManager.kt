package com.vitals.sdk.framework

import android.content.Context

//class SdkLogManager: ISdkLogManager {
//    private var mLogEnable = true
//    private var mStatsEnable = false
//    private var mContext: Context? = null
//    private var mLogDirPath: String? = null
//
//    private var mLogger: XLogImp? = null
//
//    fun setup(context: Context) {
//        mContext = context.applicationContext
//        if (mLogDirPath == null) {
//            mLogDirPath = context.cacheDir.path + "/" + SdkManager.getSdkDirName() + "/log"
//        }
//        if (mLogEnable) {
//            mLogger = XLogImp(context, mLogDirPath)
//        }
//        if (mStatsEnable) {
////            StatsReporter.newTestReportBuilder()
//        }
//    }
//
//    override fun setLogEnable(enable: Boolean) {
//        mLogEnable = enable
//        if (mLogEnable && mLogger == null) {
//
//        }
//    }
//
//    override fun getLogEnable(): Boolean {
//        return mLogEnable
//    }
//
//    override fun setLogDirPath(path: String) {
//        mLogDirPath = path
//    }
//
//    override fun getLogDirPath(): String {
//        return mLogDirPath ?: ""
//    }
//
//    override fun setStatsEnable(enable: Boolean) {
//        mStatsEnable = enable
//    }
//
//    override fun getStatsEnable(): Boolean {
//        return mStatsEnable
//    }
//
//}