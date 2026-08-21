package jp.infold.news.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import jp.infold.news.data.ApiClient
import jp.infold.news.data.Article
import jp.infold.news.data.BASE_URL
import jp.infold.news.ui.theme.LocalInfoldColors
import jp.infold.news.ui.theme.categoryColor
import jp.infold.news.ui.theme.categorySoftColor
import jp.infold.news.util.categoryDisplayName
import jp.infold.news.util.formatPublishedAt
import kotlinx.coroutines.launch

// ============================================================
// INFOLD 共通 UI コンポーネント — Liquid Glass UI
// ============================================================

/** Web 版のロゴマーク（グラデーションの角丸四角 + 白いドキュメント） */
@Composable
fun BrandLogo(size: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val px = with(density) { size.toPx() }
    Canvas(modifier = modifier.size(size)) {
        val brush = Brush.linearGradient(listOf(Color(0xFF0D8BFF), Color(0xFF6A4DFF)))
        drawRoundRect(
            brush = brush,
            topLeft = Offset(px * 0.04f, px * 0.04f),
            size = Size(px * 0.92f, px * 0.92f),
            cornerRadius = CornerRadius(px * 0.24f, px * 0.24f),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(px * 0.27f, px * 0.36f),
            size = Size(px * 0.21f, px * 0.30f),
            cornerRadius = CornerRadius(px * 0.06f, px * 0.06f),
        )
        drawCircle(
            color = Color.White,
            radius = px * 0.075f,
            center = Offset(px * 0.375f, px * 0.22f),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.55f),
            topLeft = Offset(px * 0.56f, px * 0.36f),
            size = Size(px * 0.21f, px * 0.30f),
            cornerRadius = CornerRadius(px * 0.06f, px * 0.06f),
        )
    }
}

/** INFOLD ワードマーク（グラデーションテキスト） */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier) {
    val colors = LocalInfoldColors.current
    Text(
        text = "INFOLD",
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 4.sp,
            brush = Brush.horizontalGradient(listOf(colors.textPrimary, colors.primary)),
        ),
    )
}

/** ソリッドカード（不透明背景 + ボーダー） */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalInfoldColors.current
    val shape = RoundedCornerShape(14.dp)
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.cardBorder, shape)
            .then(clickModifier),
        content = content,
    )
}

/** カテゴリバッジ */
@Composable
fun CategoryBadge(slug: String, name: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(categorySoftColor(slug))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = categoryColor(slug),
        )
    }
}

/** カテゴリフィルターピル */
@Composable
fun CategoryChip(
    slug: String?,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalInfoldColors.current
    val color = if (slug != null) categoryColor(slug) else colors.primary
    val bg = if (selected) color else colors.surface
    val fg = if (selected) Color.White else color
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) color else colors.cardBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
        )
    }
}

/** 記事一覧用の横長カード */
@Composable
fun ArticleRowCard(
    article: Article,
    lang: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalInfoldColors.current
    GlassCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(12.dp)) {
            ArticleThumb(
                raw = article.thumbnail,
                category = article.category,
                modifier = Modifier.size(width = 120.dp, height = 100.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (article.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = article.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryBadge(article.category, categoryDisplayName(article.category, emptyList(), lang))
                    if (article.featured) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatPublishedAt(article.publishedAt, lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textFaint,
                    )
                }
            }
        }
    }
}

/** サムネイル（無い場合はカテゴリ色のグラデーション + ロゴ） */
@Composable
fun ArticleThumb(raw: String?, category: String = "other", modifier: Modifier = Modifier) {
    val colors = LocalInfoldColors.current
    val resolved = ApiClient.resolveImage(raw)
    val shape = RoundedCornerShape(10.dp)
    if (resolved != null) {
        AsyncImage(
            model = resolved,
            contentDescription = null,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        val c = categoryColor(category)
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(c.copy(alpha = 0.85f), colors.backgroundSoft)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            BrandLogo(size = 30.dp)
        }
    }
}

/** ホームの注目記事（ヒーロー）カード */
@Composable
fun HeroCard(
    article: Article,
    lang: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val resolved = ApiClient.resolveImage(article.thumbnail)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(shape)
            .background(Color(0xFF1A1D2B))
            .clickable(onClick = onClick),
    ) {
        if (resolved != null) {
            AsyncImage(
                model = resolved,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Black.copy(alpha = 0.82f),
                    )
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryBadge(article.category, article.category)
                if (article.featured) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(14.dp))
                }
            }
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatPublishedAt(article.publishedAt, lang),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/** セクション見出し（アクセントバー付き） */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalInfoldColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(colors.primary, colors.accent))),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/** 読み込み中表示 */
@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    val colors = LocalInfoldColors.current
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = colors.primary)
    }
}

/** エラー表示 */
@Composable
fun ErrorView(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    debug: String? = null,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "INFOLD",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp,
                brush = Brush.horizontalGradient(listOf(colors.primary, colors.accent)),
            ),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onRetry != null) {
            Text(
                text = "↻",
                style = MaterialTheme.typography.titleLarge,
                color = colors.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
        val scope = rememberCoroutineScope()
        var testing by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<String?>(null) }
        Text(
            text = if (testing) "接続テスト中…" else "接続テスト",
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    if (!testing) {
                        testing = true
                        testResult = null
                        scope.launch {
                            testResult = ApiClient.rawConnectivityTest()
                            testing = false
                        }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            text = "INFOLD v$version\nAPI: $BASE_URL\n${debug ?: ""}" +
                (testResult?.let { "\n\n[接続テスト]\n$it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textFaint,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 空表示 */
@Composable
fun EmptyView(message: String, modifier: Modifier = Modifier) {
    val colors = LocalInfoldColors.current
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = colors.textFaint)
    }
}

/** サブ画面用のトップバー */
@Composable
fun SubTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalInfoldColors.current
    Column(modifier = modifier.fillMaxWidth().background(colors.headerBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
        androidx.compose.material3.HorizontalDivider(
            color = colors.cardBorder,
            thickness = 1.dp,
        )
    }
}

/** アカウントアイコン（ホームヘッダー用） */
@Composable
fun AccountIconButton(onClick: () -> Unit) {
    val colors = LocalInfoldColors.current
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = colors.textSecondary)
    }
}

// ============================================================
// フローティング型ナビゲーションバー
// 画面下部から少し浮いた Liquid Glass カプセル型。
// 背景コンテンツを別の GraphicsLayer に記録し、Android 12 以降では
// RenderEffect のブラーをそのレイヤーに適用してからガラス内へ描画する。
// これにより単なる alpha の低い色ではなく、実際の背後コンテンツが
// 見える（かつ軽くぼやける）構成になる。
// ============================================================

data class FloatingNavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

/**
 * Compose のコンテンツを通常用とブラー用の2つのネイティブ GraphicsLayer に記録する。
 * Android 12 未満では RenderEffect が使えないため、透明ガラスのみへフォールバックする。
 */
@OptIn(ExperimentalGraphicsApi::class)
@Composable
fun BackdropContent(
    contentLayer: GraphicsLayer,
    blurLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {

    SideEffect {
        @Suppress("NewApi")
        blurLayer.renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BlurEffect(
                radiusX = 20f,
                radiusY = 20f,
                edgeTreatment = TileMode.Clamp,
            )
        } else {
            null
        }
    }

    Box(
        modifier = modifier.drawWithContent {
            // 2回目の記録は同じ背後コンテンツをブラー用に保持するためのもの。
            val layerSize = size.toIntSize()
            contentLayer.record(
                density = this,
                layoutDirection = layoutDirection,
                size = layerSize,
            ) { this@drawWithContent.drawContent() }
            blurLayer.record(
                density = this,
                layoutDirection = layoutDirection,
                size = layerSize,
            ) { this@drawWithContent.drawContent() }
            drawLayer(contentLayer)
        },
    ) {
        content()
    }
}

@OptIn(ExperimentalGraphicsApi::class)
@Composable
fun FloatingBottomNavBar(
    items: List<FloatingNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    blurLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    val colors = LocalInfoldColors.current
    val density = LocalDensity.current
    val selectedIndex = items.indexOfFirst { item ->
        if (item.route == "articles") currentRoute?.startsWith("articles") == true
        else currentRoute == item.route
    }
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "glass-indicator",
    )

    // バー自身は不透明な背景を持たず、呼び出し側の背後レイヤーを受け取る。
    FloatingBottomNavBarSurface(
        items = items,
        onSelect = onSelect,
        colors = colors,
        density = density,
        selectedIndex = selectedIndex,
        indicatorAlpha = indicatorAlpha,
        blurLayer = blurLayer,
        modifier = modifier,
    )
}

@OptIn(ExperimentalGraphicsApi::class)
@Composable
private fun FloatingBottomNavBarSurface(
    items: List<FloatingNavItem>,
    onSelect: (String) -> Unit,
    colors: jp.infold.news.ui.theme.InfoldColors,
    density: androidx.compose.ui.unit.Density,
    selectedIndex: Int,
    indicatorAlpha: Float,
    blurLayer: GraphicsLayer,
    modifier: Modifier,
) {
    val barHeight = with(density) { 56.dp.toPx() }
    val horizontalPadding = with(density) { 24.dp.toPx() }
    val bottomPadding = with(density) { 12.dp.toPx() }
    val itemSize = with(density) { 48.dp.toPx() }
    val radius = with(density) { 28.dp.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val barWidth = size.width - horizontalPadding * 2f
            val barTop = size.height - bottomPadding - barHeight
            val barRect = Rect(
                left = horizontalPadding,
                top = barTop,
                right = horizontalPadding + barWidth,
                bottom = barTop + barHeight,
            )
            val barPath = Path().apply {
                addRoundRect(RoundRect(barRect, CornerRadius(radius, radius)))
            }

            // バーの下にだけ影を置き、画面から浮いている質感を作る。
            drawRoundRect(
                color = Color.Black.copy(alpha = if (colors.isDark) 0.24f else 0.12f),
                topLeft = Offset(barRect.left, barRect.top + with(density) { 4.dp.toPx() }),
                size = Size(barRect.width, barRect.height),
                cornerRadius = CornerRadius(radius, radius),
            )

            clipPath(barPath) {
                // 背景コンテンツそのものを RenderEffect 付きで描画するため、
                // 画像・文字・背景がガラス越しに実際に見え、軽くぼやける。
                drawLayer(blurLayer)
                drawRoundRect(
                    brush = if (colors.isDark) {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.13f),
                                Color(0xFF17243D).copy(alpha = 0.16f),
                                Color(0xFF0C1220).copy(alpha = 0.10f),
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.34f),
                                Color.White.copy(alpha = 0.22f),
                                Color(0xFFE8F0FF).copy(alpha = 0.16f),
                            )
                        )
                    },
                    topLeft = barRect.topLeft,
                    size = barRect.size,
                    cornerRadius = CornerRadius(radius, radius),
                )
                // 控えめな斜めの反射。
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.04f),
                        ),
                        start = Offset(barRect.left, barRect.top),
                        end = Offset(barRect.right, barRect.bottom),
                    ),
                    topLeft = barRect.topLeft,
                    size = barRect.size,
                    cornerRadius = CornerRadius(radius, radius),
                )
            }

            // 薄い明色の外周と、上側の柔らかなハイライト。
            drawRoundRect(
                color = Color.White.copy(alpha = if (colors.isDark) 0.30f else 0.58f),
                topLeft = barRect.topLeft,
                size = barRect.size,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = with(density) { 1.dp.toPx() }),
            )
            drawLine(
                color = Color.White.copy(alpha = if (colors.isDark) 0.22f else 0.42f),
                start = Offset(barRect.left + radius, barRect.top + with(density) { 1.dp.toPx() }),
                end = Offset(barRect.right - radius, barRect.top + with(density) { 1.dp.toPx() }),
                strokeWidth = with(density) { 1.5.dp.toPx() },
            )

            if (selectedIndex >= 0 && indicatorAlpha > 0f) {
                val innerLeft = barRect.left + with(density) { 8.dp.toPx() }
                val innerWidth = barRect.width - with(density) { 16.dp.toPx() }
                val gap = ((innerWidth - itemSize * items.size) / (items.size + 1)).coerceAtLeast(0f)
                val centerX = innerLeft + gap + itemSize / 2f + selectedIndex * (itemSize + gap)
                val centerY = barTop + barHeight / 2f
                val selectionRadius = itemSize / 2f
                val selectionPath = Path().apply {
                    addOval(
                        Rect(
                            centerX - selectionRadius,
                            centerY - selectionRadius,
                            centerX + selectionRadius,
                            centerY + selectionRadius,
                        )
                    )
                }

                clipPath(selectionPath) {
                    drawLayer(blurLayer)
                    drawCircle(
                        color = colors.primary.copy(alpha = 0.12f * indicatorAlpha),
                        radius = selectionRadius,
                        center = Offset(centerX, centerY),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = if (colors.isDark) 0.14f else 0.28f),
                        radius = selectionRadius - with(density) { 1.dp.toPx() },
                        center = Offset(centerX, centerY),
                        style = Stroke(width = with(density) { 1.dp.toPx() }),
                    )
                }
                // 選択領域の内側上端にだけ明るい反射を置く。
                drawArc(
                    color = Color.White.copy(alpha = 0.30f * indicatorAlpha),
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(
                        centerX - selectionRadius + with(density) { 2.dp.toPx() },
                        centerY - selectionRadius + with(density) { 2.dp.toPx() },
                    ),
                    size = Size(
                        (selectionRadius - with(density) { 2.dp.toPx() }) * 2f,
                        (selectionRadius - with(density) { 2.dp.toPx() }) * 2f,
                    ),
                    style = Stroke(width = with(density) { 1.dp.toPx() }),
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                FloatingNavItemButton(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(item.route) },
                )
            }
        }
    }
}

@Composable
private fun FloatingNavItemButton(
    item: FloatingNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "icon",
    )
    val label = remember(item.labelRes) { context.getString(item.labelRes) }

    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = label,
            tint = if (selected) colors.primary else colors.textFaint.copy(alpha = 0.72f),
            modifier = Modifier.size((22 * iconScale).dp),
        )
    }
}
