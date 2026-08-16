package jp.infold.news.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jp.infold.news.R
import jp.infold.news.data.ApiClient
import jp.infold.news.data.Article
import jp.infold.news.ui.components.ArticleRowCard
import jp.infold.news.ui.components.EmptyView
import jp.infold.news.ui.components.ErrorView
import jp.infold.news.ui.components.LoadingView
import jp.infold.news.ui.theme.LocalInfoldColors

// ============================================================
// 検索（ネイティブ UI）
// ============================================================

@Composable
fun SearchScreen(
    lang: String,
    onOpenArticle: (Long) -> Unit,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var searchKey by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf<List<Article>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(searchKey) {
        val q = query.trim()
        if (q.isEmpty()) return@LaunchedEffect
        loading = true
        error = null
        searched = true
        try {
            results = ApiClient.search(q).articles
        } catch (e: Exception) {
            error = if (ApiClient.isOfflineException(e)) {
                context.getString(R.string.common_offline)
            } else {
                context.getString(R.string.common_failed)
            }
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            placeholder = { Text(context.getString(R.string.search_placeholder), color = colors.textFaint) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textFaint) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { searchKey++ }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.cardBorder,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                cursorColor = colors.primary,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
            ),
        )

        when {
            loading -> LoadingView(Modifier.fillMaxSize())

            error != null -> ErrorView(
                message = error!!,
                onRetry = { searchKey++ },
                modifier = Modifier.fillMaxSize(),
            )

            searched && results.isEmpty() -> EmptyView(
                message = context.getString(R.string.search_no_results),
                modifier = Modifier.fillMaxSize(),
            )

            searched -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "count") {
                    Text(
                        text = context.getString(R.string.search_results_for, query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                items(results, key = { it.id }) { article ->
                    ArticleRowCard(
                        article = article,
                        lang = lang,
                        onClick = { onOpenArticle(article.id) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                item(key = "bottom") { Spacer(Modifier.height(20.dp)) }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
            ) {
                Spacer(Modifier.height(60.dp))
                Text(
                    text = context.getString(R.string.search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textFaint,
                )
            }
        }
    }
}
