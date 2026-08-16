package jp.infold.news.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.infold.news.AuthState
import jp.infold.news.R
import jp.infold.news.data.ApiClient
import jp.infold.news.data.PointsSummary
import jp.infold.news.ui.components.BrandLogo
import jp.infold.news.ui.components.GlassCard
import jp.infold.news.ui.components.LoadingView
import jp.infold.news.ui.components.SubTopBar
import jp.infold.news.ui.theme.LocalInfoldColors
import jp.infold.news.util.formatRemaining

// ============================================================
// アカウント / メニュー（ネイティブ UI）
// ============================================================

@Composable
fun AccountScreen(
    authState: AuthState,
    onOpenLogin: () -> Unit,
    onOpenPoints: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current
    var points by remember { mutableStateOf<PointsSummary?>(null) }

    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) {
            points = try {
                ApiClient.points()
            } catch (_: Exception) {
                null
            }
        } else {
            points = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        SubTopBar(title = context.getString(R.string.nav_account), onBack = null)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            when (authState) {
                is AuthState.Loading -> item { LoadingView(Modifier.fillMaxSize()) }

                is AuthState.LoggedOut -> {
                    item(key = "intro") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BrandLogo(size = 64.dp)
                            Text(
                                text = context.getString(R.string.points_about_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = context.getString(R.string.points_about1),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = context.getString(R.string.points_about2),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = context.getString(R.string.points_about3),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textFaint,
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
                }

                is AuthState.LoggedIn -> {
                    val user = authState.user
                    item(key = "profile") {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            androidx.compose.ui.graphics.Brush.linearGradient(
                                                listOf(colors.primary, colors.primary2)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = (user.name.ifBlank { user.email }).take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = user.name.ifBlank { user.email },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = user.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textFaint,
                                    )
                                }
                            }
                        }
                    }

                    item(key = "points") {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            onClick = onOpenPoints,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = context.getString(R.string.points_balance_label),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textFaint,
                                    )
                                    Text(
                                        text = "${points?.points ?: 0} POINT",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = colors.textPrimary,
                                    )
                                    val adFree = points?.adFree
                                    if (adFree != null && adFree.active) {
                                        Text(
                                            text = context.getString(R.string.points_status_label) + "：" +
                                                context.getString(R.string.points_status_on) + "（" +
                                                formatRemaining(adFree.remainingSeconds ?: 0) + "）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.ok,
                                        )
                                    }
                                }
                                Text(
                                    text = "›",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colors.textFaint,
                                )
                            }
                        }
                    }

                    item(key = "settings") {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            onClick = onOpenSettings,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = context.getString(R.string.nav_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "›",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colors.textFaint,
                                )
                            }
                        }
                    }

                    item(key = "logout") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.warn.copy(alpha = 0.1f))
                                .clickable(onClick = onLogout)
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = context.getString(R.string.account_logout),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.warn,
                            )
                        }
                    }
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}
