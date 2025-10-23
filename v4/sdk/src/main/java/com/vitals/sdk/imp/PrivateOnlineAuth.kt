package com.vitals.sdk.imp

import com.vitals.sdk.api.VitalsSdkInitOption
import com.vitals.sdk.framework.ErrCode
import com.vitals.sdk.framework.SdkManager
import com.vitals.sdk.framework.VitalsException
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

class PrivateOnlineAuth {
    fun authorize(vitalsSdkInitOption: VitalsSdkInitOption): Boolean {
        val appId = vitalsSdkInitOption.appId
        val appSecret = vitalsSdkInitOption.appSecret
        val outUserId = vitalsSdkInitOption.outUserId
        val serverUrl = SdkManager.getNetService().serverUrl
//        if (serverUrl.isBlank()) {
//            throw VitalsException(ErrCode.AUTH_ILLEGAL_ARGUMENT, "serverUrl can't be empty")
//        }
        if (appId.isBlank()) {
            throw VitalsException(ErrCode.AUTH_ILLEGAL_ARGUMENT, "appId can't be empty")
        }
        if (appSecret.isBlank()) {
            throw VitalsException(ErrCode.AUTH_ILLEGAL_ARGUMENT, "appSecret can't be empty")
        }
        if (outUserId.isBlank()) {
            throw VitalsException(ErrCode.AUTH_ILLEGAL_ARGUMENT, "outUserId can't be empty")
        }
        var url = serverUrl
//        if (!url.endsWith('/')) {
//            url += '/'
//        }
        url += "/user/authorize"

        return callRequest(url, appId, appSecret, outUserId)
    }

    private fun callRequest(
        urlStr: String,
        appId: String,
        appSecret: String,
        outUserId: String,
    ): Boolean {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 3000
        connection.readTimeout = 3000

        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");

//        val data = "{" +
//                "\"appId\"=\"" + URLEncoder.encode(appId, "UTF-8") + "\"," +
//                "\"timestamp\"=\"" + URLEncoder.encode(timestamp, "UTF-8") + "\"," +
//                "\"nonce\"=\"" + URLEncoder.encode(nonce, "UTF-8") + "\"," +
//                "\"sign\"=\"" + URLEncoder.encode(sign, "UTF-8") + "\"," +
//                "\"entry\"=\"" + URLEncoder.encode(entry, "UTF-8") + "\"," +
//                "\"outUserId\"=\"" + URLEncoder.encode(outUserId, "UTF-8") + "\"" +
//                "}"
        val timestamp = System.currentTimeMillis()
        val sign = generateSign(appId, outUserId, timestamp, appSecret)
        val json = JSONObject()
            .put("appId", appId)
            .put("outUserId", outUserId)
            .put("timestamp", timestamp)
            .put("sign", sign)
//            .put("nonce", nonce)
//            .put("entry", entry)
            .toString()
        connection.doInput = true
        connection.doOutput = true
        connection.useCaches = false
        connection.connect()
        val dos = DataOutputStream(connection.outputStream)
        dos.write(json.toByteArray())
        dos.flush()
        dos.close()

        val statusCode = connection.responseCode
        try {
            if (statusCode == 200) {
                val res = connection.inputStream.bufferedReader().readText()
                connection.inputStream.close()
                connection.disconnect()
                val jsonObject = JSONObject(res)
                val code = jsonObject.optInt("code", -1)
                val token = jsonObject.optJSONObject("data")?.optString("token")
                if (code != 0 || token.isNullOrEmpty()) {
                    throw VitalsException(ErrCode.AUTH_DENIED, "Authentication fails: $res")
                }
                SdkManager.sp.edit().putString("token", token).apply()
                return true
            } else {
                val err = connection.errorStream.bufferedReader().readText()
                connection.disconnect()
                throw VitalsException(ErrCode.AUTH_BAD_STATUS, "Bad statusCode $statusCode: $err")
            }
        } catch (e: IOException) {
            throw VitalsException(ErrCode.AUTH_NET_ERR, e.message?:"network error", e)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun generateSign(appId: String, outUserId: String, timestamp: Long, key: String): String {
        val params = listOf(
            "appId" to appId,
            "outUserId" to outUserId,
            "timestamp" to timestamp.toString(),
        )
        params.sortedBy { it.first }
        var signStr = params.joinToString("&") { "${it.first}=${it.second}"}
        signStr += "&key=$key"
        val digest = MessageDigest.getInstance("MD5")
        val sign = digest.digest(signStr.encodeToByteArray()).toHexString()
        return sign
    }
}