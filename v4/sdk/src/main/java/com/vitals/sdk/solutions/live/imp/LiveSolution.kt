package com.vitals.sdk.solutions.live.imp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.mv.engine.EngineWrapper
import com.vitals.lib.Port
import com.vitals.sdk.framework.ErrCode
import com.vitals.sdk.framework.FakeLog
import com.vitals.sdk.framework.ILogger
import com.vitals.sdk.framework.SdkTestManager
import com.vitals.sdk.framework.StatsReporter
import com.vitals.sdk.framework.VitalsException
import com.vitals.sdk.solutions.core.FaceChecker
import com.vitals.sdk.solutions.live.LiveSampledData
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


class LiveSolution(private val context: Context, private val lifecycleOwner: LifecycleOwner) :
    DefaultLifecycleObserver {
    companion object {
        private var logger: ILogger = FakeLog()

        fun setLogImp(logImp: ILogger) {
            logger = logImp
        }
    }

    private val TAG = "LiveSolution"

    enum class State {
        IDLE,
        KEEP,
        START,
        HANDLE,
        END,
        ANALYZE,
        RESTART,
        PAUSE,
        ERROR,
    }

    enum class Event {
        FACE_RESULT,
        STATE_CHANGE,
        PROGRESS,
        COLLECT_RESULT,
        ERROR,
    }
    private var eventListener: ((Event, Any?) -> Unit)? = null

    class StateChangeEvent(val from: State, val to: State)
    class ProgressEvent(val progress: Float, val remainingTimeMs: Long)

    private lateinit var mainExecutor: Executor
    private lateinit var previewView: PreviewView
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private var analysisExecutor: ExecutorService = Executors.newFixedThreadPool(2)
//    private var handleExecutor = Executors.newSingleThreadExecutor()
    private var handleExecutor = ThreadPoolExecutor(
        1, 1,
        0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>()
    )
    private var poolExecutor = ThreadPoolExecutor(2, 3,
        30, TimeUnit.SECONDS,
        LinkedBlockingQueue()
    )

    private lateinit var liveEngine: EngineWrapper

    private var faceLandmarker: FaceLandmarker? = null

    private val faceChecker = FaceChecker()
    var actionThreshold: Float
        get() = faceChecker.actionThreshold
        set(value) {
            faceChecker.actionThreshold = value
        }

    // State Machine >>>
    var state = State.IDLE
        private set
    // State Machine <<<

    // State Control Parameter >>>
    private object Threshold {
        const val keepTime = 1000
        const val faceOutTimeLimit = 0
        const val faceOutCountLimit = 0
        const val recordTimeLimit = 20000
    }

    private var keepStartTime = 0L
    private var handleStartTime = 0L
    // State Control Parameter <<<

    // Control Options >>>
    private var shareMeshStep = 3
    // Control Options <<<

    // Running Var >>>
    data class Frame(
        val id: Int,
        val timestamp: Long,
        var frame: Bitmap? = null,
        var frameTimestamp: Long = 0,
    ) {
        var faceResult: FaceLandmarkerResult? = null
        var faceCheckResult: FaceChecker.FaceCheckResult? = null
        var idx: Int = 0
        var mesh: List<Point>? = null
        var shareMesh: List<Point>? = null
        var pixels: Port.CombinedPixels? = null
    }
    private var frameQueue = ArrayList<Frame>()
    private var incrementFrameId = AtomicInteger(0)
    private var faceProcessCount = AtomicInteger(0)

    private var livenessConfidences = ConcurrentLinkedQueue<Float>()

    private var clipSize: Size? = null
    private var cropRect: Rect? = null

    private var preFaceCheckTime = 0L

    private var lastFaceFrameIdx: Int = -1
    private var preFaceFrameIdx: Int = -1
    private var preFrameTimestamp: Long = 0L

    private val countDownSignal = CountDownSignal()

    private val pickedLandmarks = ArrayList<List<PointF>>()
    private val pickedFrames = ArrayList<Bitmap>()

//    private var handleStartTime = 0L
    // Running Var <<<

    // DEBUG >>>
    val debugger = Debugger()
    private val mock_analysisExecutor = Executor { command -> command?.run() }
    private val solutionStats = SolutionStats()
    // DEBUG <<<


    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        mainExecutor = ContextCompat.getMainExecutor(context)
        liveEngine = EngineWrapper(context.assets)
        liveEngine.init()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        imageAnalyzer?.clearAnalyzer()

        val executors = ArrayDeque(
            arrayListOf(
                analysisExecutor,
                handleExecutor,
                poolExecutor,
                analysisExecutor,
                handleExecutor,
                poolExecutor,
            )
        )

        fun flushExecutor(executors: ArrayDeque<ExecutorService>) {
            if (executors.isEmpty()) {
                mainExecutor.execute {
                    logger.d(TAG, "onDestroy: release all")
                    val faceLandmarkerLocal = faceLandmarker
                    faceLandmarker = null
                    faceLandmarkerLocal?.close()
                    liveEngine.destroy()
                    analysisExecutor.shutdown()
                    handleExecutor.shutdown()
                    poolExecutor.shutdown()
                }
            } else {
                executors.removeFirst().execute {
                    flushExecutor(executors)
                }
            }
        }
        flushExecutor(executors)
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        logger.d(TAG, "onPause")
        handleExecutor.execute {
            pauseHandle()
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        logger.d(TAG, "onResume")
        handleExecutor.execute {
            resumeHandle()
        }
    }

    fun setup(previewView: PreviewView, listener: ((Event, Any?) -> Unit)? = null) {
        eventListener = listener
        this.previewView = previewView
        previewView.post {
            setupCamera()
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (t: Throwable) {
                logger.e(TAG, "Camera setup failed.", t)
                toState(State.ERROR)
                emitEvent(Event.ERROR, VitalsException(ErrCode.OTHER_CAMERA_ERROR, "Camera setup failed.", t))
            }
        }, mainExecutor)
    }

    @SuppressLint("RestrictedApi")
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val cameraSelector : CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()
        val targetResolution = Size(480, 480)

        val preview = Preview.Builder()
            .setTargetResolution(targetResolution)
            .setMaxResolution(Size(720, 720))
            .build()
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageRotationEnabled(true)
            .setTargetResolution(targetResolution)
            .setMaxResolution(Size(720, 720))
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
//            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
//            .setBackgroundExecutor(analysisExecutor)
            .setImageQueueDepth(10)
            .apply {
                val extender = Camera2Interop.Extender(this)
                extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
            }
            .build()
        analysisExecutor.execute {
            faceLandmarker = setupFaceLandmarker()
            imageAnalysis.setAnalyzer(analysisExecutor, this::handleImageAnalyze)
        }

        val viewPort = ViewPort.Builder(
            Rational(targetResolution.width, targetResolution.height),
            Surface.ROTATION_0
        ).build()
        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageAnalysis)
            .setViewPort(viewPort)
            .build()


        try {
            val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup)
            this.camera = camera
            this.preview = preview
            this.imageAnalyzer = imageAnalysis
        } catch (e: Exception) {
            // IllegalStateException – If the use case has already been bound to another lifecycle or method is not called on main thread.
            // IllegalArgumentException – If the provided camera selector is unable to resolve a camera to be used for the given use cases.
            val errCode = if (e is IllegalStateException) {
                ErrCode.CAMERA_STATE_ERROR
            } else if (e is IllegalArgumentException) {
                ErrCode.OPEN_FRONT_CAMERA_FAIL
            } else {
                ErrCode.OTHER_CAMERA_ERROR
            }
            toState(State.ERROR)
            emitEvent(Event.ERROR, VitalsException(errCode, "Fail to use camera.", e))
        }
    }

    private fun calcFitCenterSize(source: Size, viewPort: Size): Size {
        val swh = source.width.toFloat() / source.height
        val vwh = viewPort.width.toFloat() / viewPort.height
        var width = 0
        var height = 0
        if (swh == vwh) {
            return Size(source.width, source.height)
        } else if (vwh > swh) { // same width
            width = source.width
            height = (width / vwh).toInt()
        } else { // same height
            height = source.height
            width = (height * vwh).toInt()
        }
        return Size(width, height)
    }

    private fun handleImageAnalyze(image: ImageProxy) {
//        logger.d("image", "${image.width}x${image.height}, ${image.imageInfo.rotationDegrees}")
        val now = System.currentTimeMillis()

        solutionStats.frameStats.trigger()

        if (state == State.ANALYZE || state == State.END || state == State.PAUSE || state == State.RESTART) {
            image.close()
            return
        }

        val imageSize = Size(image.width, image.height)
        var clipSize = this.clipSize
        if (clipSize == null) {
            clipSize = calcFitCenterSize(imageSize, Size(previewView.width, previewView.height))
            this.clipSize = clipSize
            val left = (imageSize.width - clipSize.width) / 2
            val top = (imageSize.height - clipSize.height) /2
            val right = left + clipSize.width
            val bottom = top + clipSize.height
            cropRect = Rect(left, top, right, bottom)
            logger.d(TAG, "frame size: $imageSize, clip to $clipSize")
            StatsReporter.updateFrameSize(imageSize)
            StatsReporter.updateClipSize(clipSize)
        }
        var bitmap = image.toBitmap()

        if (imageSize != clipSize) {
            solutionStats.clipStats.start()
            val cropRect = this.cropRect!!
            bitmap = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
            solutionStats.clipStats.end()
        }

        val frame = Frame(incrementFrameId.getAndIncrement(), now, bitmap, image.imageInfo.timestamp)

        // Debug
        debugger.debugFrame(frame)

        postFrame(frame)
        image.close()
    }

    private fun postFrame(frame: Frame) {
        handleExecutor.execute {
            handleFrame(frame)
        }
    }

    private fun postFace(frame: Frame) {
        poolExecutor.execute {
            handleFace(frame)
            postFaceResult(frame)
        }
    }

    private fun postFaceResult(frame: Frame) {
        handleExecutor.execute {
            faceProcessCount.decrementAndGet()
            handleFaceResult(frame)
        }
    }

    private fun handleFace(frame: Frame) {
        val mpImage = BitmapImageBuilder(frame.frame).build()
        solutionStats.faceStats.start()
        var frameTimestamp = frame.timestamp
        if (frameTimestamp == preFrameTimestamp) {
            frameTimestamp += 1
        }
        preFrameTimestamp = frameTimestamp
        faceLandmarker?.detectForVideo(mpImage, frameTimestamp)?.also { result ->
            frame.faceResult = result
            frame.faceCheckResult = faceChecker.check(result, frame.frame!!)
        }
//        val result = faceLandmarker!!.detect(mpImage)
        solutionStats.faceStats.end()
    }

    private fun handleFrame(frame: Frame) {
        if (state == State.IDLE || state == State.KEEP) {
            if (faceProcessCount.compareAndSet(0, 1)) {
                postFace(frame)
            }
        } else if (state == State.HANDLE) {
            frame.idx = frameQueue.size
            frameQueue.add(frame)
            var doFace = lastFaceFrameIdx == -1 || frame.idx - lastFaceFrameIdx > shareMeshStep
            if (doFace && faceProcessCount.compareAndSet(0, 1)) {
                preFaceFrameIdx = lastFaceFrameIdx
                lastFaceFrameIdx = frame.idx
                postFace(frame)
            }
        }
    }

    private fun handleFaceResult(frame: Frame) {
        // Debug
        debugger.debugFaceResult(frame)

        val faceOutType = frame.faceCheckResult?.faceOutType ?: return
        val now = System.currentTimeMillis()

        emitEvent(Event.FACE_RESULT, frame.faceCheckResult!!)

        val faceGood = faceOutType == FaceChecker.FaceOutType.FACE_OUT_TYPE_PASS

//        Log.d(TAG, "faceCheck $faceGood $faceOutType $state")

        when(state) {
            State.IDLE -> {
                if (faceGood) {
                    keepStartTime = now
                    toState(State.KEEP)
                }
            }
            State.KEEP -> {
                if (faceGood) {
                    if (now - keepStartTime > Threshold.keepTime) {
                        startHandle()
                        pickFrame(frame)
                    }
                } else {
                    toState(State.IDLE)
                }
            }
            State.START -> { /* nothing */ }
            State.HANDLE -> {
                updateProgress()
                if (faceGood) {
                    postPixelsBlock(preFaceFrameIdx, frame.idx)
                    if (now - handleStartTime > Threshold.recordTimeLimit) {
                        stopHandle()
                    } else {
                        postDetectLiveness(frame)
                    }
                } else {
                    restartHandle()
                }
            }
            State.END -> { /* nothing */ }
            State.ANALYZE -> { /* nothing */ }
            State.RESTART -> { /* nothing */ }
            State.PAUSE -> { /* nothing */ }
            State.ERROR -> { /* nothing */ }
        }
//        if (faceGood) {
//            val st = System.currentTimeMillis()
//            val pixels = VitalsLib.extractPixels(inputBitmap, result.faceLandmarks()[0])
//            val et = System.currentTimeMillis()
//            Log.d(TAG, "pixels time ${et - st}")
//            Log.d(TAG, "handleFaceResult: ${pixels.joinToString { it.contentToString() }}")
//        }
    }

    private fun updateProgress() {
        val now = System.currentTimeMillis()
        val handleTime = now - handleStartTime
        val progress = handleTime.toFloat() / Threshold.recordTimeLimit
        val remainingTimeMs = Threshold.recordTimeLimit - handleTime
        emitEvent(Event.PROGRESS, ProgressEvent(progress, remainingTimeMs))
    }

    private fun startHandle() {
        toState(State.START)
        handleStartTime = System.currentTimeMillis()
        lastFaceFrameIdx  = -1
        preFaceFrameIdx = -1
        frameQueue = ArrayList()
        livenessConfidences = ConcurrentLinkedQueue()
        toState(State.HANDLE)
    }

    private fun stopHandle() {
        toState(State.END)
        countDownSignal.await()
        debugger.debugFrameQueue(frameQueue)
        toState(State.ANALYZE)
        poolExecutor.execute {
            doAnalyze(frameQueue)
        }
    }

    private fun restartHandle() {
        toState(State.RESTART)
        debugger.debugFrameQueue(frameQueue)
        toState(State.IDLE)
    }

    private fun pauseHandle() {
        if (state != State.ANALYZE) {
            toState(State.PAUSE)
            // clear
        }
    }

    private fun resumeHandle() {
        if (state == State.PAUSE) {
            toState(State.IDLE)
        }
    }

    private fun postDetectLiveness(frame: Frame) {
        poolExecutor.execute {
            detectLiveness(frame)
        }
    }

    private fun detectLiveness(frame: Frame) {
        frame.frame?.let {
            val res = liveEngine.detect(it)
            if (res.hasFace) {
                livenessConfidences.add(res.confidence)
//                if (res.confidence > 0.915f) {
//                    logger.i(TAG, "detectLiveness TTT ${res.confidence}") // DEBUG
//                } else {
//                    logger.e(TAG, "detectLiveness FFF ${res.confidence}") // DEBUG
//                }
            } else {
//                logger.e(TAG, "detectLiveness no face") // DEBUG
            }
        }
    }

    private fun postPixelsBlock(preIdx: Int, currIdx: Int) {
        countDownSignal.add()
        poolExecutor.execute {
            doPixelsBlock(preIdx, currIdx)
            countDownSignal.countDown()
        }
    }

    private fun doPixelsBlock(preIdx: Int, currIdx: Int) {
        val frame = frameQueue[currIdx]
        val bitmap = frame.frame!!
        val landmark = frame.faceResult!!.faceLandmarks()[0]
        val mesh = VitalsLib.genLandmarkPoints(landmark, bitmap.width, bitmap.height)
        frame.mesh = mesh
        frame.pixels = extractCombinedPixels(bitmap, mesh)
        frame.frame = null // release bitmap
        if (preIdx >= 0) {
            val preFrame = frameQueue[preIdx]
            val shareMesh = calcShareMesh(preFrame.mesh!!, frame.mesh!!)
            var i = currIdx
            while (--i >= 0 && i > preIdx) {
                val f = frameQueue[i]
                f.shareMesh = shareMesh
                f.pixels = extractCombinedPixels(f.frame!!, shareMesh)
                f.frame = null // release bitmap
            }
        }
    }

    private fun extractCombinedPixels(bitmap: Bitmap, landmark: List<Point>): Port.CombinedPixels {
        val st = System.currentTimeMillis()
        val pixels = Port.extractCombinedPixels(bitmap, landmark)
        val et = System.currentTimeMillis()
        solutionStats.pixelsStats.push(solutionStats.TimeStats(st, et))
        return pixels
    }

    private fun calcShareMesh(s1: List<Point>, s2: List<Point>): List<Point> {
        val shareMesh = ArrayList<Point>(s1.size)
        for (i in s1.indices) {
            val p1 = s1[i]
            val p2 = s2[i]
            val x = (p1.x + p2.x) / 2
            val y = (p1.y + p2.y) / 2
            shareMesh.add(Point(x, y))
        }
        return shareMesh
    }

    private fun doAnalyze(frameQueue: MutableList<Frame>) {
//        val task = NativeAnalyzerTask()
        for (i in frameQueue.size - 1 downTo 0) {
            // FIXME: pixels为空的不应该移除，应该填充前值
            if (frameQueue[i].pixels == null && frameQueue[i].faceResult == null) {
                frameQueue.removeAt(i)
            } else {
                break
            }
        }
        debugger.debugFrameQueue(frameQueue)

//        emitEvent(Event.COLLECT_RESULT, NativeAnalyzerTask(frameQueue, Port.copyBPModels(context), logger))
//        emitEvent(Event.COLLECT_RESULT, ArrayList(frameQueue))
        emitEvent(Event.COLLECT_RESULT, LiveSampledData(
            ArrayList(frameQueue),
            ArrayList(pickedLandmarks),
            ArrayList(pickedFrames),
            ArrayList(livenessConfidences),
        ))
    }

    private fun setupFaceLandmarker(): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setDelegate(Delegate.GPU)
            .setModelAssetPath("face_landmarker.task")
            .build()
        val faceLandmarkerOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
//            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(2)
            .setOutputFaceBlendshapes(false)
            .setErrorListener(this::onFaceLandmarkError)
            .build()
        val faceLandmarker = FaceLandmarker.createFromOptions(context, faceLandmarkerOptions)
        return faceLandmarker
    }

    private fun onFaceLandmarkError(error: RuntimeException) {
        logger.d( "onFaceLandmarkError",
            error.message ?: "An unknown error has occurred"
        )
    }

    @Synchronized
    private fun toState(newState: State) {
        logger.d(TAG, "toState: $state > $newState")
        val oldState = state
        state = newState
        emitEvent(Event.STATE_CHANGE, StateChangeEvent(oldState, state))
    }

    private fun emitEvent(event: Event, data: Any?) {
        mainExecutor.execute {
            eventListener?.invoke(event, data)
        }
    }

    private fun pickFrame(frame: Frame) {
        val bitmap = frame.frame ?: return
        val landmark = frame.faceResult?.faceLandmarks()?.getOrNull(0)?.map {
            PointF(it.x, it.y)
        } ?: return
        pickedFrames.add(bitmap)
        pickedLandmarks.add(landmark)
    }

    inner class Debugger {
        private var hasOwner = false
        var preTimestamp: Long = 0
        var fpsFrameCount = 0
        var fpsSt = 0L
        var preFaceOutType: FaceChecker.FaceOutType? = null

        var enableMockFrame = false

        var frameMocker: SdkTestManager.IFrameMocker? = null
        private var isFrameMockerCreated = false

        init {
            loadDebugConfig()
        }

        fun loadDebugConfig() {
            SdkTestManager.liveSolutionDebugConfig?.let {
                enableMockFrame = it.enableMockFrame
                frameMocker = it.frameMocker
            }
        }

        fun debugFrame(frame: Frame) {
            fpsFrameCount++
            val now = System.currentTimeMillis()
            if (now - fpsSt >= 1000) {
                logger.d(TAG, "debugFrame: fps ${fpsFrameCount}")
                fpsFrameCount = 0
                fpsSt = now
            }

            if (enableMockFrame) {
                frameMocker?.let {
                    frame.frame = it.nextFrame()
//                    Log.d(TAG, "mock frame ${frame.frame!!.width}x${frame.frame!!.height}")
                }
            }
        }

        fun debugFaceResult(frame: Frame) {
            val faceOutType = frame.faceCheckResult?.faceOutType
            if (faceOutType != preFaceOutType) {
                logger.d(TAG, "faceCheck $preFaceOutType -> $faceOutType")
                preFaceOutType = faceOutType
            }
        }

        fun debugFrameQueue(frameQueue: List<Frame>) {
            var s = ""
            frameQueue.forEach {
                var f = getFrameState(it)
                s += f
            }
            logger.d(TAG, "frameQueue [${frameQueue.size}] $s")
        }

        fun getFrameState(f: Frame): String {
            // val S = 'S'
            // val P = 'P'
            // val M = 'M'
            // val F = 'F'
            // val N = 'N'
            val S = "+"
            val P = "*"
            val SP = "-"
            val M = "#"
            val F = "."
            val N = "N"
            return if (f.shareMesh != null && f.pixels != null) {
                SP
            } else if (f.pixels != null) {
                P
            } else if (f.shareMesh != null) {
                S
            } else if (f.mesh != null) {
                M
            } else if (f.frame != null) {
                F
            } else {
                N
            }
        }
    }

    private inner class SolutionStats {
        val TAG = "SolutionStats"

        inner class TimeStats(
            var startTime: Long = System.currentTimeMillis(),
            var endTime: Long = System.currentTimeMillis(),
        ) {
            fun start(): Long {
                startTime = System.currentTimeMillis()
                return startTime
            }
            fun end(): Long {
                endTime = System.currentTimeMillis()
                return endTime
            }
            fun time(): Long {
                return endTime - startTime
            }
        }

        open inner class StatsLogger(
            val samplingPeriod: Long = 1000L,
            var groupPrint: Int = 1,
            var tag: String = "StatsLogger",
        ) {
            protected var timeStatsRecord = ArrayList<TimeStats>()
            protected var groupPrintList = ArrayList<String>()
            protected var currTimeStats: TimeStats? = null
            protected var periodStartTime: Long = 0
            protected var periodStartIdx: Int = 0
            protected var periodEndIdx: Int = 0
            protected var pid: Int = 0

            var recordAll = false

            var enable = true
                set(value) {
                    field  = value
                    reset()
                }

            init {
                this.print("StatsLogger groupPrint=$groupPrint samplingPeriod=$samplingPeriod")
            }

            open fun reset() {
                periodStartTime = 0L
                timeStatsRecord.clear()
                groupPrintList.clear()
                periodStartIdx = 0
                periodEndIdx = 0
                pid = 0
            }

            open fun start(): Long {
                val st = System.currentTimeMillis()
                if (!enable) return st
                currTimeStats = TimeStats(st, st)
                if (periodStartTime == 0L) {
                    periodStartTime = currTimeStats!!.startTime
                    print(genStatsFormat())
//                    timeStatsRecord.clear()
//                    periodStartIdx = 0
//                    periodEndIdx = 0
                }
                return st
            }

            open fun end(): Long {
                var et = System.currentTimeMillis()
                if (!enable) return et
                currTimeStats?.let {
                    et = it.end()
                    push(it)
                }
                currTimeStats = null
                return et
            }

            @Synchronized
            open fun push(timeStats: TimeStats) {
                timeStatsRecord.add(timeStats)
                periodEndIdx = timeStatsRecord.size
                if (periodStartTime == 0L) {
                    periodStartTime = timeStats.startTime
                    print(genStatsFormat())
                } else if (System.currentTimeMillis() - periodStartTime >= samplingPeriod) {
                    printStats(periodStartIdx, periodEndIdx)
                    periodStartTime = System.currentTimeMillis()
                    periodStartIdx = periodEndIdx
                    if (!recordAll) {
                        timeStatsRecord.clear()
                        periodStartIdx = 0
                        periodEndIdx = 0
                    }
                }
            }

            open fun printStats(periodStartIdx: Int, periodEndIdx: Int) {
                ++pid
                print(genStatsLog(periodStartIdx, periodEndIdx))
            }

            open fun print(text: String) {
                logger.d(tag, text)
            }

            protected fun getTimeList(periodStartIdx: Int, periodEndIdx: Int): List<Long> {
                return timeStatsRecord.subList(periodStartIdx, periodEndIdx).map { it.time() }
            }

            open fun genStatsFormat(): String {
                return "statsFormat: (avg.1 | fps.1 | min-diff-max | ct=count/time) | list"
            }

            open fun genStatsLog(periodStartIdx: Int, periodEndIdx: Int): String {
                var min = Long.MAX_VALUE
                var max = Long.MIN_VALUE
                var sum = 0L
                val count = periodEndIdx - periodStartIdx
                val timeList = timeStatsRecord
                    .subList(periodStartIdx, periodEndIdx)
                    .map {
                        val t = it.time()
                        if (t > max) {
                            max = t
                        }
                        if (t < min) {
                            min = t
                        }
                        sum += t
                        return@map t
                    }

                val avg = sum.toFloat() / count
                val diff = max - min
                val samplingTime = timeStatsRecord[periodEndIdx - 1].endTime - periodStartTime
                val fps = 1000 / avg
                val ct = count.toFloat() / samplingTime * 1000
                var text = "cost #${pid}: ${"%.1f".format(avg)}ms | ${"%.1f".format(fps)}fps | ${min}-${diff}-${max} | ct:${"%.1f".format(ct)}=${count}/${samplingTime}"
                text += " | ${timeList.joinToString()}"
                return text
            }

        }

//        abstract inner class CostStatsLogger(
//            samplingPeriod: Long,
//            groupPrint: Int,
//            tag: String,
//        ) : StatsLogger(samplingPeriod, groupPrint, tag) {
//            override fun printStats(periodStartIdx: Int, periodEndIdx: Int) {
//                super.printStats(periodStartIdx, periodEndIdx)
//                val timeList = getTimeList(periodStartIdx, periodEndIdx)
//                onPrintStats(timeList, periodStartIdx, periodEndIdx)
//            }
//
//            protected fun getTimeList(periodStartIdx: Int, periodEndIdx: Int): List<Long> {
//                return timeStatsRecord.subList(periodStartIdx, periodEndIdx).map { it.time() }
//            }
//
//            abstract fun onPrintStats(timeList: List<Long>, periodStartIdx: Int, periodEndIdx: Int)
//        }

        inner class FaceStats(samplingPeriod: Long, groupPrint: Int): StatsLogger(samplingPeriod, groupPrint, "FaceStats") {
            override fun printStats(periodStartIdx: Int, periodEndIdx: Int) {
                StatsReporter.pushFaceStats(getTimeList(periodStartIdx, periodEndIdx))
            }
        }

        inner class FrameStats(samplingPeriod: Long, groupPrint: Int): StatsLogger(samplingPeriod, groupPrint, "FrameStats"){
            fun trigger() {
                if (currTimeStats == null) {
                    start()
                } else {
                    end()
                    start()
                }
            }

            override fun printStats(periodStartIdx: Int, periodEndIdx: Int) {
                super.printStats(periodStartIdx, periodEndIdx)
//                val timeList = timeStatsRecord.subList(periodStartIdx, periodEndIdx).map { it.time() }
//                val adequate = (timeList.average() - 33.3).absoluteValue < 1
//                val stable = (timeList.max() / 33.3) < 1.5
//                TestReporter.pushFrameStats(PeriodStats(adequate, stable))
                StatsReporter.pushFrameStats(getTimeList(periodStartIdx, periodEndIdx))
            }
        }
        var faceStats = FaceStats(1000, 1)
        var frameStats = FrameStats(1000, 1)
        var clipStats = object : StatsLogger(1000, 10, "ClipStats") {
            override fun printStats(periodStartIdx: Int, periodEndIdx: Int) {
                StatsReporter.pushClipStats(getTimeList(periodStartIdx, periodEndIdx))
            }
        }
        var pixelsStats = object : StatsLogger(1000, 10, "PixelsStats") {
            override fun push(timeStats: TimeStats) {
                super.push(timeStats)
                synchronized(this) {
                    StatsReporter.pushPixelsStats(timeStats.time())
                }
            }
        }

        fun reset() {
            frameStats.reset()
            faceStats.reset()
            clipStats.reset()
            pixelsStats.reset()
        }
    }

    class CountDownSignal: Object() {
        private var count = 0
        @Synchronized
        fun countDown() {
            if (count <= 0) {
                throw Exception("countDown too much")
            }
            --count
            if (count == 0) {
                this.notifyAll()
            }
        }

        @Synchronized
        fun add() {
            ++count
        }

        @Synchronized
        fun await() {
            if (count > 0) {
                this.wait()
            }
        }
    }

    interface AnalyzeTask {
        fun execute(): AnalyzeResult
    }

    class AnalyzeResult(
        var heartRate: Float = 0f,
        var heartRateVariability: Float = 0f,
        var oxygenSaturation: Float = 0f,
        var respirationRate: Float = 0f,

        var stress: Float = 0f,

        var systolicBloodPressure: Float = 0f,
        var diastolicBloodPressure: Float = 0f,

        var confidence: Float = 0f,
    )
}