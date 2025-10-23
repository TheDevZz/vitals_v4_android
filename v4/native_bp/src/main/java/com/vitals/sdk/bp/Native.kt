package com.vitals.sdk.bp

object Native {
    init {
        System.loadLibrary("vitals_bp")
    }
    external fun verifyCredential(timestamp: Long, sign: String): Boolean
}