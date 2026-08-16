package jp.infold.news.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.infold.news.R
import jp.infold.news.data.ApiClient
import jp.infold.news.data.ArticleDetailResponse
import jp.infold.news.data.Category
import jp.infold.news.ui.LocalSnackbarHostState
import jp.infold.news.ui.UiState
import jp.infold.news.ui.components.ArticleRowCard
import jp.infold.news.ui.components.CategoryBadge
import jp.infold.news.ui.components.ErrorView
import jp.infold.news.ui.components.GlassCard
import jp.infold.news.ui.components.LoadingView
import jp.infold.news.ui.components.SectionTitle
import jp.infold.news.ui.components.SubTopBar
import jp.infold.news.ui.theme.LocalInfoldColors
import jp.infold.news.util.categoryDisplayName
import jp.infold.news.util.formatPublishedAt
import jp.infold.news.util.openExternalLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ============================================================
// 記事詳細（ネイティブ UI）
// API から取得した記事データを表示する。WebView は使用しない。
// 本文中の外部リンクのみ外部ブラウザで開く。
// ============================================================

@Composable
fun ArticleDetailScreen(
    articleId: Long,
    lang: String,
    isLoggedIn: Boolean,
    categories: List<Category>,
    onBack: () -> Unit,
    onOpenArticle: (Long) -> Unit,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<UiState<ArticleDetailResponse>>(UiState.Loading) }
    // 記事ごとに新しいリスト状態（先頭から表示）
    val scrollState = remember(articleId) { LazyListState() }
    var claimArmed by remember { mutableStateOf(false) }
    var claimDone by remember { mutableStateOf(false) }

    LaunchedEffect(articleId) {
        state = UiState.Loading
        claimArmed = false
        claimDone = false
        state = try {
            UiState.Ready(ApiClient.getArticle(articleId))
        } catch (e: Exception) {
            UiState.Error(
                if (ApiClient.isOfflineException(e)) {
                    context.getString(R.string.common_offline)
                } else {
                    context.getString(R.string.common_failed)
                },
                debug = e.javaClass.simpleName + ": " + (e.message ?: ""),
            )
        }
    }

    // 30秒経過でポイント獲得のアームを開始
    LaunchedEffect(articleId) {
        delay(30_000)
        claimArmed = true
    }

    // 末尾までスクロールしたら /complete を呼んで +1 POINT を獲得
    LaunchedEffect(scrollState, claimArmed, claimDone, isLoggedIn, articleId) {
        if (!claimArmed || claimDone || !isLoggedIn) return@LaunchedEffect
        snapshotFlow {
            val info = scrollState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 1
        }.first { it }
        claimDone = true
        val res = try {
            ApiClient.completeArticle(articleId)
        } catch (_: Exception) {
            null
        }
        if (res != null) {
            val msg = if (res.awarded) {
                context.getString(R.string.article_points_claimed)
            } else {
                context.getString(R.string.article_points_already)
            }
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SubTopBar(title = context.getString(R.string.nav_articles), onBack = onBack)

        when (val s = state) {
            is UiState.Loading -> LoadingView(Modifier.fillMaxSize())

            is UiState.Error -> ErrorView(
                message = s.message,
                debug = s.debug,
                onRetry = {
                    scope.launch {
                        state = UiState.Loading
                        state = try {
                            UiState.Ready(ApiClient.getArticle(articleId))
                        } catch (e: Exception) {
                            UiState.Error(
                                if (ApiClient.isOfflineException(e)) {
                                    context.getString(R.string.common_offline)
                                } else {
                                    context.getString(R.string.common_failed)
                                },
                                debug = e.javaClass.simpleName + ": " + (e.message ?: ""),
                            )
                        }
                    }
                },
            )

            is UiState.Ready -> {
                val detail = s.data
                val article = detail.article
                val resolvedThumb = ApiClient.resolveImage(article.thumbnail)

                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // ヒーロー画像
                    item(key = "hero") {
                        if (resolvedThumb != null) {
                            AsyncImage(
                                model = resolvedThumb,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(230.dp),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }

                    // タイトル・メタ
                    item(key = "head") {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = article.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = colors.textPrimary,
                            )
                            if (article.description.isNotBlank()) {
                                Text(
                                    text = article.description,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = colors.textSecondary,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CategoryBadge(
                                    slug = article.category,
                                    name = categoryDisplayName(article.category, categories, lang),
                                )
                                if (article.featured) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            Text(
                                text = formatPublishedAt(article.publishedAt, lang),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textFaint,
                            )
                            if (article.sourceName != null && article.sourceName.isNotBlank()) {
                                Text(
                                    text = context.getString(R.string.article_source) + "：" + article.sourceName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textFaint,
                                )
                            }
                        }
                    }

                    // 本文
                    item(key = "content") {
                        val blocks = remember(article.content) { Markdown.parse(article.content) }
                        MarkdownContent(
                            blocks = blocks,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }

                    // 元記事リンク
                    if (!article.sourceUrl.isNullOrBlank()) {
                        item(key = "source") {
                            GlassCard(
                                modifier = Modifier
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                                    .fillMaxWidth(),
                                onClick = { openExternalLink(context, article.sourceUrl) },
                            ) {
                                Text(
                                    text = context.getString(R.string.article_read_original) + " ↗",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.primary,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }

                    // 関連記事
                    if (detail.related.isNotEmpty()) {
                        item(key = "related-title") {
                            SectionTitle(
                                title = context.getString(R.string.article_related),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                        items(detail.related, key = { it.id }) { related ->
                            ArticleRowCard(
                                article = related,
                                lang = lang,
                                onClick = { onOpenArticle(related.id) },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                    }

                    item(key = "bottom") { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}
