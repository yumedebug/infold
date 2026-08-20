package jp.infold.news.ui.ads

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import jp.infold.news.ui.theme.LocalInfoldColors

// ============================================================
// INFOLD 広告マネージャー — AdMob バナー広告
// INFOLD POINT の「一時的な広告削除」機能と連携
// ============================================================

/** AdMob 広告ユニット ID（テスト用 / 本番用に差し替え） */
private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111" // テストバナー

@Composable
fun InfoldAdBanner(
    adFree: Boolean,
    modifier: Modifier = Modifier,
) {
    if (adFree) return

    val inspectionMode = LocalInspectionMode.current
    if (inspectionMode) {
        val colors = LocalInfoldColors.current
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp)),
        )
        return
    }

    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = AD_UNIT_ID
        }
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }

    AndroidView(
        factory = { adView },
        update = { view ->
            view.loadAd(AdRequest.Builder().build())
        },
        modifier = modifier.fillMaxWidth(),
    )
}
