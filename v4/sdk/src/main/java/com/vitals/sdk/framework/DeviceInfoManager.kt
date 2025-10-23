package com.vitals.sdk.framework

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.appcompat.app.AppCompatActivity.ACTIVITY_SERVICE
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.security.MessageDigest


class DeviceInfoManager(val sp: SharedPreferences) {
    private val SP_KEY_DEVICE_INFO_HASH = "device_hash"
    private val SP_KEY_DEVICE_ID = "device_id"

    private val TAG = "DeviceInfoManager"
    private val logger = SdkManager.getLogger()

    fun executeReport(
        context: Context,
        url: String,
        uuid: String,
        outUserId: String,
    ) {
        try {
            val deviceInfoJson = buildDeviceInfoJson(context, uuid, outUserId)
            val newHash = deviceInfoJson.toString().hashCode()
            val cachedHash = sp.getInt(SP_KEY_DEVICE_INFO_HASH, 0)
            if (newHash != cachedHash) {
                // 信息有变化，更新缓存并上报
                if (report(url, deviceInfoJson)) {
                    sp.edit { putInt(SP_KEY_DEVICE_INFO_HASH, newHash) }
                }
            } else {
                logger?.d(TAG, "Device info unchanged, skipping report.")
            }
        } catch (e: Exception) {
            logger?.e(TAG, "Error occurred while reporting device info", e)
        }
    }

    fun report(
        url: String,
        deviceInfoJson: JSONObject,
    ): Boolean {
        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), deviceInfoJson.toString())
        val request = Request.Builder().url(url).post(body).build()
        val client = OkHttpClient.Builder()
            .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    logger?.d(TAG, "Device info reported successfully")
                    return true
                } else {
                    logger?.e(TAG, "Failed to report device info: $response")
                    return false
                }
            }
        } catch (e: Throwable) {
            logger?.e(TAG, "Error reporting device info", e)
        }
        return false
    }

    fun buildDeviceInfoJson(
        context: Context,
        uuid: String,
        outUserId: String,
    ): JSONObject {
        val json = JSONObject()
        json.put("uuid", uuid)
        json.put("outUserId", outUserId)

        json.put("platform", "Android")
        json.put("brand", Build.BRAND)
        json.put("model", Build.MODEL)
        json.put("osVersion", Build.VERSION.SDK_INT.toString())
        json.put("language", context.resources.configuration.locales[0].language)

        val memorySizeMB = getMemoryInfo(context).totalMem / (1024 * 1024)
        val storageSizeMB = getInternalStorageInfo().totalSize / (1024 * 1024)
        json.put("memorySize", memorySizeMB)
        json.put("storageSize", storageSizeMB)

        val imei = getIMEI(context)
        val androidId = getAndroidId(context)
        val deviceId = getDeviceId(context)
        json.put("imei", imei)
        json.put("androidId", androidId)
        json.put("deviceId", deviceId)

        return json
    }

    /**
    * 获取设备唯一标识符
    * 优先从缓存获取，如果没有则重新生成
    */
    fun getDeviceId(context: Context): String {
        synchronized(DeviceInfoManager::class.java) {
            // 先尝试从缓存获取
            val cachedId = sp.getString(SP_KEY_DEVICE_ID, null)
            if (!cachedId.isNullOrEmpty()) {
                return cachedId
            }

            // 生成新的设备ID
            val newId = generateDeviceId(context)

            // 缓存到SharedPreferences
            sp.edit { putString(SP_KEY_DEVICE_ID, newId) }
            return newId
        }
    }

    /**
     * 生成基于稳定设备信息的唯一标识符
     * 只使用不会因应用重装、权限变化、SIM卡变化而改变的信息
     */
    private fun generateDeviceId(context: Context): String {
        val deviceInfo = collectStableDeviceInfo(context)
        val combinedInfo = deviceInfo.joinToString("|")

        logger?.d(TAG, "稳定设备信息: $deviceInfo")

        // 使用SHA-256生成最终的设备ID
        return sha256(combinedInfo).take(32) // 取前32位
    }

    /**
     * 收集稳定的设备信息
     * 只包含在设备生命周期内基本不变的信息
     */
    @SuppressLint("HardwareIds")
    private fun collectStableDeviceInfo(context: Context): List<String> {
        val deviceInfo = mutableListOf<String>()

        try {
            // 1. Android ID (最重要且相对稳定的标识符)
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
                deviceInfo.add("aid:$androidId")
            }

            // 2. 设备硬件信息（出厂后基本不变）
            deviceInfo.add("brand:${Build.BRAND}")
            deviceInfo.add("model:${Build.MODEL}")
            deviceInfo.add("device:${Build.DEVICE}")
            deviceInfo.add("board:${Build.BOARD}")
            deviceInfo.add("hardware:${Build.HARDWARE}")
            deviceInfo.add("product:${Build.PRODUCT}")

            // 3. CPU架构信息（硬件固定）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                deviceInfo.add("abis:${Build.SUPPORTED_ABIS.contentToString()}")
            } else {
                @Suppress("DEPRECATION")
                deviceInfo.add("abi:${Build.CPU_ABI}")
                @Suppress("DEPRECATION")
                deviceInfo.add("abi2:${Build.CPU_ABI2}")
            }

            // 4. 屏幕硬件信息（物理特性，基本不变）
            val displayMetrics = context.resources.displayMetrics
            // 使用物理尺寸而不是像素，避免因UI缩放变化
            deviceInfo.add("screenSize:${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
            deviceInfo.add("densityDpi:${displayMetrics.densityDpi}")

            // 5. 序列号（如果可获取）
//            val serialNumber = getSerialNumber()
//            if (serialNumber != "unknown" && serialNumber != "no_permission") {
//                deviceInfo.add("serial:$serialNumber")
//            }

            // 6. 构建指纹的哈希（包含多种构建信息）
            deviceInfo.add("fingerprint:${Build.FINGERPRINT.hashCode()}")

            // 7. 系统版本（虽然可能升级，但有助于区分设备）
            deviceInfo.add("sdk:${Build.VERSION.SDK_INT}")

            // 8. 添加一些额外的构建信息
            deviceInfo.add("bootloader:${Build.BOOTLOADER}")
            deviceInfo.add("host:${Build.HOST.hashCode()}") // 构建主机信息的哈希
            deviceInfo.add("user:${Build.USER.hashCode()}") // 构建用户信息的哈希

        } catch (e: Exception) {
            logger?.e(TAG, "收集设备信息时出错", e)
            // 添加错误标识，确保即使出错也能生成ID
            deviceInfo.add("error:${e.javaClass.simpleName}")
        }

        // 确保至少有一些基础信息
        if (deviceInfo.isEmpty()) {
            deviceInfo.add("fallback:${(Build.BRAND + Build.MODEL + Build.DEVICE).hashCode()}")
        }

        return deviceInfo
    }

    private fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger?.e(TAG, "SHA-256加密失败", e)
            // 备用哈希方案
            input.hashCode().toString(16).padStart(8, '0')
        }
    }

    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String? {
        return try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            androidId
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    fun getIMEI(context: Context): String? {
        try {
            // 必要的权限检查
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
                return null
            }

            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null

            // getImei 在 API 26+ 可用；在更老版本上可用 getDeviceId（已弃用）
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    // 某些设备/平台可能在调用时抛 SecurityException（比如 Android 10+ 非特权 app）
                    try {
                        tm.imei ?: tm.getDeviceId()
                    } catch (se: SecurityException) {
                        // 无权访问：安全回退
                        null
                    }
                }
                else -> {
                    try {
                        @Suppress("DEPRECATION")
                        tm.deviceId
                    } catch (se: SecurityException) {
                        null
                    }
                }
            }
        } catch (t: Throwable) {
            // 捕获所有异常，避免崩溃
            return null
        } catch (e: Error) {
            // e.g. NoSuchMethodError: No virtual method getMeid()
            return null
        }
    }

    data class StorageInfo(val totalSize: Long, val availSize: Long)

    fun getInternalStorageInfo(): StorageInfo {
        val internalStoragePath = Environment.getDataDirectory() // 内部存储路径
        val statFs = StatFs(internalStoragePath.absolutePath)

        val totalSize: Long
        val availableSize: Long

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) { // API 18+
            totalSize = statFs.totalBytes
            availableSize = statFs.availableBytes
        } else {
            val blockSize = statFs.blockSizeLong
            val totalBlocks = statFs.blockCountLong
            val availableBlocks = statFs.availableBlocksLong

            totalSize = blockSize * totalBlocks
            availableSize = blockSize * availableBlocks
        }

        return StorageInfo(totalSize, availableSize)
    }

    fun getMemoryInfo(context: Context): MemoryInfo {
        val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memInfo = MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo
    }
}