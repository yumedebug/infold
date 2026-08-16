package jp.infold.news.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// INFOLD カラーパレット（Android アプリ版）
// ブランドカラー（シアン/マゼンタ）とダーク/ライトは Web 版と同系色を
// 使いつつ、Android ではリキッドグラス（半透明・ガラス風）を使わない
// ソリッドな配色にする。Web 版の CSS は変更しない。
// ============================================================

// ネオンアクセント
val NeonCyan = Color(0xFF00E0FF)
val NeonViolet = Color(0xFF8B5CFF)
val NeonPink = Color(0xFFFF3D9A)
val NeonGreen = Color(0xFF3DFFB8)

// ---- Light テーマ（ソリッド） ----
val LightBackground = Color(0xFFF4F8FF)
val LightBackgroundSoft = Color(0xFFE9F0FD)
val LightCard = Color(0xFFFFFFFF)
val LightCardHover = Color(0xFFF2F7FF)
val LightText = Color(0xFF101828)
val LightMuted = Color(0xFF56648A)
val LightFaint = Color(0xFF8B97B8)
val LightBorder = Color(0xFFDBE6F7)
val LightPrimary = Color(0xFF0D8BFF)
val LightPrimary2 = Color(0xFF6A4DFF)
val LightAccent = Color(0xFFFF3D9A)
val LightOk = Color(0xFF0A9E6D)
val LightWarn = Color(0xFFD97706)
val LightHeader = Color(0xFFFFFFFF)
val LightCardBorder = Color(0xFFD9E4F6)

// ---- Dark テーマ（ソリッド） ----
val DarkBackground = Color(0xFF05070F)
val DarkBackgroundSoft = Color(0xFF0C1122)
val DarkCard = Color(0xFF121A2C)
val DarkCardHover = Color(0xFF1A2438)
val DarkText = Color(0xFFE6ECFF)
val DarkMuted = Color(0xFF93A0C4)
val DarkFaint = Color(0xFF6B7694)
val DarkBorder = Color(0xFF1C2740)
val DarkPrimary = Color(0xFF00C8FF)
val DarkPrimary2 = Color(0xFF8B5CFF)
val DarkAccent = Color(0xFFFF3D9A)
val DarkOk = Color(0xFF3DFFB8)
val DarkWarn = Color(0xFFFFB020)
val DarkHeader = Color(0xFF0B101C)
val DarkCardBorder = Color(0xFF22304D)

// ---- カテゴリカラー（Web 版のバッジ色と同一） ----
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

/** カテゴリスラッグ → カラー */
fun categoryColor(slug: String): Color = CategoryColors[slug] ?: CategoryColors.getValue("other")

/** カテゴリスラッグ → バッジ背景色（少し透明） */
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
)

val LightInfoldColors = InfoldColors(
    background = LightBackground,
    backgroundSoft = LightBackgroundSoft,
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
)

val DarkInfoldColors = InfoldColors(
    background = DarkBackground,
    backgroundSoft = DarkBackgroundSoft,
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
)
