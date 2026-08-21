package jp.infold.news.ui.articles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.infold.news.R
import jp.infold.news.data.ApiClient
import jp.infold.news.data.Article
import jp.infold.news.data.Category
import jp.infold.news.ui.ads.InfoldAdBanner
import jp.infold.news.ui.components.ArticleRowCard
import jp.infold.news.ui.components.CategoryChip
import jp.infold.news.ui.components.EmptyView
import jp.infold.news.ui.components.ErrorView
import jp.infold.news.ui.components.FeaturedBannerCard
import jp.infold.news.ui.components.LoadingView
import jp.infold.news.ui.components.SectionTitle
import jp.infold.news.ui.theme.LocalInfoldColors
import jp.infold.news.util.categoryDisplayName
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

// ============================================================
// 記事一覧（ソリッド UI）
// カテゴリフィルター + ページネーション + 広告バナー
// ============================================================

@Composable
fun ArticlesScreen(
    lang: String,
    categories: List<Category>,
    initialCategory: String?,
    adFree: Boolean,
    onOpenArticle: (Long) -> Unit,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current
    val retryScope = rememberCoroutineScope()

    var selected by remember { mutableStateOf(initialCategory) }
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    var featured by remember { mutableStateOf<List<Article>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var total by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var loadMoreBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadMoreError by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    // LazyListState は selected が変わったときだけ再作成
    val listState = remember(selected) {
        androidx.compose.foundation.lazy.LazyListState()
    }

    // --- 最初のページを読み込む ---
    suspend fun loadFirst(category: String?) {
        articles = emptyList()
        featured = emptyList()
        page = 1
        total = 0
        loading = true
        error = null
        loadMoreError = false
        try {
            val res = ApiClient.listArticles(page = 1, limit = 20, category = category)
            articles = res.articles
            total = res.total
            page = 2
            // おすすめ記事を取得（フィルターなしの場合のみ）
            if (category == null) {
                try {
                    featured = ApiClient.listArticles(page = 1, limit = 5, featured = true).articles
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            error = if (ApiClient.isOfflineException(e)) {
                context.getString(R.string.common_offline)
            } else {
                context.getString(R.string.common_failed) + "\n" +
                    e.javaClass.simpleName + ": " + (e.message ?: "")
            }
        }
        loading = false
    }

    // --- 追加読み込み ---
    suspend fun loadMore() {
        if (loading || loadMoreBusy || articles.size >= total || error != null) return
        loadMoreBusy = true
        loadMoreError = false
        try {
            val res = ApiClient.listArticles(page = page, limit = 20, category = selected)
            if (res.articles.isEmpty()) {
                // 空ページ → 全件読み込み済み
                total = articles.size.toLong()
            } else {
                articles = articles + res.articles
                total = res.total
                page += 1
            }
        } catch (_: Exception) {
            loadMoreError = true
        }
        loadMoreBusy = false
    }

    LaunchedEffect(selected, refreshKey) { loadFirst(selected) }

    // --- スクロール位置の監視（articles.size に依存しない） ---
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 5
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && !loading && !loadMoreBusy) {
                loadMore()
            }
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
                // おすすめ記事（水平カルーセル）
                if (featured.isNotEmpty()) {
                    item(key = "feat-title") {
                        SectionTitle(
                            title = context.getString(R.string.home_featured),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                    item(key = "feat-carousel") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(featured, key = { it.id }) { article ->
                                FeaturedBannerCard(
                                    article = article,
                                    lang = lang,
                                    onClick = { onOpenArticle(article.id) },
                                )
                            }
                        }
                    }
                }

                articles.forEachIndexed { index, article ->
                    item(key = "article-${article.id}") {
                        ArticleRowCard(
                            article = article,
                            lang = lang,
                            onClick = { onOpenArticle(article.id) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                    // 4件ごとに広告バナーを挿入
                    if (adFree.not() && (index + 1) % 4 == 0 && index < articles.lastIndex) {
                        item(key = "ad-articles-$index") {
                            InfoldAdBanner(
                                adFree = adFree,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                // フッター（読み込み中 / 最後まで表示 / エラー）
                item(key = "footer") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            loadMoreBusy -> CircularProgressIndicator(
                                color = colors.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            loadMoreError -> Text(
                                text = context.getString(R.string.common_failed) + " (タップで再試行)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { retryScope.launch { loadMore() } }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                            articles.size >= total && total > 0 -> Text(
                                text = context.getString(R.string.common_end_of_list),
                                style = MaterialTheme.typography.bodySmall,
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
