package jp.infold.news.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// INFOLD Liquid Glass カラーパレット
// Web 版（html/index.html の CSS 変数）と同一のデザイントークン
// ============================================================

// ネオンアクセント
val NeonCyan = Color(0xFF00E0FF)
val NeonViolet = Color(0xFF8B5CFF)
val NeonPink = Color(0xFFFF3D9A)
val NeonGreen = Color(0xFF3DFFB8)

// ---- Light テーマ ----
val LightBackground = Color(0xFFF4F8FF)
val LightBackgroundSoft = Color(0xFFE9F0FD)
val LightCard = Color(0x99FFFFFF) // rgba(255,255,255,.6)
val LightCardHover = Color(0xC7FFFFFF) // rgba(255,255,255,.78)
val LightText = Color(0xFF101828)
val LightMuted = Color(0xFF56648A)
val LightFaint = Color(0xFF8B97B8)
val LightBorder = Color(0xFFDBE6F7)
val LightPrimary = Color(0xFF0D8BFF)
val LightPrimary2 = Color(0xFF6A4DFF)
val LightAccent = Color(0xFFFF3D9A)
val LightOk = Color(0xFF0A9E6D)
val LightWarn = Color(0xFFD97706)
val LightGlass = Color(0x8CFFFFFF) // rgba(255,255,255,.55)
val LightGlassBorder = Color(0x1A101828) // rgba(16,24,40,.1)
val LightGlassHi = Color(0x6BFFFFFF) // rgba(255,255,255,.42)

// ---- Dark テーマ ----
val DarkBackground = Color(0xFF05070F)
val DarkBackgroundSoft = Color(0xFF0C1122)
val DarkCard = Color(0x800E1426) // rgba(14,20,38,.5)
val DarkCardHover = Color(0xB8151E38) // rgba(21,30,56,.72)
val DarkText = Color(0xFFE6ECFF)
val DarkMuted = Color(0xFF93A0C4)
val DarkFaint = Color(0xFF6B7694)
val DarkBorder = Color(0xFF1C2740)
val DarkPrimary = Color(0xFF00C8FF)
val DarkPrimary2 = Color(0xFF8B5CFF)
val DarkAccent = Color(0xFFFF3D9A)
val DarkOk = Color(0xFF3DFFB8)
val DarkWarn = Color(0xFFFFB020)
val DarkGlass = Color(0x800A0F1E) // rgba(10,15,30,.5)
val DarkGlassBorder = Color(0x1FFFFFFF) // rgba(255,255,255,.12)
val DarkGlassHi = Color(0x17FFFFFF) // rgba(255,255,255,.09)

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
    val glassBorder: Color,
    val glassHighlight: Color,
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
    glassBorder = LightGlassBorder,
    glassHighlight = LightGlassHi,
    headerBackground = LightGlass,
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
    glassBorder = DarkGlassBorder,
    glassHighlight = DarkGlassHi,
    headerBackground = DarkGlass,
)
