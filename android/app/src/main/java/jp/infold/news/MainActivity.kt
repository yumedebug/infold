package jp.infold.news

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import jp.infold.news.notifications.NotificationHelper
import jp.infold.news.ui.nav.InfoldNavHost
import jp.infold.news.ui.theme.InfoldTheme

// ============================================================
// メインアクティビティ（Jetpack Compose のネイティブ UI）
// 通知タップ / INFOLD リンク → ネイティブの記事詳細画面を開く
// ============================================================

class MainActivity : AppCompatActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            InfoldTheme(darkTheme = darkTheme) {
                InfoldNavHost(viewModel = viewModel)
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 通知タップや INFOLD リンクから記事詳細を開く */
    private fun handleIntent(intent: Intent?) {
        val articleId = intent?.getLongExtra(NotificationHelper.EXTRA_ARTICLE_ID, -1L)
        if (articleId != null && articleId > 0) {
            intent.removeExtra(NotificationHelper.EXTRA_ARTICLE_ID)
            viewModel.openArticle(articleId)
            return
        }

        // https://infold.f5.si/articles/123 のようなリンク
        val data = intent?.data
        if (data != null && data.host == "infold.f5.si") {
            val segments = data.pathSegments
            if (segments.size >= 2 && segments[0] == "articles") {
                segments[1].toLongOrNull()?.let { viewModel.openArticle(it) }
            }
        }
    }
}
