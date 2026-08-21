package jp.infold.news.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// INFOLD カラーパレット
// 画面の可読性を保ちつつ、Liquid Glass サーフェスは各コンポーネント側で
// 背景を透過して重ねる。下部ナビゲーションでは RenderEffect も使用する。
// ============================================================

// ---- ネオンアクセント ----
val NeonCyan = Color(0xFF00E0FF)
val NeonViolet = Color(0xFF8B5CFF)
val NeonPink = Color(0xFFFF3D9A)

// ---- ダークテーマ ----
val DarkBg = Color(0xFF0F1118)
val DarkBgSoft = Color(0xFF1A1D2B)
val DarkCard = Color(0xFF1E2235)
val DarkCardHover = Color(0xFF262A3F)
val DarkText = Color(0xFFE8ECFF)
val DarkMuted = Color(0xFF8B95B8)
val DarkFaint = Color(0xFF5A6585)
val DarkBorder = Color(0xFF2A2E42)
val DarkPrimary = Color(0xFF00C8FF)
val DarkPrimary2 = Color(0xFF8B5CFF)
val DarkAccent = Color(0xFFFF3D9A)
val DarkOk = Color(0xFF3DFFB8)
val DarkWarn = Color(0xFFFFB020)
val DarkHeader = Color(0xFF161927)
val DarkCardBorder = Color(0xFF2A2E42)

// ---- ライトテーマ ----
val LightBg = Color(0xFFF3F4F8)
val LightBgSoft = Color(0xFFE8EAF0)
val LightCard = Color(0xFFFFFFFF)
val LightCardHover = Color(0xFFF0F1F5)
val LightText = Color(0xFF1A1D2B)
val LightMuted = Color(0xFF566488)
val LightFaint = Color(0xFF8B97B8)
val LightBorder = Color(0xFFDDE0E8)
val LightPrimary = Color(0xFF0D8BFF)
val LightPrimary2 = Color(0xFF6A4DFF)
val LightAccent = Color(0xFFFF3D9A)
val LightOk = Color(0xFF0A9E6D)
val LightWarn = Color(0xFFD97706)
val LightHeader = Color(0xFFFFFFFF)
val LightCardBorder = Color(0xFFDDE0E8)

// ---- カテゴリカラー（Web 版と同一） ----
val CategoryColors = mapOf(
    "it" to Color(0xFF369EF6),
    "ai" to Color(0xFF7C3AED),
    "windows" to Color(0xFF0284C7),
    "android" to Color(0xFF16A34A),
    "apple" to Color(0xFF64748B),
    "web" to Color(0xFFDB2777),
    "programming" to Color(0xFFEA580C),
    "other" to Color(0xFF475569),
)

fun categoryColor(slug: String): Color = CategoryColors[slug] ?: CategoryColors.getValue("other")
fun categorySoftColor(slug: String): Color = categoryColor(slug).copy(alpha = 0.14f)

// ---- テーマ別のカラースキーム ----
data class InfoldColors(
    val background: Color,
    val backgroundSoft: Color,
    val surface: Color,
    val surfaceHover: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textFaint: Color,
    val border: Color,
    val primary: Color,
    val primary2: Color,
    val accent: Color,
    val ok: Color,
    val warn: Color,
    val cardBorder: Color,
    val headerBackground: Color,
    val isDark: Boolean,
)

val LightInfoldColors = InfoldColors(
    background = LightBg,
    backgroundSoft = LightBgSoft,
    surface = LightCard,
    surfaceHover = LightCardHover,
    textPrimary = LightText,
    textSecondary = LightMuted,
    textFaint = LightFaint,
    border = LightBorder,
    primary = LightPrimary,
    primary2 = LightPrimary2,
    accent = LightAccent,
    ok = LightOk,
    warn = LightWarn,
    cardBorder = LightCardBorder,
    headerBackground = LightHeader,
    isDark = false,
)

val DarkInfoldColors = InfoldColors(
    background = DarkBg,
    backgroundSoft = DarkBgSoft,
    surface = DarkCard,
    surfaceHover = DarkCardHover,
    textPrimary = DarkText,
    textSecondary = DarkMuted,
    textFaint = DarkFaint,
    border = DarkBorder,
    primary = DarkPrimary,
    primary2 = DarkPrimary2,
    accent = DarkAccent,
    ok = DarkOk,
    warn = DarkWarn,
    cardBorder = DarkCardBorder,
    headerBackground = DarkHeader,
    isDark = true,
)
