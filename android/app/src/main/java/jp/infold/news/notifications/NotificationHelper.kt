package jp.infold.news.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import jp.infold.news.MainActivity
import jp.infold.news.R

// ============================================================
// 通知の表示
// タップで MainActivity を起動し、article_id を渡して
// ネイティブの記事詳細画面を開く（WebView / Chrome は使わない）
// ============================================================

object NotificationHelper {
    const val CHANNEL_ID = "new_articles"
    const val EXTRA_ARTICLE_ID = "article_id"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, articleId: Long?, body: String) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (articleId != null) putExtra(EXTRA_ARTICLE_ID, articleId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            articleId?.toInt() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(articleId?.toInt() ?: 0, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS が許可されていない場合は通知しない
        }
    }
}
