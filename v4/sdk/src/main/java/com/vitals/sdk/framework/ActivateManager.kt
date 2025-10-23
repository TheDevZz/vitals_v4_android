package com.vitals.sdk.framework

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import okhttp3.MediaType
import okhttp3.OkHttpClient
import org.json.JSONObject

class ActivateManager(val sp: SharedPreferences) {
    private val SP_KEY_ACTIVATE: String = "activate"

    fun isActivated(): Boolean {
        return sp.getBoolean(SP_KEY_ACTIVATE, false)
    }

    fun updateActivateState(activated: Boolean) {
        sp.edit { putBoolean(SP_KEY_ACTIVATE, activated) }
    }

    fun executeActivation(
        context: Context,
        url: String,
        appId: String,
        outUserId: String,
        uuid: String,
        sdkVersion: String,
    ): Boolean {
        if (!isActivated()) {
            val result = report(context, url, appId, outUserId, uuid, sdkVersion)
            updateActivateState(result)
            return result
        }
        return false
    }

    fun report(
        context: Context,
        url: String,
        appId: String,
        outUserId: String,
        uuid: String,
        sdkVersion: String,
    ): Boolean {
        val json = JSONObject()
        json.put("appId", appId)
        json.put("uuid", uuid)
        json.put("outUserId", outUserId)
        json.put("sdkVersion", sdkVersion)

        val appPackage = context.applicationInfo.packageName
        val activationTime = System.currentTimeMillis()
        val timeZone = java.util.TimeZone.getDefault().id
        val utcOffsetMinutes = java.util.Calendar.getInstance().get(java.util.Calendar.ZONE_OFFSET) / 60000
        json.put("appPackage", appPackage)
        json.put("activationTime", activationTime)
        json.put("timeZone", timeZone)
        json.put("utcOffset", utcOffsetMinutes)

        val body = okhttp3.RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json.toString())
        val request = okhttp3.Request.Builder().url(url).post(body).build()
        val client = OkHttpClient.Builder()
            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        try {
            client.newCall(request)
                .execute().use { response ->
                    if (response.isSuccessful) {
                        updateActivateState(true)
                        SdkManager.getLogger()?.d("ActivateManager", "report activation success")
                        return true
                    } else {
                        SdkManager.getLogger()?.e("ActivateManager", "report activation failed: ${response.code()}")
                    }
                }
        } catch (e: Throwable) {
            SdkManager.getLogger()?.e("ActivateManager", "report activation failed", e)
        }
        return false
    }
}