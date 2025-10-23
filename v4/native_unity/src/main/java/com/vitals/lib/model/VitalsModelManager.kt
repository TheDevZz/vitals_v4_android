package com.vitals.lib.model

import android.content.Context
import java.io.File

object VitalsModelManager {

    class CheckResult(
        val fineList: List<String>,
        val missList: List<String>,
    ) {
        val ok: Boolean
            get() = missList.isEmpty()
    }

    val modelNameList: ArrayList<String> = arrayListOf(
//        "ep-5_test_loss-1.1068_test_acc-0.4359.pt",
//        "HBP_06302023__model=ResCNN1D3_nclasses=1_pretrained=0_loss15.12446915_epoch3.pt",
//        "LBP_06302023__model=ResCNN1D3_nclasses=1_pretrained=0_loss10.89517215_epoch268.pt",
//        "model=ResCNN1D_nclasses=3_pretrained=0_class0.pt",
//        "model=ResCNN1D_nclasses=3_pretrained=0_class1.pt",
//        "model=ResCNN1D_nclasses=3_pretrained=0_class2.pt",
    )

    fun prepareModels(context: Context): String {
        val modelsDir = File(context.cacheDir, "models")
        modelsDir.mkdirs()
        val assets = context.assets
        modelNameList.forEach {
            val output = File(modelsDir, it)
            if (!output.exists()) {
                assets.open(it).copyTo(File(modelsDir, it).outputStream())
            }
        }
        return modelsDir.path + File.separatorChar
    }

    fun checkModels(modelDirPath: String): CheckResult {
        val fineList = ArrayList<String>()
        val missList = ArrayList<String>()
        modelNameList.forEach {
            val file = File(modelDirPath, it)
            if (file.isFile) {
                fineList.add(it)
            } else {
                missList.add(it)
            }
        }
        return CheckResult(fineList, missList)
    }
}