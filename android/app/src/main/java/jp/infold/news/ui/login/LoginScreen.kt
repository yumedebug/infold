package jp.infold.news.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import jp.infold.news.data.ApiClient
import jp.infold.news.data.ApiException
import jp.infold.news.ui.components.BrandLogo
import jp.infold.news.ui.components.BrandWordmark
import jp.infold.news.ui.components.SubTopBar
import jp.infold.news.ui.theme.LocalInfoldColors
import kotlinx.coroutines.launch
import jp.infold.news.R

// ============================================================
// ログイン / 新規登録（ネイティブ UI）
// 認証は Web 版と同じクッキーベース API
// ============================================================

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    onLoginSuccess: (jp.infold.news.data.AuthResponse) -> Unit,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) } // 0=login 1=register
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (submitting) return
        val mail = email.trim()
        val pass = password
        if (mail.isEmpty() || pass.isEmpty()) {
            error = context.getString(R.string.common_failed)
            return
        }
        if (tab == 1 && pass.length < 8) {
            error = context.getString(R.string.account_password_short)
            return
        }
        scope.launch {
            submitting = true
            error = null
            try {
                val res = if (tab == 0) {
                    ApiClient.login(mail, pass)
                } else {
                    ApiClient.register(mail, pass, name.trim())
                }
                if (res.ok && res.user != null) {
                    onLoginSuccess(res)
                    onLoggedIn()
                } else {
                    error = context.getString(R.string.common_failed)
                }
            } catch (e: ApiException) {
                error = when (e.code) {
                    "invalid_credentials" -> context.getString(R.string.account_invalid_credentials)
                    "email_taken" -> context.getString(R.string.account_email_taken)
                    "password_too_short" -> context.getString(R.string.account_password_short)
                    "csrf_rejected" -> context.getString(R.string.account_network_error)
                    else -> e.message ?: context.getString(R.string.common_failed)
                }
            } catch (e: Exception) {
                error = if (ApiClient.isOfflineException(e)) {
                    context.getString(R.string.account_network_error)
                } else {
                    context.getString(R.string.common_failed)
                }
            }
            submitting = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        SubTopBar(title = context.getString(R.string.account_title), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            BrandLogo(size = 56.dp)
            Spacer(Modifier.height(4.dp))
            BrandWordmark()

            // タブ切替
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TabButton(
                    label = context.getString(R.string.account_login_tab),
                    selected = tab == 0,
                    onClick = { tab = 0; error = null },
                    modifier = Modifier.weight(1f),
                )
                TabButton(
                    label = context.getString(R.string.account_register_tab),
                    selected = tab == 1,
                    onClick = { tab = 1; error = null },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = context.getString(
                    if (tab == 0) R.string.account_login_desc else R.string.account_register_desc
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            if (tab == 1) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(context.getString(R.string.account_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(),
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(context.getString(R.string.account_email)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(context.getString(R.string.account_password)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
            )

            if (error != null) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warn,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = { submit() },
                enabled = !submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = context.getString(
                            if (tab == 0) R.string.account_sign_in else R.string.account_register
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalInfoldColors.current
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.primary else colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.primary else colors.glassBorder),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) androidx.compose.ui.graphics.Color.White else colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalInfoldColors.current.primary,
    unfocusedBorderColor = LocalInfoldColors.current.glassBorder,
    focusedContainerColor = LocalInfoldColors.current.surface,
    unfocusedContainerColor = LocalInfoldColors.current.surface,
    cursorColor = LocalInfoldColors.current.primary,
    focusedTextColor = LocalInfoldColors.current.textPrimary,
    unfocusedTextColor = LocalInfoldColors.current.textPrimary,
    focusedLabelColor = LocalInfoldColors.current.primary,
    unfocusedLabelColor = LocalInfoldColors.current.textFaint,
)
