package com.vitals.sdk.framework

import android.content.Context
import java.lang.IllegalStateException

abstract class AbsSdkBase : ISdkBase {
    private var mContext: Context? = null

    protected fun setContext(context: Context) {
        mContext = context.applicationContext
    }

    override fun getContext(): Context {
        return mContext ?: throw IllegalStateException("Please initialize the SDK first.")
    }
}