package jp.infold.news.data

import android.content.Context

// ============================================================
// アプリ設定（SharedPreferences）
// テーマ・言語・プッシュトークン登録状態などを保持する
// ============================================================

object Prefs {
    private const val NAME = "infold_prefs"

    private const val KEY_THEME = "theme" // dark | light | system
    private const val KEY_LANGUAGE = "language" // ja | en
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_FCM_REGISTERED = "fcm_registered"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---- theme ----
    fun getTheme(context: Context): String =
        sp(context).getString(KEY_THEME, "dark") ?: "dark"

    fun setTheme(context: Context, value: String) {
        sp(context).edit().putString(KEY_THEME, value).apply()
    }

    // ---- language ----
    fun getLanguage(context: Context): String =
        sp(context).getString(KEY_LANGUAGE, "ja") ?: "ja"

    fun setLanguage(context: Context, value: String) {
        sp(context).edit().putString(KEY_LANGUAGE, value).apply()
    }

    // ---- FCM token ----
    fun getFcmToken(context: Context): String? =
        sp(context).getString(KEY_FCM_TOKEN, null)

    fun setFcmToken(context: Context, token: String?) {
        sp(context).edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun isFcmRegistered(context: Context): Boolean =
        sp(context).getBoolean(KEY_FCM_REGISTERED, false)

    fun setFcmRegistered(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_FCM_REGISTERED, value).apply()
    }

    // ---- notifications toggle ----
    fun getNotificationsEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_NOTIFICATIONS, true)

    fun setNotificationsEnabled(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
    }
}
