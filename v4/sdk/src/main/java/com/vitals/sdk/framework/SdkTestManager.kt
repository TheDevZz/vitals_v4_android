package com.vitals.sdk.framework

import android.graphics.Bitmap
import com.vitals.sdk.solutions.live.imp.LiveSolution
import com.vitals.sdk.solutions.live.imp.SolutionDebugger

object SdkTestManager {
    init {
        registerSolutionDebugger(SolutionDebugger())
        // SdkManager.getLogger()?.let { LiveSolution.setLogImp(it) }
    }

    interface ITestHelper {
        fun getFrameMocker(): IFrameMocker?
    }

    interface IFrameMocker {
        fun nextFrame(): Bitmap
    }

    private var mTestHelper: ITestHelper? = null
    var liveSolutionDebugConfig: LiveSolutionDebugConfig? = null

    private var mSolutionDebugger: com.vitals.sdk.solutions.live.ISolutionDebugger? = null

    fun registerTestHelperImp(helper: ITestHelper) {
        mTestHelper = helper
    }

    fun getTestHelperImp(): ITestHelper? {
        return mTestHelper
    }

    fun registerSolutionDebugger(debugger: com.vitals.sdk.solutions.live.ISolutionDebugger) {
        mSolutionDebugger = debugger
    }

    fun getSolutionDebugger(): com.vitals.sdk.solutions.live.ISolutionDebugger? {
        return mSolutionDebugger
    }


    class LiveSolutionDebugConfig(
        var enableMockFrame: Boolean,
        var frameMocker: IFrameMocker,
    )
}