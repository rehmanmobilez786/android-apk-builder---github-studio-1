package com.example.security

import android.content.Context
import android.content.SharedPreferences

class SecurityManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("notebook_security_prefs", Context.MODE_PRIVATE)

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean("biometric_enabled", false)
        set(value) = prefs.edit().putBoolean("biometric_enabled", value).apply()

    var isPinEnabled: Boolean
        get() = prefs.getBoolean("pin_enabled", false)
        set(value) = prefs.edit().putBoolean("pin_enabled", value).apply()

    var pinCode: String
        get() = prefs.getString("pin_code", "1234") ?: "1234"
        set(value) = prefs.edit().putString("pin_code", value).apply()

    var isLocked: Boolean
        get() = isBiometricEnabled || isPinEnabled
        private set(_) {}

    fun verifyPin(inputPin: String): Boolean {
        return inputPin == pinCode
    }
}
