package jp.infold.news.ui.categories

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.infold.news.R
import jp.infold.news.data.Category
import jp.infold.news.ui.components.CategoryBadge
import jp.infold.news.ui.components.GlassCard
import jp.infold.news.ui.theme.LocalInfoldColors
import jp.infold.news.util.categoryDisplayName

// ============================================================
// カテゴリ一覧
// ============================================================

@Composable
fun CategoriesScreen(
    lang: String,
    categories: List<Category>,
    onOpenCategory: (String) -> Unit,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "head") {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = context.getString(R.string.nav_categories),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = context.getString(R.string.meta_home_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            items(categories, key = { it.slug }) { c ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    onClick = { onOpenCategory(c.slug) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(slug = c.slug, name = categoryDisplayName(c.slug, categories, lang))
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (lang == "ja") "${c.articleCount}記事" else "${c.articleCount} articles",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textFaint,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.textFaint,
                        )
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(20.dp)) }
        }
    }
}
