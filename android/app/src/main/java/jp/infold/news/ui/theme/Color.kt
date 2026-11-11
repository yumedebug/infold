package jp.infold.news.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// INFOLD カラーパレット
// html/index.html の CSS 変数（INFOLD 2.0）と同一の値を使用する。
// ============================================================

// ---- アクセント（Web 版と同一） ----
val NeonCyan = Color(0xFF83C441)
val NeonViolet = Color(0xFF6EAA35)
val NeonPink = Color(0xFFA5D86A)
val NeonGreen = Color(0xFF83C441)

// ---- ダークテーマ ----
val DarkBg = Color(0xFF101114)
val DarkBgSoft = Color(0xFF17191D)
val DarkCard = Color(0xFF202329)
val DarkCardHover = Color(0xFF252930)
val DarkText = Color(0xFFF5F5F5)
val DarkMuted = Color(0xFFA5A8AE)
val DarkFaint = Color(0xFF7C8188)
val DarkBorder = Color(0xFF34373D)
val DarkPrimary = Color(0xFF83C441)
val DarkPrimary2 = Color(0xFF5E952A)
val DarkAccent = Color(0xFF83C441)
val DarkOk = Color(0xFF8FD14F)
val DarkWarn = Color(0xFFE2B04A)
val DarkHeader = Color(0xFF101114)
val DarkCardBorder = Color(0xFF34373D)
val DarkOnPrimary = Color(0xFF101114)

// ---- ライトテーマ ----
val LightBg = Color(0xFFFFFFFF)
val LightBgSoft = Color(0xFFF4F5F2)
val LightCard = Color(0xFFFFFFFF)
val LightCardHover = Color(0xFFF8F9F6)
val LightText = Color(0xFF17191B)
val LightMuted = Color(0xFF666B70)
val LightFaint = Color(0xFF8A9089)
val LightBorder = Color(0xFFE1E4DF)
val LightPrimary = Color(0xFF6EAA35)
val LightPrimary2 = Color(0xFF4F7D24)
val LightAccent = Color(0xFF6EAA35)
val LightOk = Color(0xFF4F8A2A)
val LightWarn = Color(0xFFA56806)
val LightHeader = Color(0xFFFFFFFF)
val LightCardBorder = Color(0xFFE1E4DF)
val LightOnPrimary = Color(0xFFFFFFFF)

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
    val onPrimary: Color,
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
    onPrimary = LightOnPrimary,
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
    onPrimary = DarkOnPrimary,
    isDark = true,
)
