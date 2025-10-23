package com.vitals.sdk.imp

import com.vitals.sdk.api.VitalsSdkInitOption
import com.vitals.sdk.framework.ErrCode
import com.vitals.sdk.framework.SdkManager
import com.vitals.sdk.framework.VitalsException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

class OnlineAuth {
    fun authorize(vitalsSdkInitOption: VitalsSdkInitOption): Boolean {
        val appId = vitalsSdkInitOption.appId
        val appSecret = ""//vitalsSdkInitOption.appSecret
        val outUserId = vitalsSdkInitOption.outUserId
        if (appId.isBlank()) {
            throw VitalsException(ErrCode.AUTH_ILLEGAL_ARGUMENT, "appId can't be empty")
        }
        if (appSecret.isBlank()) {
            throw VitalsException(ErrCode.AUTH_ILLEGAL_ARGUMENT, "appSecret can't be empty")
        }
        if (outUserId.isBlank()) {
            throw VitalsException(ErrCode.AUTH_ILLEGAL_ARGUMENT, "outUserId can't be empty")
        }
        val entry = ""//vitalsSdkInitOption.entry
        val now = System.currentTimeMillis()
        val timestamp = now.toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val str = appId + appSecret + nonce + timestamp + outUserId
        val digest = MessageDigest.getInstance("MD5")
        @OptIn(ExperimentalStdlibApi::class)
        val sign = digest.digest(str.toByteArray()).toHexString()
        val url = "https://api.utours.cn/open-service/face-detect/sdk/authorize"

        return callRequest(url, appId, timestamp, nonce, sign, entry, outUserId)
    }

    private fun callRequest(
        urlStr: String,
        appId: String,
        timestamp: String,
        nonce: String,
        sign: String,
        entry: String,
        outUserId: String,
    ): Boolean {
        val data = "appId=${appId}" +
                "&timestamp=" + URLEncoder.encode(timestamp, "UTF-8") +
                "&nonce=" + URLEncoder.encode(nonce, "UTF-8") +
                "&sign=" + URLEncoder.encode(sign, "UTF-8") +
                "&entry=" + URLEncoder.encode(entry, "UTF-8") +
                "&outUserId=" + URLEncoder.encode(outUserId, "UTF-8")
        val url = URL("$urlStr?$data")
//        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000
        connection.readTimeout = 3000

//        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
//        connection.setRequestProperty("Accept", "application/json");

//        val data = "{" +
//                "\"appId\"=\"" + URLEncoder.encode(appId, "UTF-8") + "\"," +
//                "\"timestamp\"=\"" + URLEncoder.encode(timestamp, "UTF-8") + "\"," +
//                "\"nonce\"=\"" + URLEncoder.encode(nonce, "UTF-8") + "\"," +
//                "\"sign\"=\"" + URLEncoder.encode(sign, "UTF-8") + "\"," +
//                "\"entry\"=\"" + URLEncoder.encode(entry, "UTF-8") + "\"," +
//                "\"outUserId\"=\"" + URLEncoder.encode(outUserId, "UTF-8") + "\"" +
//                "}"
//        val json = JSONObject()
//            .put("appId", appId)
//            .put("timestamp", timestamp)
//            .put("nonce", nonce)
//            .put("sign", sign)
//            .put("entry", entry)
//            .put("outUserId", outUserId)
//            .toString()
//        connection.doInput = true
//        connection.doOutput = true
//        connection.useCaches = false
//        connection.connect()
//        val dos = DataOutputStream(connection.outputStream)
//        dos.write(json.toByteArray())
//        dos.flush()
//        dos.close()

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


}