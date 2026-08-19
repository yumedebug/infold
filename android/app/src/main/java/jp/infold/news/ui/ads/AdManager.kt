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
//
// INFOLD POINT の「一時的な広告削除」機能と連携：
//   adFree=true  → 広告を表示しない
//   adFree=false → 広告を表示する
//
// AdMob App ID は AndroidManifest.xml の <meta-data> で設定。
// 実際の広告ユニット ID は下の AD_UNIT_ID に設定する。
// 開発・テスト中はテスト広告ユニット ID を使用するのが安全。
//
// Web 版の Ninja AdMax は変更しない。
// ============================================================

/** AdMob 広告ユニット ID（テスト用 / 本番用に差し替え） */
private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111" // テストバナー

/**
 * AdMob バナー広告コンポーネント
 *
 * @param adFree true のとき広告を表示しない（INFOLD POINT の広告なしモード）
 * @param modifier レイアウト修飾子
 */
@Composable
fun InfoldAdBanner(
    adFree: Boolean,
    modifier: Modifier = Modifier,
) {
    // 広告非表示モードなら何も描画しない
    if (adFree) return

    // プレビュー画面（Android Studio）では AdMob を初期化しない
    val inspectionMode = LocalInspectionMode.current
    if (inspectionMode) {
        val colors = LocalInfoldColors.current
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface.copy(alpha = 0.5f))
                .border(1.dp, colors.glassBorder, RoundedCornerShape(8.dp)),
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
