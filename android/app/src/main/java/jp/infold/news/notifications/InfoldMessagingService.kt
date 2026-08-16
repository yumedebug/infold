package jp.infold.news.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import jp.infold.news.data.ApiClient
import jp.infold.news.data.Prefs
import kotlinx.coroutines.runBlocking

// ============================================================
// FCM プッシュ通知の受信
// 通知タップ → MainActivity（article_id 付き）→ ネイティブの記事詳細へ
// ============================================================

class InfoldMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (!Prefs.getNotificationsEnabled(this)) return
        // バックグラウンドでトークンをバックエンドに登録
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

    override fun onMessageReceived(message: RemoteMessage) {
        val articleId = message.data["article_id"]?.toLongOrNull()
        val body = message.notification?.body
            ?: message.data["body"]
            ?: getString(jp.infold.news.R.string.notification_body)
        NotificationHelper.showNotification(this, articleId, body)
    }
}
