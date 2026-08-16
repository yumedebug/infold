package jp.infold.news.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import jp.infold.news.data.Category
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ISO8601 → 「2026年8月16日 10:30」/「Aug 16, 2026 10:30」 */
fun formatPublishedAt(iso: String?, lang: String): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val zdt = ZonedDateTime.ofInstant(Instant.parse(iso), ZoneId.of("Asia/Tokyo"))
        val formatter = if (lang == "ja") {
            DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
        } else {
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.ENGLISH)
        }
        zdt.format(formatter)
    } catch (_: Exception) {
        iso
    }
}

/** 残り秒数 → 「6時間30分」 */
fun formatRemaining(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}時間${m}分"
        h > 0 -> "${h}時間"
        else -> "${m}分"
    }
}

/** カテゴリスラッグ → 表示名（言語対応） */
fun categoryDisplayName(slug: String, categories: List<Category>, lang: String): String {
    val c = categories.firstOrNull { it.slug == slug }
    if (c != null) {
        if (lang == "en" && c.nameEn.isNotBlank()) return c.nameEn
        return c.name
    }
    val fallback = mapOf(
        "it" to "IT", "ai" to "AI", "windows" to "Windows", "android" to "Android",
        "apple" to "Apple", "web" to "Web", "programming" to "Programming",
    )
    return if (lang == "en") fallback[slug] ?: "Other" else fallback[slug] ?: "その他"
}

/** 外部ブラウザでリンクを開く（INFOLD 自身の画面には使わない） */
fun openExternalLink(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        // 端末にブラウザがない場合などは無視
    }
}
