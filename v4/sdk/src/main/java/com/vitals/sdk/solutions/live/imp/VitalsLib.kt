package com.vitals.sdk.solutions.live.imp

import android.graphics.Bitmap
import android.graphics.Point
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.vitals.lib.Port
import com.vitals.lib.VitalsNativeException
import com.vitals.lib.model.VitalsModelManager
import com.vitals.sdk.api.MeasureResult
import com.vitals.sdk.framework.ErrCode
import com.vitals.sdk.framework.ResultOrException
import com.vitals.sdk.framework.SdkManager
import com.vitals.sdk.framework.VitalsException
import java.io.File

object VitalsLib {

    val modelNameList = arrayListOf(
        "ep-5_test_loss-1.1068_test_acc-0.4359.pt",
        "HBP_06302023__model=ResCNN1D3_nclasses=1_pretrained=0_loss15.12446915_epoch3.pt",
        "LBP_06302023__model=ResCNN1D3_nclasses=1_pretrained=0_loss10.89517215_epoch268.pt",
        "model=ResCNN1D_nclasses=3_pretrained=0_class0.pt",
        "model=ResCNN1D_nclasses=3_pretrained=0_class1.pt",
        "model=ResCNN1D_nclasses=3_pretrained=0_class2.pt",
    )

    fun checkModels(modelDirPath: String): VitalsModelManager.CheckResult {
        val fineList = ArrayList<String>()
        val missList = ArrayList<String>()
        VitalsModelManager.modelNameList.forEach {
            val file = File(modelDirPath, it)
            if (file.isFile) {
                fineList.add(it)
            } else {
                missList.add(it)
            }
        }
        return VitalsModelManager.CheckResult(fineList, missList)
    }

    fun extractPixels(bitmap: Bitmap, landmark: List<NormalizedLandmark>): List<DoubleArray> {
        val width = bitmap.width
        val height = bitmap.height
//        val landmarkPoints = landmark.map { Point((it.x() * width).toInt(), (it.y() * height).toInt()) }
        val landmarkPoints = genLandmarkPoints(landmark, width, height)
        return Port.extractPixels(bitmap, landmarkPoints)
    }

    fun genLandmarkPoints(landmark: List<NormalizedLandmark>, width: Int, height: Int): List<Point> {
        return landmark.map { Point((it.x() * width).toInt(), (it.y() * height).toInt()) }
    }

    fun convertMeasureResult(measureResultV2: Port.MeasureResult?): MeasureResult? {
        if (measureResultV2 == null) {
            return null
        } else {
            val res = MeasureResult()
            res.heartRate = measureResultV2.hr.toFloat()
            res.respirationRate = measureResultV2.rr.toFloat()
            res.heartRateVariability = measureResultV2.hrv.toFloat()
            res.oxygenSaturation = measureResultV2.spo2.toFloat()

            res.stress = measureResultV2.stress.toFloat()

            res.systolicBloodPressure = measureResultV2.hbp.toFloat()
            res.diastolicBloodPressure = measureResultV2.lbp.toFloat()

            res.confidence = measureResultV2.ratio.toFloat()
            res.createTime = System.currentTimeMillis()
            return res
        }
    }

    fun processPixelsV2(pixels: DoubleArray, shape: IntArray, fps: Double, modelsDir: String,
                        age: Int?, gender: Int?, height: Double?, weight: Double?): ResultOrException<MeasureResult> {
        val analyzeResult = ResultOrException<MeasureResult>(null, null)
        try {
            val res :Port.MeasureResult? =
                if (age != null && gender != null && height != null && weight != null) {
                    Port.processPixelsV2(pixels, shape, fps, modelsDir, age, gender, height, weight)
                } else {
                    Port.processPixelsV2(pixels, shape, fps, modelsDir)
                }
            analyzeResult.data = convertMeasureResult(res)
        } catch (e: VitalsNativeException) {
            val exception: VitalsException =
                if (e.errCode.contains("c10::ERROR")) {
                    val msg = e.message ?: ""
                    if (msg.contains("open file failed")) {
                        VitalsException(ErrCode.OPEN_MODEL_FAIL, msg, e)
                    } else {
                        VitalsException(ErrCode.RUN_MODEL_FAIL, msg, e)
                    }
                } else if (e.errCode.contains("std::exception")) {
                    VitalsException(ErrCode.ANALYZER_FAIL, e.message?:"", e)
                } else {
                    VitalsException(ErrCode.ANALYZER_FAIL, e.message?:"", e)
                }
            analyzeResult.exception = exception
            SdkManager.getCrashHandler().processException(exception)
        }
        return analyzeResult
    }
}