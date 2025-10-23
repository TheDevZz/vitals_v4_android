package com.vitals.sdk.framework

import android.content.SharedPreferences
import java.util.UUID
import androidx.core.content.edit

class IdentityManager(val sp: SharedPreferences) {
    private val SP_KEY_UUID = "uuid"

    fun getUUID(): String {
        synchronized(IdentityManager::class.java) {
            var uuid = sp.getString(SP_KEY_UUID, null)
            if (uuid == null) {
                uuid = genUUID()
                sp.edit { putString(SP_KEY_UUID, uuid) }
            }
            return uuid
        }
    }

    private fun genUUID(): String {
        return UUID.randomUUID().toString()
    }
}