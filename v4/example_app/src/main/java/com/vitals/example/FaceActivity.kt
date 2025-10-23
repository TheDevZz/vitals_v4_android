package com.vitals.example

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.vitals.example.databinding.ActivityFaceBinding
import com.vitals.sdk.api.FaceState
import com.vitals.sdk.api.SamplerEventListener
import com.vitals.sdk.api.SamplerState
import com.vitals.sdk.api.Vitals
import com.vitals.sdk.api.VitalsRuntimeException
import com.vitals.sdk.api.VitalsSampledData
import kotlin.math.ceil
import kotlin.math.roundToInt

class FaceActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityFaceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityFaceBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // 设置屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 设置屏幕最亮，用以补光
        window.attributes.screenBrightness = 1f

        // 将预览视图设置为圆形
        viewBinding.previewView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.measuredWidth, view.measuredHeight)
            }
        }
        viewBinding.previewView.clipToOutline = true

        if (checkPermissions()) {
            setup()
        } else {
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    setup()
                } else {
                    Toast.makeText(
                        this,
                        "Permission request denied",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.launch(android.Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 恢复屏幕亮度
        window.attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }


    private fun checkPermissions() = arrayOf(android.Manifest.permission.CAMERA).all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun setup() {
        val solution = Vitals.getSdkInstance().getSolution()
        val vitalsSampler = solution.createSampler()
        vitalsSampler.bindToLifecycle(this, this, viewBinding.previewView)
        vitalsSampler.setEventListener(object : SamplerEventListener {
            override fun onFaceStateChange(state: FaceState) {
                viewBinding.tips.text = getTips(state)
            }

            override fun onSamplerStateChange(state: SamplerState) {
                if (state == SamplerState.SAMPLING) {
                    viewBinding.progress.progress = 0
                    viewBinding.countdownText.text = ""
                    viewBinding.progress.visibility = View.VISIBLE
                    viewBinding.countdownText.visibility = View.VISIBLE
                } else {
                    viewBinding.progress.visibility = View.INVISIBLE
                    viewBinding.countdownText.visibility = View.INVISIBLE
                }
            }

            override fun onProgressChange(progress: Float, remainingTimeMs: Long) {
                viewBinding.progress.progress = (progress * 100).roundToInt()
                viewBinding.countdownText.text = "剩余${ceil(remainingTimeMs/1000.0).toInt()}s"
            }

            override fun onQualityChange(quality: Int) {
                viewBinding.star1.isActivated = quality >= 1
                viewBinding.star2.isActivated = quality >= 2
                viewBinding.star3.isActivated = quality >= 3
                viewBinding.star4.isActivated = quality >= 4
                viewBinding.star5.isActivated = quality >= 5
            }

            override fun onSampledData(sampledData: VitalsSampledData) {
                DataBridge.sampledData = sampledData
                startActivity(Intent(this@FaceActivity, ResultActivity::class.java))
                finish()
            }

            override fun onError(e: VitalsRuntimeException) {
                AlertDialog.Builder(this@FaceActivity)
                    .setTitle("发生错误")
                    .setMessage("code: ${e.errCode}, msg: ${e.message}, case: ${e.cause}")
                    .show()
            }

        })
    }

    private fun getTips(faceState: FaceState): String {
        return when(faceState) {
            FaceState.NO_FACE -> "请面对镜头"
            FaceState.OUT_OF_FRAME -> "请保持面部在取景框内"
            FaceState.TOO_FAR -> "请靠近"
            FaceState.TOO_DARK -> "光线太暗"
            FaceState.UNSTEADY -> "请保持静止，检测期间不要移动"
            FaceState.OK -> "请保持脸部稳定，勿中途退出"
        }
    }
}