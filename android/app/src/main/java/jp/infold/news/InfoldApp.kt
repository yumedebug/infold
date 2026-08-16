package jp.infold.news

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import jp.infold.news.data.ApiClient
import jp.infold.news.data.Prefs
import kotlinx.coroutines.runBlocking

// ============================================================
// INFOLD アプリケーション
// API クライアント初期化・保存済み言語の適用・FCM トークン登録
// ============================================================

class InfoldApp : Application() {

    override fun onCreate() {
        super.onCreate()

        ApiClient.init(this)

        // 画像ローダー（SVG サムネイル対応）
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components { add(SvgDecoder.Factory()) }
                .crossfade(true)
                .build()
        )

        // 保存済みの言語を適用（日本語 / English）
        val lang = Prefs.getLanguage(this)
        if (lang == "en") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }

        // FCM: google-services.json が設定されていればトークンを登録する
        if (Prefs.getNotificationsEnabled(this)) {
            try {
                if (FirebaseApp.getApps(this).isNotEmpty()) {
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            if (token != null) {
                                Thread {
                                    try {
                                        runBlocking { ApiClient.registerPushToken(token) }
                                        Prefs.setFcmToken(this, token)
                                        Prefs.setFcmRegistered(this, true)
                                    } catch (_: Exception) {
                                        // 次回起動時に再試行
                                    }
                                }.start()
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Firebase 未設定などは無視
            }
        }
    }
}
