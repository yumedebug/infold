package jp.infold.news.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

// ============================================================
// INFOLD API クライアント
// 認証は Web 版と同じ HttpOnly クッキー（infold_reader_session）。
// ネイティブアプリからは OkHttp の CookieJar で保持・送信する。
// CSRF 対策のため全リクエストに X-Requested-With ヘッダーを付ける。
// ============================================================

const val BASE_URL = "https://infold.f5.si"

class ApiException(val code: String, override val message: String) : Exception(message)

/** クッキーを SharedPreferences に永続化する CookieJar */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("infold_cookies", Context.MODE_PRIVATE)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val map = loadAll().toMutableMap()
        for (cookie in cookies) {
            map[cookie.name] = cookie.toString()
        }
        prefs.edit().putStringSet("cookies", map.values.toSet()).apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        val map = loadAll().toMutableMap()
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cookie = Cookie.parse(url, entry.value) ?: continue
            if (cookie.expiresAt < now) {
                iterator.remove()
            } else {
                result.add(cookie)
            }
        }
        prefs.edit().putStringSet("cookies", map.values.toSet()).apply()
        return result
    }

    fun clear() {
        prefs.edit().remove("cookies").apply()
    }

    private fun loadAll(): Map<String, String> {
        val set = prefs.getStringSet("cookies", emptySet()) ?: emptySet()
        return set.associateBy { it.substringBefore('=') }
    }
}

object ApiClient {
    private lateinit var cookieJar: PersistentCookieJar

    val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) {
        if (::cookieJar.isInitialized) return
        cookieJar = PersistentCookieJar(context.applicationContext)
    }

    fun clearCookies() {
        if (::cookieJar.isInitialized) cookieJar.clear()
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private fun url(path: String): HttpUrl = BASE_URL.toHttpUrl().resolve(path)!!

    private suspend fun <T> request(
        path: String,
        method: String = "GET",
        bodyJson: String? = null,
        decode: (String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url(path))
        if (bodyJson != null) {
            builder.method(method, bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
        } else {
            builder.method(method, null)
        }
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val err = try {
                    json.decodeFromString<ApiErrorBody>(text)
                } catch (_: Exception) {
                    ApiErrorBody(error = "http_${resp.code}")
                }
                throw ApiException(err.error, err.message ?: err.error)
            }
            decode(text)
        }
    }

    private inline fun <reified T> decode(text: String): T =
        json.decodeFromString<T>(text)

    // --------------------------------------------------------
    // Public API
    // --------------------------------------------------------

    suspend fun listArticles(
        page: Int = 1,
        limit: Int = 12,
        category: String? = null,
        featured: Boolean? = null,
        exclude: Long? = null,
    ): ArticleListResponse {
        val sb = StringBuilder("/api/articles?page=$page&limit=$limit")
        if (!category.isNullOrBlank()) sb.append("&category=").append(category)
        if (featured != null) sb.append("&featured=").append(if (featured) "1" else "0")
        if (exclude != null) sb.append("&exclude=").append(exclude)
        return request(sb.toString()) { decode(it) }
    }

    suspend fun getArticle(id: Long): ArticleDetailResponse =
        request("/api/articles/$id") { decode(it) }

    suspend fun categories(): CategoriesResponse =
        request("/api/categories") { decode(it) }

    suspend fun search(q: String): ArticleListResponse =
        request("/api/search?q=${java.net.URLEncoder.encode(q, "UTF-8")}") { decode(it) }

    // --------------------------------------------------------
    // Reader auth
    // --------------------------------------------------------

    suspend fun login(email: String, password: String): AuthResponse =
        request("/api/auth/login", "POST", json.encodeToString(AuthRequest.serializer(), AuthRequest(email = email, password = password))) { decode(it) }

    suspend fun register(email: String, password: String, name: String): AuthResponse =
        request("/api/auth/register", "POST", json.encodeToString(RegisterRequest.serializer(), RegisterRequest(email = email, password = password, name = name))) { decode(it) }

    suspend fun logout(): OkResponse =
        request("/api/auth/logout", "POST") { decode(it) }

    suspend fun me(): MeResponse =
        request("/api/auth/me") { decode(it) }

    // --------------------------------------------------------
    // Points
    // --------------------------------------------------------

    suspend fun points(): PointsSummary =
        request("/api/points") { decode(it) }

    suspend fun pointsHistory(): PointsHistoryResponse =
        request("/api/points/history?limit=50") { decode(it) }

    suspend fun completeArticle(id: Long): CompleteResponse =
        request("/api/articles/$id/complete", "POST", "{}") { decode(it) }

    suspend fun spendAdFree(planPoints: Long): SpendAdFreeResponse =
        request("/api/points/ad-free", "POST", json.encodeToString(SpendAdFreeRequest.serializer(), SpendAdFreeRequest(plan = planPoints))) { decode(it) }

    // --------------------------------------------------------
    // Push registration
    // --------------------------------------------------------

    suspend fun registerPushToken(token: String): OkResponse =
        request("/api/push/register", "POST", json.encodeToString(PushRegisterRequest.serializer(), PushRegisterRequest(token = token, platform = "android"))) { decode(it) }

    suspend fun unregisterPushToken(token: String) {
        try {
            request("/api/push/unregister", "POST", json.encodeToString(PushRegisterRequest.serializer(), PushRegisterRequest(token = token, platform = "android"))) { decode<OkResponse>(it) }
        } catch (_: Exception) {
            // ベストエフォート
        }
    }

    /** 画像URLを解決する（相対パス → 絶対URL、data URI → ByteArray） */
    fun resolveImage(raw: String?): Any? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("data:image/svg+xml;base64,") -> {
                try {
                    android.util.Base64.decode(
                        trimmed.substringAfter(","),
                        android.util.Base64.DEFAULT
                    )
                } catch (_: Exception) {
                    null
                }
            }
            trimmed.startsWith("data:") -> null
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("/") -> BASE_URL + trimmed
            else -> BASE_URL + "/" + trimmed
        }
    }

    fun isOfflineException(e: Throwable): Boolean =
        e is IOException || (e is ApiException && e.code.startsWith("http_"))
}

@kotlinx.serialization.Serializable
data class AuthRequest(val email: String, val password: String)

@kotlinx.serialization.Serializable
data class RegisterRequest(val email: String, val password: String, val name: String)

@kotlinx.serialization.Serializable
data class SpendAdFreeRequest(val plan: Long)

@kotlinx.serialization.Serializable
data class PushRegisterRequest(val token: String, val platform: String = "android")
