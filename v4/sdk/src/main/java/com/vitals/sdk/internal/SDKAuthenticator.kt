package com.vitals.sdk.internal

import android.content.Context

class SDKAuthenticator(context: Context, val appId: String, appSecret: String) {
    val secretHashKey = CryptoUtils.hashSHA256(appId + appSecret)
    var isAuthPass: Boolean = false
        private set

    private val allowSigns = listOf(
        "338eb78dea596cfab40e3a027ce1e7e1df6c1f23b1e4a564e92fc79fccaf3896", // com.vitals.example.app
        "cd703b5e5ac6696b2ea0554cc54efcef93590129b1fa80a842b5bb87fcc35d79", // com.vitals.example
        "7ddf92400a60c43acf07ad6e9c02a3f21ac50b5b712c159591b0df8942a97972", // com.transsion.aivoiceassistant
    )

    init {
        val packageName = context.applicationInfo.packageName
        isAuthPass = authenticate(secretHashKey, packageName)
    }

    private fun authenticate(secretHashKey: String, packageName: String): Boolean {
        val sign = CryptoUtils.hashSHA256(secretHashKey + packageName)
//        android.util.Log.d("SDKAuthenticator", "Authenticating with sign: $sign")
        return allowSigns.contains(sign)
    }
}