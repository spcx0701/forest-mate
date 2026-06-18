package kr.forestmate.app

import android.content.Context
import kr.forestmate.core.api.ApiConfig

class PhoneStore(context: Context) {
    private val prefs = context.getSharedPreferences("forestmate_phone", Context.MODE_PRIVATE)

    var apiBase: String
        get() = prefs.getString("apiBase", ApiConfig.DEFAULT_BASE_URL) ?: ApiConfig.DEFAULT_BASE_URL
        set(value) = prefs.edit().putString("apiBase", value).apply()

    var deviceToken: String
        get() = prefs.getString("deviceToken", "") ?: ""
        set(value) = prefs.edit().putString("deviceToken", value).apply()

    var activeHikeId: String
        get() = prefs.getString("activeHikeId", "") ?: ""
        set(value) = prefs.edit().putString("activeHikeId", value).apply()
}
