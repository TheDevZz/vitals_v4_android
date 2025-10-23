package com.vitals.sdk.framework

import android.content.Context

interface ISdkManager {
    fun getLogger(): ILogger?
    fun checkAuth(): Boolean
    fun getContext(): Context
}