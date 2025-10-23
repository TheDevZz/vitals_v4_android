package com.vitals.sdk.solutions.live.imp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.vitals.lib.Port
import com.vitals.sdk.api.Gender
import com.vitals.sdk.api.MeasureResult
import com.vitals.sdk.api.Result
import com.vitals.sdk.framework.AndroidLog
import com.vitals.sdk.framework.ILogger
import com.vitals.sdk.framework.ResultOrException
import com.vitals.sdk.framework.SdkFileManager
import com.vitals.sdk.framework.SdkManager
import com.vitals.sdk.solutions.core.Box
import com.vitals.sdk.solutions.core.FaceChecker.FaceCheckResult
import com.vitals.sdk.solutions.core.FaceChecker.FaceOutType
import com.vitals.sdk.solutions.live.NativeAnalyzer
import java.io.File
import java.nio.Buffer
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class ActionSolution {
    private val TAG = "ActionSolution"
    val logger: ILogger = AndroidLog()

//    lateinit var context: Context

    enum class State {
        INITIALIZED,
        CREATED,
        STARTED,
        HANDLE,
        STOPPED,
        DESTROYED,
        ERROR,
    }

    enum class Event {
        STATE_CHANGE,
        ERROR,
        SAMPLED,
//        FACE_RESULT,
//        PROGRESS,
//        COLLECT_RESULT,
    }

    private var eventListener: ((Event, Any?) -> Unit)? = null

    class SampledDataOwner {
        private var mSampledData: SampledData? = null
        private var id = 0

        @Synchronized
        fun get(id: Int): SampledData? {
            if (id == this.id) {
                val data = mSampledData
                mSampledData = null
                return data
            } else {
                return null
            }
        }

        @Synchronized
        fun releaseAndUpdate(data: SampledData?) {
            mSampledData?.release()
            mSampledData = data
            id += 1
        }

        @Synchronized
        fun createSampledDataHolder(): SampledDataHolder {
            return SampledDataHolder(this, id)
        }
    }
    class SampledDataHolder(private val owner: SampledDataOwner, private val id: Int) {
        fun get(): SampledData? {
            return owner.get(id)
        }

        fun release() {
            get()?.release()
        }
    }

    private enum class ErrType {
        FACE,
        UNKNOWN
    }

    data class Frame(
        val id: Int,
        val timestamp: Long,
        var frame: Bitmap? = null,
        var frameTimestamp: Long = 0,
    ) {
        var faceResult: FaceLandmarkerResult? = null
        var faceCheckResult: FaceCheckResult? = null
        var idx: Int = 0
        var mesh: List<Point>? = null
        var shareMesh: List<Point>? = null
        var pixels: Port.CombinedPixels? = null

        var flag: Int = 0

        companion object {
            const val FLAG_CACHED = 2
            const val FLAG_FILL = 4
        }

        fun addFlag(f: Int) {
            flag = flag or f
        }

        fun removeFlag(f: Int) {
            flag = flag and f.inv()
        }

        fun hasFlag(f: Int) = flag and f != 0
    }
    private var frameQueue = ArrayList<Frame>()
    private var incrementFrameId = AtomicInteger(0)
    private var faceProcessCount = AtomicInteger(0)

    private var handleExecutor = Executors.newSingleThreadExecutor()
    private var faceExecutor = Executors.newSingleThreadExecutor()
    private var poolExecutor = ThreadPoolExecutor(2, 3,
        30, TimeUnit.SECONDS,
        LinkedBlockingQueue()
    )
//    private var eventExecutor = Executors.newSingleThreadExecutor()
    private lateinit var mainExecutor: Executor

//    private var poolCountDownSignal = CountDownSignal()

//    private val faceChecker = FaceChecker()
//    private var frameCacheManager: FrameCacheManager? = null
    private var blockHandler: BlockHandler? = null
    private var sampledDataOwner = SampledDataOwner()

    private var faceLandmarker: FaceLandmarker? = null
    private lateinit var frameCacheDir: String

    private var lastFaceFrameIdx: Int = -1
    private var preFaceFrameIdx: Int = -1
    private var preFrameTimestamp: Long = 0L

    private var shareMeshStep = 0

    var state = State.INITIALIZED

    private val handleTimeoutMs = 15_000L
    private var handleStartTime = 0L

    fun create(context: Context, listener: ((Event, Any?) -> Unit)? = null) {
        mainExecutor = ContextCompat.getMainExecutor(context)
        eventListener = listener
        handleExecutor.submit {
            doCreate(context)
        }
    }

    fun start() {
        handleExecutor.submit(::doStart)
    }

    fun stop() {
        handleExecutor.submit(::doStop)
    }

//    fun end() {
//        handleExecutor.submit(::doEnd)
//    }

    fun release() {
        handleExecutor.submit(::doRelease)
    }

    private fun doCreate(context: Context) {
        if (state == State.INITIALIZED) {
            toState(State.CREATED)
            frameCacheDir = SdkManager.getFileManager().getDirPath(context, SdkFileManager.TEMP_DIR_NAME)
            faceLandmarker = setupFaceLandmarker(context)
        }
    }

    private fun doStart() {
        if (state == State.CREATED) {
            toState(State.STARTED)
        }
    }

    private fun doStop() {
        if (state == State.STARTED || state == State.HANDLE) {
            if (state == State.HANDLE) {
                stopHandle()
            }
            toState(State.STOPPED)
        }
    }

    private fun startHandle() {
        handleStartTime = System.currentTimeMillis()
        lastFaceFrameIdx  = -1
        preFaceFrameIdx = -1
        frameQueue = ArrayList()
//        frameCacheManager = FrameCacheManager(frameCacheDir, poolExecutor)
        blockHandler = BlockHandler(poolExecutor, frameCacheDir)
    }

    private fun stopHandle() {
        logger.d(TAG, "stopHandle")
        debugFrameQueue(frameQueue)
        val sampledData = SampledData(frameQueue, blockHandler!!)
        sampledDataOwner.releaseAndUpdate(sampledData)
        val sampledDataHolder = sampledDataOwner.createSampledDataHolder()
        emitEvent(Event.SAMPLED, sampledDataHolder)
    }

    private fun restartHandle() {
        logger.d(TAG, "restart")
        debugFrameQueue(frameQueue)
    }

//    private fun doEnd() {}

    private fun doRelease() {
        if (state == State.STOPPED) {
            toState(State.DESTROYED)
            faceLandmarker?.close()
            sampledDataOwner.releaseAndUpdate(null)
            faceExecutor.shutdown()
            poolExecutor.shutdown()
            handleExecutor.shutdown()
        }
    }

    fun postFrame(bitmap: Bitmap, frameTimestamp: Long) {
        val f = Frame(
            incrementFrameId.getAndIncrement(),
            System.currentTimeMillis(),
            bitmap,
            frameTimestamp
        )
        handleExecutor.execute {
            handleFrame(f)
        }
    }

    private fun postFace(frame: Frame) {
        faceExecutor.execute {
            handleFace(frame)
            postFaceResult(frame)
            // TODO
            // BUG: 若调用release，handleExecutor被shutdown，无法再添加新任务
        }
    }

    private fun postFaceResult(frame: Frame) {
        handleExecutor.execute {
            faceProcessCount.decrementAndGet()
            handleFaceResult(frame)
        }
    }

    private fun handleFrame(frame: Frame) {
        if (state == State.STARTED || state == State.HANDLE) {
            frame.idx = frameQueue.size
            frameQueue.add(frame)
            val doFace = lastFaceFrameIdx == -1 || frame.idx - lastFaceFrameIdx > shareMeshStep
            if (doFace && faceProcessCount.compareAndSet(0, 1)) {
                preFaceFrameIdx = lastFaceFrameIdx
                lastFaceFrameIdx = frame.idx
                postFace(frame)
            }
        }
    }

    private fun handleFace(frame: Frame) {
        val mpImage = BitmapImageBuilder(frame.frame).build()
//        solutionStats.faceStats.start()
        var frameTimestamp = frame.timestamp
        if (frameTimestamp == preFrameTimestamp) {
            frameTimestamp += 1
        }
        preFrameTimestamp = frameTimestamp
        val result = faceLandmarker!!.detectForVideo(mpImage, frameTimestamp)
//        val result = faceLandmarker!!.detect(mpImage)
//        solutionStats.faceStats.end()
//        mpImage.close()
        Thread.sleep(33*6)
        frame.faceResult = result
        frame.faceCheckResult = FaceHelper.checkFace(result, frame.frame!!)
    }

    private fun handleFaceResult(frame: Frame) {
        val faceOutType = frame.faceCheckResult!!.faceOutType
        val now = System.currentTimeMillis()

        val faceGood = faceOutType == FaceOutType.FACE_OUT_TYPE_PASS

        when(state) {
            State.STARTED -> {
                if (faceGood) {
                    startHandle()
                    toState(State.HANDLE)
                }
            }
            State.HANDLE -> {
                if (faceGood) {
                    val preIdx = preFaceFrameIdx
                    val currIdx = frame.idx
                    blockHandler?.postBlock(frameQueue, preIdx, currIdx)
                    if (System.currentTimeMillis() - handleStartTime > handleTimeoutMs) {
                        logger.d(TAG, "timeout $handleTimeoutMs")
                        stopHandle()
                        toState(State.STOPPED)
                    }
                } else {
                    if (System.currentTimeMillis() - handleStartTime > 10_000L) {
                        logger.d(TAG, "end when out")
                        stopHandle()
                        toState(State.STOPPED)
                    } else {
                        restartHandle()
                        toState(State.STARTED)
                    }
                }
            }
            State.INITIALIZED,
            State.CREATED,
            State.DESTROYED,
            State.STOPPED,
            State.ERROR -> { /* nothing */ }
         }
    }

    private fun handleError(errType: ErrType, error: Exception?) {
        TODO()
        emitEvent(Event.ERROR, null)
    }

    private fun toState(newState: State) {
        logger.d(TAG, "toState: $state > $newState")
        val oldState = state
        state = newState
//        emitEvent(Event.STATE_CHANGE, StateChangeEvent(oldState, state))
    }

    private fun emitEvent(event: Event, data: Any?) {
        mainExecutor.execute {
            eventListener?.invoke(event, data)
        }
    }

    private fun setupFaceLandmarker(context: Context): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setDelegate(Delegate.GPU)
            .setModelAssetPath("face_landmarker.task")
            .build()
        val faceLandmarkerOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
//            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(false)
            .setErrorListener(this::onFaceLandmarkError)
            .build()
        val faceLandmarker = FaceLandmarker.createFromOptions(context.applicationContext, faceLandmarkerOptions)
        return faceLandmarker
    }

    private fun onFaceLandmarkError(error: RuntimeException) {
        logger.d( "onFaceLandmarkError",
            error.message ?: "An unknown error has occurred"
        )
        handleExecutor.execute {
            handleError(ErrType.FACE, error)
        }
    }

    private fun debugFrameQueue(frameQueue: List<Frame>) {
        var s = ""
        frameQueue.forEach {
            var f = FaceHelper.getFrameState(it)
            s += f
        }
        logger.d(TAG, "frameQueue [${frameQueue.size}] $s")
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

    class BlockHandler(val executor: ExecutorService, saveDirPath: String) {
        private val STATE_RUNNING = 1
        private val STATE_RELEASE = 2
        private var state = AtomicInteger(STATE_RUNNING)

        private val countDownSignal = CountDownSignal()

        val cacheRecord = ArrayList<Pair<Int, Int>>()

        val saveDir: File

        init {
            val batchDir = "${System.currentTimeMillis()}_${(Random.nextDouble() * 1000).toInt()}"
            saveDir = File(Path(saveDirPath, batchDir).pathString)
            saveDir.mkdirs()
        }

        fun await() {
            countDownSignal.await()
        }

        fun release() {
            state.set(STATE_RELEASE)
            if (executor.isShutdown) {
                doRelease()
            } else {
                executor.execute(::doRelease)
            }
        }

        private fun doRelease() {
            countDownSignal.await()
            try {
                saveDir.deleteRecursively()
            } catch (e: Exception) {
                // ignore
            }
        }

        fun postBlock(frameQueue: List<Frame>, preIdx: Int, currIdx: Int) {
            if (state.get() != STATE_RUNNING) {
                return
            }
            countDownSignal.add()
            executor.execute {
                if (state.get() == STATE_RUNNING) {
                    doBlock(frameQueue, preIdx, currIdx)
                }
                countDownSignal.countDown()
            }
        }

        private fun doBlock(frameQueue: List<Frame>, preIdx: Int, currIdx: Int) {
            val frame = frameQueue[currIdx]
            val bitmap = frame.frame!!
            val landmark = frame.faceResult!!.faceLandmarks()[0]
            val mesh = VitalsLib.genLandmarkPoints(landmark, bitmap.width, bitmap.height)
            frame.mesh = mesh
            frame.pixels = FaceHelper.extractCombinedPixels(bitmap, mesh)
            frame.frame = null // release bitmap
            if (preIdx >= 0) {
                if (FaceHelper.checkShareable(frameQueue, preIdx, currIdx)) {
                    val preFrame = frameQueue[preIdx]
                    val shareMesh = FaceHelper.calcShareMesh(preFrame.mesh!!, frame.mesh!!)
                    var i = currIdx
                    while (--i >= 0 && i > preIdx) {
                        val f = frameQueue[i]
                        f.shareMesh = shareMesh
                        f.pixels = FaceHelper.extractCombinedPixels(f.frame!!, shareMesh)
                        f.frame = null // release bitmap
                    }
                } else {
                    cacheRecord.add(Pair(preIdx, currIdx))
                    for (i in (preIdx + 1) until currIdx) {
                        val f = frameQueue[i]
                        f.addFlag(Frame.FLAG_CACHED)
                        storeFrame(f)
                    }
                }
            }
        }

        private fun storeFrame(frame: Frame): Boolean {
            return frame.frame?.let {
                saveBitmap2(it, getSaveFile(frame.idx)).also { saved ->
                    if (saved) {
                        frame.frame = null
                    }
                }
            } ?: false
        }

        fun restoreFrame(frame: Frame): Boolean {
            val saveFile = getSaveFile(frame.idx)
            val bitmap = readBitmap2(saveFile)
            return if (bitmap != null) {
                frame.frame = bitmap
                saveFile.delete()
                true
            } else {
                false
            }
        }

        private fun getSaveFile(idx: Int): File {
            return File(saveDir, "$idx.png")
        }

        private fun saveBitmap(bitmap: Bitmap, saveFile: File): Boolean {
            return try {
                val st = System.currentTimeMillis()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, saveFile.outputStream())
                Log.d("ActionSolution", "saveBitmap write cost: ${System.currentTimeMillis() - st} ms")
                true
            } catch (e: Throwable) {
                false
            }
        }

        private fun readBitmap(saveFile: File): Bitmap? {
            return if (saveFile.exists()) {
                return try {
                    BitmapFactory.decodeFile(saveFile.path)
                } catch (e: Throwable) {
                    null
                }
            } else {
                null
            }
        }

        private fun saveBitmap2(bitmap: Bitmap, saveFile: File): Boolean {
            return try {
                val st = System.currentTimeMillis()
                val os = saveFile.outputStream()
                val intByte = ByteArray(Int.SIZE_BYTES * 3)
                val intBuf = ByteBuffer.wrap(intByte)
                intBuf.putInt(bitmap.width)
                intBuf.putInt(bitmap.height)
                intBuf.putInt(bitmap.config.ordinal)
                os.write(intByte)
                val buf = ByteBuffer.allocate(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(buf)
                os.write(buf.array())
                os.close()
                true
            } catch (e: Throwable) {
                false
            }
        }

        private fun readBitmap2(saveFile: File): Bitmap? {
            return if (saveFile.exists()) {
                return try {
                    val ins = saveFile.inputStream()
                    val intByte = ByteArray(Int.SIZE_BYTES * 3)
                    val intBuf = ByteBuffer.wrap(intByte)
                    ins.read(intByte)
                    val width = intBuf.int
                    val height = intBuf.int
                    val ordinal = intBuf.int
                    val bitmapBytes = ins.readBytes()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.values()[ordinal])
                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bitmapBytes))
                    bitmap
                } catch (e: Throwable) {
                    null
                }
            } else {
                null
            }
        }
    }

    class FrameCacheManager(saveDirPath: String,
                            val executor: ExecutorService = Executors.newSingleThreadExecutor()) {

        private class FrameHolder(frame: Frame) {
            var data: Bitmap? = frame.frame
            var idx: Int = frame.idx
        }

        val saveDir: File

        private val cacheCountDownSignal = CountDownSignal()

        init {
            val batchDir = "${System.currentTimeMillis()}_${(Random.nextDouble() * 1000).toInt()}"
            saveDir = File(Path(saveDirPath, batchDir).pathString)
            saveDir.mkdirs()
        }

        fun push(frame: Frame) {
            val holder = FrameHolder(frame)
            frame.frame = null
            cacheCountDownSignal.add()
            executor.execute {
                saveData(holder)
                holder.data = null
                cacheCountDownSignal.countDown()
            }
        }

        fun pop(idx: Int): Bitmap? {
            val file = File(getSavePath(idx))
            return if (file.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(file.path)
                    file.delete()
                    bitmap
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        fun await() {
            cacheCountDownSignal.await()
        }

        private fun release() {
            executor.execute {
                cacheCountDownSignal.await()
                try {
                    saveDir.deleteRecursively()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }


        private fun getSavePath(idx: Int): String {
            return Path(saveDir.path, idx.toString()).pathString
        }

        private fun getSavePath(holder: FrameHolder): String {
            return getSavePath(holder.idx)
        }

        private fun getSaveFile(holder: FrameHolder): File {
            return File(saveDir, holder.idx.toString())
        }

        private fun saveData(holder: FrameHolder) {
            holder.data?.let {
                val saveFile = getSaveFile(holder)
                it.compress(Bitmap.CompressFormat.PNG, 100, saveFile.outputStream())
            }
        }

        private fun readData(holder: FrameHolder): Bitmap? {
            val saveFile = getSaveFile(holder)
            if (saveFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(saveFile.path)
                saveFile.delete()
                return bitmap
            } else {
                return null
            }
        }
    }

    class SampledData(frameQueue: ArrayList<Frame>, blockHandler: BlockHandler) {
        private var mFrameQueue: ArrayList<Frame>? = frameQueue
        private var mBlockHandler: BlockHandler? = blockHandler
        private var faceLandmarker: FaceLandmarker? = null

        fun release() {
            mBlockHandler?.release()
            mBlockHandler = null
            mFrameQueue = null
        }

        fun analyze(context: Context): ResultOrException<MeasureResult>? {
            val frameQueue = mFrameQueue!!
            if (frameQueue.size < 30 * 3) {
                return null // 长度太短
            }
            val cacheRecord = mBlockHandler?.cacheRecord ?: return null
            if (cacheRecord.size > 0) {
                log("createFaceLandmarker start")
                faceLandmarker = createFaceLandmarker(context)
                log("createFaceLandmarker end")
            }
            log("wait blockHandler start")
            mBlockHandler!!.await()
            log("wait blockHandler end")
            Log.d("ActionSolution", "FQS: ${FaceHelper.getFrameQueueState(frameQueue)}")
            for (pair in cacheRecord) {
                val (preIdx, currIdx) = pair
                processBlock(frameQueue, preIdx, currIdx)
            }
            log("processBlock finish")
            faceLandmarker?.close()
            Log.d("ActionSolution", "FQS: ${FaceHelper.getFrameQueueState(frameQueue)}")
            // 去尾
            for (i in frameQueue.size - 1 downTo  0) {
                val f = frameQueue[i]
                if (f.pixels == null) {
                    frameQueue.removeAt(i)
                } else {
                    break
                }
            }
            // 填充
            frameQueue.forEachIndexed { i, f ->
                if (f.pixels == null && i > 0) {
                    f.pixels = frameQueue[i - 1].pixels
                    f.addFlag(Frame.FLAG_FILL)
                }
            }
            Log.d("ActionSolution", "FQS: ${FaceHelper.getFrameQueueState(frameQueue)}")
            val res = NativeAnalyzer().analyzeAction(frameQueue, Port.copyBPModels(SdkManager.getContext()), -1, Gender.Male, -1.0, -1.0)
            log("res: $res")
            // debug
            var preFrame: Frame? = null
            for (i in 0 until frameQueue.size) {
                val f = frameQueue[i]
                if (f.mesh != null) {
                    if (preFrame != null) {
                        FaceHelper.checkShareable(frameQueue, preFrame.idx, f.idx)
                    }
                    preFrame = f
                }
            }
            // debug
            return res
        }

        private fun log(msg: String) = Log.d("ActionSolution", msg)

        private fun processBlock(frameQueue: List<Frame>, preIdx: Int, currIdx: Int) {
            if (currIdx - preIdx <= 1) {
                return
            }
            log("processBlock [$preIdx, $currIdx]")
            val midIdx = (currIdx + preIdx) / 2
            val midFrame = frameQueue[midIdx]
            mBlockHandler!!.restoreFrame(midFrame)
            midFrame.frame?.let { bitmap ->
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = faceLandmarker?.detect(mpImage)
                midFrame.faceResult = result
//                mpImage.close()
                result?.let {  faceLandmarkResult ->
                    val faceCheckResult = FaceHelper.checkFace(faceLandmarkResult, bitmap)
                    midFrame.faceCheckResult = faceCheckResult
                    if (faceCheckResult.faceOutType == FaceOutType.FACE_OUT_TYPE_PASS) {
                        val landmark = faceLandmarkResult.faceLandmarks()[0]
                        val mesh = VitalsLib.genLandmarkPoints(landmark, bitmap.width, bitmap.height)
                        midFrame.mesh = mesh
                        midFrame.pixels = FaceHelper.extractCombinedPixels(bitmap, mesh)
                        midFrame.frame = null // release bitmap
                    }
                }
            }
            if (midIdx - preIdx > 1) {
                shareBlockOrProcess(frameQueue, preIdx, midIdx)
            }
            if (currIdx - midIdx > 1) {
                shareBlockOrProcess(frameQueue, midIdx, currIdx)
            }
        }

        private fun shareBlockOrProcess(frameQueue: List<Frame>, preIdx: Int, currIdx: Int): Boolean {
            if (FaceHelper.checkShareable(frameQueue, preIdx, currIdx)) {
                log("shareBlock [$preIdx, $currIdx]")
                val preFrame = frameQueue[preIdx]
                val currFrame = frameQueue[currIdx]
                val shareMesh = FaceHelper.calcShareMesh(preFrame.mesh!!, currFrame.mesh!!)
                var i = currIdx
                while (--i >= 0 && i > preIdx) {
                    val f = frameQueue[i]
                    mBlockHandler?.restoreFrame(f)
                    f.shareMesh = shareMesh
                    f.pixels = f.frame?.let { FaceHelper.extractCombinedPixels(it, shareMesh) }
                    f.frame = null // release bitmap
                }
                return true
            } else {
                processBlock(frameQueue, preIdx, currIdx)
                return false
            }
        }

        private fun createFaceLandmarker(context: Context): FaceLandmarker {
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.GPU)
                .setModelAssetPath("face_landmarker.task")
                .build()
            val faceLandmarkerOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
//            .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(false)
                .build()
            return FaceLandmarker.createFromOptions(context, faceLandmarkerOptions)
        }
    }

    data class AnalyzeResult(var code: Int, var result: MeasureResult?)

    object FaceHelper {
        fun checkFace(result: FaceLandmarkerResult, inputBitmap: Bitmap): FaceCheckResult {
            val faceCheckResult = FaceCheckResult()

            if (result.faceLandmarks().size == 0) {
                return faceCheckResult.setFaceOutType(FaceOutType.FACE_OUT_TYPE_NO_FACE)
            }

            val normalizedLandmarks = result.faceLandmarks()[0]

            var box = Box()
            val outlineIdxs = listOf(1, 234, 127, 93, 21, 103, 109, 10, 338, 332, 251, 356, 454, 323, 288, 365, 378, 148, 152, 377, 149, 136, 58)
            outlineIdxs.forEachIndexed { i, idx ->
                val normalizedLandmark = normalizedLandmarks[idx]
                val x = normalizedLandmark.x()
                val y = normalizedLandmark.y()
                if (i == 0) {
                    box = Box(x, y, x, y)
                } else {
                    if (x < box.left) {
                        box.left = x
                    }
                    if (x > box.right) {
                        box.right = x
                    }
                    if (y < box.top) {
                        box.top = y
                    }
                    if (y > box.bottom) {
                        box.bottom = y
                    }
                }
            }
            faceCheckResult.setBox(box)

            val inBox = Box(box)
            inBox.left = max(0f, min(1f, inBox.left))
            inBox.top = max(0f, min(1f, inBox.top))
            inBox.right = max(0f, min(1f, inBox.right))
            inBox.bottom = max(0f, min(1f, inBox.bottom))
            val inArea = inBox.width * inBox.height
            val boxArea = box.width * box.height
            if (inArea / boxArea < 0.3) {
                return faceCheckResult.setFaceOutType(FaceOutType.FACE_OUT_TYPE_OUT_BOX)
            }

            return faceCheckResult.setFaceOutType(FaceOutType.FACE_OUT_TYPE_PASS)
        }

        fun checkShareable(frameQueue: List<Frame>, preIdx: Int, currIdx: Int): Boolean {
            val preFrame = frameQueue[preIdx]
            val currFrame = frameQueue[currIdx]
            if (preFrame.faceCheckResult?.faceOutType != FaceOutType.FACE_OUT_TYPE_PASS
                || currFrame.faceCheckResult?.faceOutType != FaceOutType.FACE_OUT_TYPE_PASS) {
                return false
            }
            val preFaceResult = preFrame.faceResult
            val currFaceResult = currFrame.faceResult
            if (preFaceResult == null || currFaceResult == null) {
                return false
            }
            val preLandmark = preFaceResult.faceLandmarks()[0]
            val currLandmark = currFaceResult.faceLandmarks()[0]
            val outlineIdxs = listOf(1, 234, 127, 93, 21, 103, 109, 10, 338, 332, 251, 356, 454, 323, 288, 365, 378, 148, 152, 377, 149, 136, 58)
            var sum = 0f
            for (i in outlineIdxs) {
                val p1 = currLandmark[i]
                val p2 = preLandmark[i]
                val dx = p1.x() - p2.x()
                val dy = p1.y() - p2.y()
                val d = sqrt(dy * dy + dx * dx)
                sum += d
//                Log.d("ActionSolution", "d $d, dx $dx, dy $dy")
            }
            val diff = sum / outlineIdxs.size
//            Log.d("ActionSolution", "diff[$diff], sum $sum, size ${outlineIdxs.size}")
            Log.d("ActionSolution", "df[$preIdx, $currIdx, $diff]")
            return diff < 0.01
        }

        fun calcShareMesh(s1: List<Point>, s2: List<Point>): List<Point> {
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

        fun extractCombinedPixels(bitmap: Bitmap, landmark: List<Point>): Port.CombinedPixels {
            val st = System.currentTimeMillis()
            val pixels = Port.extractCombinedPixels(bitmap, landmark)
            val et = System.currentTimeMillis()
    //        pixelsStats.push(TimeStats(st, et))
            return pixels
        }

        fun getFrameState(f: Frame): String {
            // val S = 'S'
            // val P = 'P'
            // val M = 'M'
            // val F = 'F'
            // val N = 'N'
            val P = "*"
            val SP = "-"
            val CP = "+"
            val SCP = "="
            val S = "S"
            val C = "C"
            val M = "#"
            val F = "."
            val N = "N"
            val FILL = "X"
            return when {
                f.hasFlag(Frame.FLAG_FILL) -> FILL
                f.hasFlag(Frame.FLAG_CACHED) && f.shareMesh != null && f.pixels != null -> SCP
                f.hasFlag(Frame.FLAG_CACHED) && f.pixels != null -> CP
                f.hasFlag(Frame.FLAG_CACHED) -> C
                f.shareMesh != null && f.pixels != null -> SP
                f.pixels != null -> P
                f.shareMesh != null -> S
                f.mesh != null -> M
                f.frame != null -> F
                else -> N
            }
        }

        fun getFrameQueueState(frameQueue: List<Frame>): String {
            var s = ""
            frameQueue.forEach {
                var f = FaceHelper.getFrameState(it)
                s += f
            }
            return s
        }
    }
}