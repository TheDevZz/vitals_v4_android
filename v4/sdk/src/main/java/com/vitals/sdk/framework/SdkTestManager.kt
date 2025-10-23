package com.vitals.sdk.framework

import android.graphics.Bitmap

object SdkTestManager {
    interface ITestHelper {
        fun getFrameMocker(): IFrameMocker?
    }

    interface IFrameMocker {
        fun nextFrame(): Bitmap
    }

    private var mTestHelper: ITestHelper? = null
    var liveSolutionDebugConfig: LiveSolutionDebugConfig? = null

    fun registerTestHelperImp(helper: ITestHelper) {
        mTestHelper = helper
    }

    fun getTestHelperImp(): ITestHelper? {
        return mTestHelper
    }


    class LiveSolutionDebugConfig(
        var enableMockFrame: Boolean,
        var frameMocker: IFrameMocker,
    )
}