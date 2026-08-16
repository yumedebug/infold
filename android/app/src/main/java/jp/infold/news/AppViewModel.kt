package jp.infold.news

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.infold.news.data.ApiClient
import jp.infold.news.data.AuthResponse
import jp.infold.news.data.Category
import jp.infold.news.data.Prefs
import jp.infold.news.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================
// アプリ全体で共有する状態（認証・テーマ・言語・ナビゲーション）
// ============================================================

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val user: User) : AuthState()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _themeMode = MutableStateFlow(Prefs.getTheme(application))
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(Prefs.getLanguage(application))
    val language: StateFlow<String> = _language.asStateFlow()

    // 通知タップなどの 1 回限りのイベント（チャネルで確実に配送）
    private val _navigateToArticle = Channel<Long>(Channel.BUFFERED)
    val navigateToArticle = _navigateToArticle.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                val me = withContext(Dispatchers.IO) { ApiClient.me() }
                _authState.value = if (me.user != null) AuthState.LoggedIn(me.user) else AuthState.LoggedOut
            } catch (_: Exception) {
                _authState.value = AuthState.LoggedOut
            }
        }
        refreshCategories()
    }

    // ---- auth ----

    fun onLogin(response: AuthResponse) {
        if (response.ok && response.user != null) {
            _authState.value = AuthState.LoggedIn(response.user)
        }
    }

    fun onLogout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    ApiClient.logout()
                } catch (_: Exception) {
                }
                ApiClient.clearCookies()
            }
            _authState.value = AuthState.LoggedOut
        }
    }

    // ---- categories ----

    fun refreshCategories() {
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { ApiClient.categories() }
                _categories.value = res.categories
            } catch (_: Exception) {
                // 失敗しても無視（再表示時に再取得）
            }
        }
    }

    // ---- theme / language ----

    fun setTheme(mode: String) {
        Prefs.setTheme(getApplication(), mode)
        _themeMode.value = mode
    }

    fun setLanguage(lang: String) {
        Prefs.setLanguage(getApplication(), lang)
        _language.value = lang
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(lang)
        )
    }

    /** 通知タップや INFOLD リンクから記事詳細を開く */
    fun openArticle(articleId: Long) {
        _navigateToArticle.trySend(articleId)
    }

    /** 端末の FCM トークンをバックエンドに登録する */
    fun registerPushTokenIfNeeded(token: String) {
        val ctx = getApplication<Application>()
        if (Prefs.getFcmToken(ctx) == token && Prefs.isFcmRegistered(ctx)) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { ApiClient.registerPushToken(token) }
                Prefs.setFcmToken(ctx, token)
                Prefs.setFcmRegistered(ctx, true)
            } catch (_: Exception) {
                // 次回起動時に再試行
            }
        }
    }

    fun unregisterPushToken(token: String) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ApiClient.unregisterPushToken(token) }
            Prefs.setFcmToken(ctx, null)
            Prefs.setFcmRegistered(ctx, false)
        }
    }
}

/** FCM が利用可能な端末か（google-services.json 未設定なら false） */
fun isFirebaseAvailable(context: Context): Boolean {
    return try {
        com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()
    } catch (_: Throwable) {
        false
    }
}
