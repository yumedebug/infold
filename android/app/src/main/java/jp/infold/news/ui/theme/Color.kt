package jp.infold.news.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// INFOLD カラーパレット — Liquid Glass デザイン
// 半透明サーフェス + 背景ぼかし + ガラスの質感を表現する色
// Web 版の Liquid Glass CSS とは独立した Android ネイティブ実装
// ============================================================

// ---- ネオンアクセント ----
val NeonCyan = Color(0xFF00E0FF)
val NeonViolet = Color(0xFF8B5CFF)
val NeonPink = Color(0xFFFF3D9A)

// ---- ダークテーマ（Liquid Glass） ----
// 背景はほぼ黒、サーフェスは薄暗い半透明（ガラス板）
val DarkBg = Color(0xFF03060D)
val DarkBgSoft = Color(0xFF080E1A)
val DarkGlass = Color(0x1AFFFFFF)          // 10% 白 → 暗いガラス
val DarkGlassSolid = Color(0xFF0F1525)     // 実際の描画用（.alpha で透明度を制御）
val DarkGlassBorder = Color(0x33FFFFFF)    // 20% 白 → ガラスの境界線
val DarkGlassHighlight = Color(0x14FFFFFF) // 8% 白 → ガラスのハイライト
val DarkCard = Color(0x14FFFFFF)           // カード背景
val DarkCardHover = Color(0x1EFFFFFF)
val DarkText = Color(0xFFE8ECFF)
val DarkMuted = Color(0xFF8B95B8)
val DarkFaint = Color(0xFF5A6585)
val DarkBorder = Color(0x22FFFFFF)
val DarkPrimary = Color(0xFF00C8FF)
val DarkPrimary2 = Color(0xFF8B5CFF)
val DarkAccent = Color(0xFFFF3D9A)
val DarkOk = Color(0xFF3DFFB8)
val DarkWarn = Color(0xFFFFB020)
val DarkHeader = Color(0x0DFFFFFF) // ヘッダーはほぼ透明なガラス
val DarkCardBorder = Color(0x1AFFFFFF)

// ---- ライトテーマ（Liquid Glass） ----
// 背景は淡いブルーグレー、サーフェスは明るい半透明（ガラス板）
val LightBg = Color(0xFFF0F4FA)
val LightBgSoft = Color(0xFFE4EBF7)
val LightGlass = Color(0x33FFFFFF)          // 20% 白 → 明るいガラス
val LightGlassSolid = Color(0xFFF8FAFF)    // 実際の描画用
val LightGlassBorder = Color(0x4DFFFFFF)   // 30% 白 → ガラスの境界線
val LightGlassHighlight = Color(0x66FFFFFF) // 40% 白 → ガラスのハイライト
val LightCard = Color(0x40FFFFFF)           // カード背景
val LightCardHover = Color(0x59FFFFFF)
val LightText = Color(0xFF0F1729)
val LightMuted = Color(0xFF566488)
val LightFaint = Color(0xFF8B97B8)
val LightBorder = Color(0x33FFFFFF)
val LightPrimary = Color(0xFF0D8BFF)
val LightPrimary2 = Color(0xFF6A4DFF)
val LightAccent = Color(0xFFFF3D9A)
val LightOk = Color(0xFF0A9E6D)
val LightWarn = Color(0xFFD97706)
val LightHeader = Color(0x4DFFFFFF) // ヘッダーは半透明ガラス
val LightCardBorder = Color(0x33FFFFFF)

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
    val surface: Color,          // ガラスカード背景
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
    // Liquid Glass 専用
    val glassHighlight: Color,
    val glassBorder: Color,
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
    glassHighlight = LightGlassHighlight,
    glassBorder = LightGlassBorder,
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
    glassHighlight = DarkGlassHighlight,
    glassBorder = DarkGlassBorder,
    isDark = true,
)
