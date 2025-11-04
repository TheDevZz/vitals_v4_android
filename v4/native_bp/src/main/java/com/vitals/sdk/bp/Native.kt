package com.vitals.sdk.bp

import com.vitals.sdk.internal.CryptoUtils

object Native {
    init {
        System.loadLibrary("vitals_bp")
    }

    // external fun verifyCredential(timestamp: Long, sign: String): Boolean
    private external fun nativeGetKeyHash(appIdHash: String): String
    private external fun nativeGetKey(appIdHash: String): String

    fun getKeyHash(appId: String): String {
        val appIdHash = CryptoUtils.hashSHA256(appId)
        return nativeGetKeyHash(appIdHash)
    }

    fun getKey(appId: String): String {
        val appIdHash = CryptoUtils.hashSHA256(appId)
        return nativeGetKey(appIdHash)
    }
}