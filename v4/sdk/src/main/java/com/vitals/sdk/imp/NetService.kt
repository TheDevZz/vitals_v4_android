package com.vitals.sdk.imp

import com.google.gson.Gson
import com.vitals.sdk.api.MeasureResult
import com.vitals.sdk.framework.ErrCode
import com.vitals.sdk.framework.ResultOrException
import com.vitals.sdk.framework.RoECallback
import com.vitals.sdk.framework.SdkManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException

class NetService {
    var token = ""
    var serverUrl = ""

    private fun createRequestBuilder(urlPath: String): Request.Builder {
        return Request.Builder()
            .url(serverUrl + urlPath)
            .addHeader("Authorization", "Bearer $token")
    }

    private fun createJsonBody(json: String): RequestBody {
        return RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            json
        )
    }

    private fun createPost(urlPath: String, body: RequestBody): Call {
        return OkHttpClient().newCall(createRequestBuilder(urlPath).post(body).build())
    }

    private fun cvtJson(obj: Any): String {
        return Gson().toJson(obj)
    }

    fun uploadResult(res: MeasureResult, callback: RoECallback<Boolean>) {
        SdkManager.getLogger()?.d("NetService", "uploadResult")
        createPost("/detect/save", createJsonBody(cvtJson(res))).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.invoke(ResultOrException.error(ErrCode.NET_FAIL, e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null) {
                        callback.invoke(ResultOrException.error(ErrCode.NET_INVALID_BODY, "body is null"))
                    } else {
                        try {
                            val jsonStr = body.string()
                            val json = JSONObject(jsonStr)
                            if (json.has("code")) {
                                val code = json.getInt("code")
                                if (code == 0) {
                                    callback.invoke(ResultOrException.success(true))
                                } else {
                                    callback.invoke(ResultOrException.success(false))
                                }
                            } else {
                                callback.invoke(ResultOrException.error(ErrCode.NET_INVALID_BODY, "invalid body"))
                            }
                        } catch (e: Exception) {
                            callback.invoke(ResultOrException.error(ErrCode.NET_PARSE_BODY_FAIL, "parse body fail", e))
                        }
                    }
                } else {
                    callback.invoke(ResultOrException.error(ErrCode.NET_INVALID_STATUS_CODE, "invalid status code. $response"))
                }
            }
        })
    }

    fun uploadLogFile(files: List<String>, callback: RoECallback<Boolean>) {
        return uploadLogFileParams(
            files.map { path ->
                val file = File(path)
                FileParams(file.name, path)
            },
            callback
        )
    }

    fun uploadLogFileParams(files: List<FileParams>, callback: RoECallback<Boolean>) {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
        files.forEach {
            val file = File(it.absolutePath)
            builder.addFormDataPart(it.relativePath, file.name, RequestBody.create(null, file))
        }
        val requestBody = builder.build()
        createPost("/sdkLog/save", requestBody).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.invoke(ResultOrException.error(ErrCode.NET_FAIL, e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null) {
                        callback.invoke(ResultOrException.error(ErrCode.NET_INVALID_BODY, "body is null"))
                    } else {
                        try {
                            val jsonStr = body.string()
                            val json = JSONObject(jsonStr)
                            if (json.has("code")) {
                                val code = json.getInt("code")
                                if (code == 0) {
                                    callback.invoke(ResultOrException.success(true))
                                } else {
                                    callback.invoke(ResultOrException.success(false))
                                }
                            } else {
                                callback.invoke(ResultOrException.error(ErrCode.NET_INVALID_BODY, "invalid body"))
                            }
                        } catch (e: Exception) {
                            callback.invoke(ResultOrException.error(ErrCode.NET_PARSE_BODY_FAIL, "parse body fail", e))
                        }
                    }
                } else {
                    callback.invoke(ResultOrException.error(ErrCode.NET_INVALID_STATUS_CODE, "invalid status code. $response"))
                }
            }
        })
    }

    class FileParams(
        val relativePath: String,
        val absolutePath: String,
    )
}