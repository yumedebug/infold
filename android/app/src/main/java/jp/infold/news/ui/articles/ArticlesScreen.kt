package jp.infold.news.ui.articles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.infold.news.R
import jp.infold.news.data.ApiClient
import jp.infold.news.data.Article
import jp.infold.news.data.Category
import jp.infold.news.ui.components.ArticleRowCard
import jp.infold.news.ui.components.CategoryChip
import jp.infold.news.ui.components.EmptyView
import jp.infold.news.ui.components.ErrorView
import jp.infold.news.ui.components.LoadingView
import jp.infold.news.ui.theme.LocalInfoldColors
import jp.infold.news.util.categoryDisplayName
import kotlinx.coroutines.flow.distinctUntilChanged

// ============================================================
// 記事一覧（ネイティブ UI）
// カテゴリフィルター + ページネーション（もっと見る）
// ============================================================

@Composable
fun ArticlesScreen(
    lang: String,
    categories: List<Category>,
    initialCategory: String?,
    onOpenArticle: (Long) -> Unit,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current

    var selected by remember { mutableStateOf(initialCategory) }
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var total by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    suspend fun loadFirst(category: String?) {
        articles = emptyList()
        page = 1
        total = 0
        loading = true
        error = null
        listState.scrollToItem(0)
        try {
            val res = ApiClient.listArticles(page = 1, limit = 12, category = category)
            articles = res.articles
            total = res.total
            page = 2
        } catch (e: Exception) {
            error = if (ApiClient.isOfflineException(e)) {
                context.getString(R.string.common_offline)
            } else {
                context.getString(R.string.common_failed)
            }
        }
        loading = false
    }

    suspend fun loadMore() {
        if (loading || loadingMore || articles.size >= total) return
        loadingMore = true
        try {
            val res = ApiClient.listArticles(page = page, limit = 12, category = selected)
            articles = articles + res.articles
            total = res.total
            page += 1
        } catch (_: Exception) {
            // もっと見る失敗は無視（スクロールで再試行）
        }
        loadingMore = false
    }

    LaunchedEffect(selected, refreshKey) { loadFirst(selected) }

    // 末尾近くまでスクロールしたら次ページを読み込む
    LaunchedEffect(listState, articles.size, loading, loadingMore) {
        if (loading || loadingMore) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) loadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // カテゴリフィルター
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CategoryChip(
                    slug = null,
                    name = context.getString(R.string.articles_filter_all),
                    selected = selected == null,
                    onClick = { selected = null },
                )
            }
            items(categories) { c ->
                CategoryChip(
                    slug = c.slug,
                    name = categoryDisplayName(c.slug, categories, lang),
                    selected = selected == c.slug,
                    onClick = { selected = c.slug },
                )
            }
        }

        when {
            loading -> LoadingView(Modifier.fillMaxSize())

            error != null -> ErrorView(
                message = error!!,
                onRetry = { refreshKey++ },
                modifier = Modifier.fillMaxSize(),
            )

            articles.isEmpty() -> EmptyView(
                message = context.getString(R.string.common_no_articles),
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(articles, key = { it.id }) { article ->
                    ArticleRowCard(
                        article = article,
                        lang = lang,
                        onClick = { onOpenArticle(article.id) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                item(key = "footer") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            loadingMore -> CircularProgressIndicator(
                                color = colors.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            articles.size >= total && total > 0 -> Text(
                                text = context.getString(R.string.common_end_of_list),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = colors.textFaint,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}
