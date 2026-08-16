package jp.infold.news.ui.points

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.infold.news.AuthState
import jp.infold.news.R
import jp.infold.news.data.AdFreePlan
import jp.infold.news.data.ApiClient
import jp.infold.news.data.PointEntry
import jp.infold.news.data.PointsSummary
import jp.infold.news.ui.LocalSnackbarHostState
import jp.infold.news.ui.UiState
import jp.infold.news.ui.components.ErrorView
import jp.infold.news.ui.components.GlassCard
import jp.infold.news.ui.components.LoadingView
import jp.infold.news.ui.components.SectionTitle
import jp.infold.news.ui.components.SubTopBar
import jp.infold.news.ui.theme.LocalInfoldColors
import jp.infold.news.util.formatPublishedAt
import jp.infold.news.util.formatRemaining
import kotlinx.coroutines.launch

// ============================================================
// INFOLD POINT（ネイティブ UI）
// 残高・一時的な広告削除（プラン利用）・ポイント履歴
// ============================================================

@Composable
fun PointsScreen(
    authState: AuthState,
    lang: String,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<UiState<PointsSummary>>(UiState.Loading) }
    var history by remember { mutableStateOf<List<PointEntry>>(emptyList()) }
    var confirmPlan by remember { mutableStateOf<AdFreePlan?>(null) }
    var busy by remember { mutableStateOf(false) }
    val isLoggedIn = authState is AuthState.LoggedIn

    suspend fun load() {
        state = UiState.Loading
        try {
            val summary = ApiClient.points()
            val hist = ApiClient.pointsHistory().history
            state = UiState.Ready(summary)
            history = hist
        } catch (e: Exception) {
            state = UiState.Error(
                if (ApiClient.isOfflineException(e)) {
                    context.getString(R.string.common_offline)
                } else {
                    context.getString(R.string.common_failed)
                }
            )
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) load() else state = UiState.Loading
    }

    fun usePlan(plan: AdFreePlan) {
        scope.launch {
            busy = true
            try {
                val res = ApiClient.spendAdFree(plan.points)
                if (res.ok) {
                    snackbar.showSnackbar(context.getString(R.string.points_used))
                    load()
                } else if (res.error == "insufficient_points") {
                    snackbar.showSnackbar(context.getString(R.string.points_insufficient))
                } else {
                    snackbar.showSnackbar(context.getString(R.string.common_failed))
                }
            } catch (_: Exception) {
                snackbar.showSnackbar(context.getString(R.string.account_network_error))
            }
            busy = false
            confirmPlan = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        SubTopBar(title = context.getString(R.string.nav_points), onBack = onBack)

        when {
            !isLoggedIn -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.height(48.dp))
                    Text(
                        text = context.getString(R.string.points_login_required),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = context.getString(R.string.points_about1) + "\n" +
                            context.getString(R.string.points_about2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    ) {
                        Text(
                            text = context.getString(R.string.points_login_cta),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            else -> when (val s = state) {
                is UiState.Loading -> LoadingView(Modifier.fillMaxSize())
                is UiState.Error -> ErrorView(
                    message = s.message,
                    debug = s.debug,
                    onRetry = { scope.launch { load() } },
                    modifier = Modifier.fillMaxSize(),
                )
                is UiState.Ready -> {
                    val summary = s.data
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // 残高
                        item(key = "balance") {
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = context.getString(R.string.points_balance_label),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textFaint,
                                    )
                                    Text(
                                        text = "${summary.points} POINT",
                                        style = MaterialTheme.typography.displaySmall,
                                        color = colors.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    val adFree = summary.adFree
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = context.getString(R.string.points_status_label) + "：",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.textSecondary,
                                        )
                                        if (adFree != null && adFree.active) {
                                            Text(
                                                text = context.getString(R.string.points_status_on) +
                                                    "（" + formatRemaining(adFree.remainingSeconds ?: 0) + "）",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.ok,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        } else {
                                            Text(
                                                text = context.getString(R.string.points_status_off),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.textFaint,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 獲得ルール
                        item(key = "rule") {
                            Text(
                                text = context.getString(R.string.points_earn_rule),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textFaint,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }

                        // 一時的な広告削除
                        item(key = "adfree-title") {
                            SectionTitle(
                                title = context.getString(R.string.points_ad_free_title),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                        item(key = "adfree-desc") {
                            Text(
                                text = context.getString(R.string.points_ad_free_desc) + "\n" +
                                    context.getString(R.string.points_ad_free_auto),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                        items(summary.plans, key = { it.points }) { plan ->
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${plan.points} POINT",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = colors.textPrimary,
                                        )
                                        Text(
                                            text = context.getString(R.string.points_plan_duration) + "：" + plan.durationLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textFaint,
                                        )
                                    }
                                    Button(
                                        onClick = { confirmPlan = plan },
                                        enabled = !busy && summary.points >= plan.points,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (summary.points >= plan.points) colors.primary else colors.surfaceHover,
                                            contentColor = if (summary.points >= plan.points) androidx.compose.ui.graphics.Color.White else colors.textFaint,
                                        ),
                                    ) {
                                        Text(context.getString(R.string.points_use))
                                    }
                                }
                            }
                        }

                        // 履歴
                        item(key = "history-title") {
                            SectionTitle(
                                title = context.getString(R.string.points_history_title),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                        if (history.isEmpty()) {
                            item(key = "history-empty") {
                                Text(
                                    text = context.getString(R.string.points_history_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textFaint,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                        } else {
                            items(history, key = { it.id }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(
                                                if (entry.amount > 0) colors.ok.copy(alpha = 0.14f)
                                                else colors.accent.copy(alpha = 0.14f)
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (entry.amount > 0) "+" else "−",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (entry.amount > 0) colors.ok else colors.accent,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (entry.type == "read") {
                                                context.getString(R.string.points_history_read)
                                            } else {
                                                context.getString(R.string.points_history_ad_free)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.textPrimary,
                                        )
                                        Text(
                                            text = formatPublishedAt(entry.createdAt, lang),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textFaint,
                                        )
                                    }
                                    Text(
                                        text = "${if (entry.amount > 0) "+" else ""}${entry.amount}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (entry.amount > 0) colors.ok else colors.textSecondary,
                                    )
                                }
                            }
                        }

                        item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    // 利用確認ダイアログ
    val plan = confirmPlan
    if (plan != null) {
        AlertDialog(
            onDismissRequest = { confirmPlan = null },
            title = { Text(context.getString(R.string.points_ad_free_title)) },
            text = {
                Text(
                    context.getString(
                        R.string.points_confirm,
                        plan.points,
                        plan.durationLabel,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { usePlan(plan) }, enabled = !busy) {
                    Text(context.getString(R.string.points_use), color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPlan = null }, enabled = !busy) {
                    Text(context.getString(R.string.common_close), color = colors.textFaint)
                }
            },
            containerColor = colors.backgroundSoft,
        )
    }
}
