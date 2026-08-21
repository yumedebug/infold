package jp.infold.news.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.infold.news.R
import jp.infold.news.data.ApiClient
import jp.infold.news.data.Article
import jp.infold.news.data.Category
import jp.infold.news.ui.UiState
import jp.infold.news.ui.ads.InfoldAdBanner
import jp.infold.news.ui.components.ArticleRowCard
import jp.infold.news.ui.components.BrandLogo
import jp.infold.news.ui.components.BrandWordmark
import jp.infold.news.ui.components.CategoryChip
import jp.infold.news.ui.components.ErrorView
import jp.infold.news.ui.components.FeaturedBannerCard
import jp.infold.news.ui.components.GlassCard
import jp.infold.news.ui.components.HeroCard
import jp.infold.news.ui.components.LoadingView
import jp.infold.news.ui.components.SectionTitle
import jp.infold.news.ui.theme.LocalInfoldColors
import jp.infold.news.util.categoryDisplayName

// ============================================================
// ホーム（ソリッド UI）
// 注目記事（ヒーロー）+ 最新ニュース + 広告 + カテゴリ
// ============================================================

private data class HomeData(
    val hero: Article?,
    val latest: List<Article>,
    val featured: List<Article>,
)

@Composable
fun HomeScreen(
    lang: String,
    categories: List<Category>,
    adFree: Boolean,
    onOpenArticle: (Long) -> Unit,
    onOpenArticles: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenAccount: () -> Unit,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current
    var state by remember { mutableStateOf<UiState<HomeData>>(UiState.Loading) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        state = UiState.Loading
        state = try {
            val featured = ApiClient.listArticles(page = 1, limit = 3, featured = true).articles
            val latest = ApiClient.listArticles(page = 1, limit = 11).articles
            val hero = featured.firstOrNull() ?: latest.firstOrNull()
            val latestFiltered = latest.filter { it.id != hero?.id }
            UiState.Ready(HomeData(hero = hero, latest = latestFiltered, featured = featured))
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

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // ヘッダー（ソリッド背景）
        Column(modifier = Modifier.fillMaxWidth().background(colors.headerBackground)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandLogo(size = 30.dp)
                Spacer(Modifier.width(10.dp))
                BrandWordmark()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenAccount) {
                    Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.nav_account), tint = colors.textSecondary)
                }
            }
            androidx.compose.material3.HorizontalDivider(
                color = colors.cardBorder,
                thickness = 1.dp,
            )
        }

        when (val s = state) {
            is UiState.Loading -> LoadingView(Modifier.fillMaxSize())

            is UiState.Error -> ErrorView(
                message = s.message,
                onRetry = { refreshKey++ },
                debug = s.debug,
                modifier = Modifier.fillMaxSize(),
            )

            is UiState.Ready -> {
                val data = s.data
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // ヒーロー
                    data.hero?.let { hero ->
                        item(key = "hero") {
                            HeroCard(
                                article = hero,
                                lang = lang,
                                onClick = { onOpenArticle(hero.id) },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                        }
                    }

                    // 最新ニュース（広告を交互に挿入）
                    item(key = "latest-title") {
                        SectionTitle(
                            title = context.getString(R.string.home_latest),
                            actionText = context.getString(R.string.home_all_articles),
                            onAction = onOpenArticles,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                    data.latest.forEachIndexed { index, article ->
                        item(key = "article-${article.id}") {
                            ArticleRowCard(
                                article = article,
                                lang = lang,
                                onClick = { onOpenArticle(article.id) },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                        // 3件ごとに広告バナーを挿入
                        if (adFree.not() && (index + 1) % 3 == 0 && index < data.latest.lastIndex) {
                            item(key = "ad-home-$index") {
                                InfoldAdBanner(
                                    adFree = adFree,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }

                    // カテゴリ
                    if (categories.isNotEmpty()) {
                        item(key = "cats-title") {
                            SectionTitle(
                                title = context.getString(R.string.home_categories),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                        item(key = "cats") {
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(categories) { c ->
                                    CategoryChip(
                                        slug = c.slug,
                                        name = categoryDisplayName(c.slug, categories, lang),
                                        selected = false,
                                        onClick = { onOpenCategory(c.slug) },
                                    )
                                }
                            }
                        }
                    }

                    // おすすめ記事（水平カルーセル）
                    val extraFeatured = data.featured.filter { it.id != data.hero?.id }
                    if (extraFeatured.isNotEmpty()) {
                        item(key = "feat-title") {
                            SectionTitle(
                                title = context.getString(R.string.home_featured),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                        item(key = "feat-carousel") {
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(extraFeatured, key = { it.id }) { article ->
                                    FeaturedBannerCard(
                                        article = article,
                                        lang = lang,
                                        onClick = { onOpenArticle(article.id) },
                                    )
                                }
                            }
                        }
                    }

                    item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
