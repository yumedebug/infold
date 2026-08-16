package jp.infold.news.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import jp.infold.news.R
import jp.infold.news.data.Prefs
import jp.infold.news.isFirebaseAvailable
import jp.infold.news.ui.LocalSnackbarHostState
import jp.infold.news.ui.components.BrandLogo
import jp.infold.news.ui.components.GlassCard
import jp.infold.news.ui.components.SubTopBar
import jp.infold.news.ui.theme.LocalInfoldColors
import kotlinx.coroutines.launch

// ============================================================
// 設定（ネイティブ UI）
// 言語・テーマ・プッシュ通知・アプリ情報
// ============================================================

@Composable
fun SettingsScreen(
    language: String,
    themeMode: String,
    onBack: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetTheme: (String) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    var notificationsEnabled by remember {
        mutableStateOf(Prefs.getNotificationsEnabled(context))
    }
    val fcmAvailable = remember { isFirebaseAvailable(context) }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "2.0.0"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notificationsEnabled = true
            Prefs.setNotificationsEnabled(context, true)
            onToggleNotifications(true)
        } else {
            notificationsEnabled = false
            scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_permission_required)) }
        }
    }

    fun setNotifications(checked: Boolean) {
        if (!checked) {
            notificationsEnabled = false
            Prefs.setNotificationsEnabled(context, false)
            onToggleNotifications(false)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationsEnabled = true
            Prefs.setNotificationsEnabled(context, true)
            onToggleNotifications(true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        SubTopBar(title = context.getString(R.string.nav_settings), onBack = onBack)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // 言語
            item(key = "lang") {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = context.getString(R.string.settings_language),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LanguageOption(
                                label = "日本語",
                                selected = language == "ja",
                                onClick = { onSetLanguage("ja") },
                            )
                            LanguageOption(
                                label = "English",
                                selected = language == "en",
                                onClick = { onSetLanguage("en") },
                            )
                        }
                    }
                }
            }

            // テーマ
            item(key = "theme") {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = context.getString(R.string.settings_theme),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeOption(
                                label = context.getString(R.string.settings_theme_dark),
                                selected = themeMode == "dark",
                                onClick = { onSetTheme("dark") },
                            )
                            ThemeOption(
                                label = context.getString(R.string.settings_theme_light),
                                selected = themeMode == "light",
                                onClick = { onSetTheme("light") },
                            )
                            ThemeOption(
                                label = context.getString(R.string.settings_theme_system),
                                selected = themeMode == "system",
                                onClick = { onSetTheme("system") },
                            )
                        }
                    }
                }
            }

            // プッシュ通知
            item(key = "notifications") {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(R.string.settings_notifications),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = context.getString(R.string.settings_notifications_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textFaint,
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { setNotifications(it) },
                            enabled = fcmAvailable,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = colors.primary,
                                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                            ),
                        )
                    }
                    if (!fcmAvailable) {
                        Text(
                            text = "FCM: google-services.json 未設定（ビルド時に Firebase 設定を追加すると有効になります）",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textFaint,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // このアプリについて
            item(key = "about") {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BrandLogo(size = 44.dp)
                        Text(
                            text = "INFOLD",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = context.getString(R.string.settings_version) + " " + versionName,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textFaint,
                        )
                        Text(
                            text = context.getString(R.string.settings_about_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalInfoldColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) androidx.compose.ui.graphics.Color.White else colors.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primary else colors.backgroundSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalInfoldColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) androidx.compose.ui.graphics.Color.White else colors.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primary else colors.backgroundSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
