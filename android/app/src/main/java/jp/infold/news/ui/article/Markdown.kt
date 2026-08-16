package jp.infold.news.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.ClickableText
import coil.compose.AsyncImage
import jp.infold.news.data.ApiClient
import jp.infold.news.ui.theme.LocalInfoldColors
import jp.infold.news.util.openExternalLink

// ============================================================
// Web 版と同じ「マークダウン風」本文フォーマットをネイティブ描画する
//   ## 見出し / - リスト / 空行区切りの段落 / **強調** / [リンク](url)
//   画像: ![alt](url)
// ============================================================

sealed class ContentBlock {
    data class Heading(val text: String) : ContentBlock()
    data class Paragraph(val spans: List<Span>) : ContentBlock()
    data class ListItem(val spans: List<Span>) : ContentBlock()
    data class BlockQuote(val spans: List<Span>) : ContentBlock()
    data class Image(val url: String, val alt: String) : ContentBlock()
}

sealed class Span {
    data class TextSpan(val text: String) : Span()
    data class BoldSpan(val text: String) : Span()
    data class LinkSpan(val text: String, val url: String) : Span()
}

object Markdown {

    private val INLINE_RE =
        Regex("(\\*\\*[^*]+\\*\\*|\\[[^\\]]+\\]\\([^)\\s]+\\)|https?://[^\\s()<>]+)")
    private val IMAGE_RE = Regex("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)")

    fun parse(content: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        for (rawLine in content.split("\n")) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("### ") || line.startsWith("## ") || line.startsWith("# ") ->
                    blocks += ContentBlock.Heading(line.substringAfter(' ').trim())

                line.startsWith("- ") || line.startsWith("* ") ->
                    blocks += ContentBlock.ListItem(parseInline(line.drop(2).trim()))

                line.startsWith("> ") ->
                    blocks += ContentBlock.BlockQuote(parseInline(line.drop(2).trim()))

                IMAGE_RE.matches(line) -> {
                    val m = IMAGE_RE.find(line)
                    if (m != null) {
                        blocks += ContentBlock.Image(m.groupValues[2], m.groupValues[1])
                    }
                }

                else -> blocks += ContentBlock.Paragraph(parseInline(line))
            }
        }
        return blocks
    }

    fun parseInline(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        var last = 0
        for (m in INLINE_RE.findAll(text)) {
            if (m.range.first > last) {
                spans += Span.TextSpan(text.substring(last, m.range.first))
            }
            val token = m.value
            when {
                token.startsWith("**") ->
                    spans += Span.BoldSpan(token.removePrefix("**").removeSuffix("**"))

                token.startsWith("[") -> {
                    val inner = token.substring(1, token.length - 1)
                    val idx = inner.indexOf("](")
                    if (idx > 0) {
                        spans += Span.LinkSpan(inner.substring(0, idx), inner.substring(idx + 2))
                    } else {
                        spans += Span.TextSpan(token)
                    }
                }

                token.startsWith("http") -> spans += Span.LinkSpan(token, token)
                else -> spans += Span.TextSpan(token)
            }
            last = m.range.last + 1
        }
        if (last < text.length) {
            spans += Span.TextSpan(text.substring(last))
        }
        return spans
    }
}

@Composable
fun MarkdownContent(blocks: List<ContentBlock>, modifier: Modifier = Modifier) {
    val colors = LocalInfoldColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (block in blocks) {
            when (block) {
                is ContentBlock.Heading -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 6.dp),
                )

                is ContentBlock.Paragraph -> InlineText(
                    spans = block.spans,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )

                is ContentBlock.ListItem -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    InlineText(
                        spans = block.spans,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }

                is ContentBlock.BlockQuote -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.backgroundSoft)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    InlineText(
                        spans = block.spans,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }

                is ContentBlock.Image -> {
                    val resolved = ApiClient.resolveImage(block.url)
                    if (resolved != null) {
                        AsyncImage(
                            model = resolved,
                            contentDescription = block.alt.ifBlank { null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

/** インライン要素（強調・リンク）を含むテキスト。リンクは外部ブラウザで開く */
@Composable
private fun InlineText(
    spans: List<Span>,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalInfoldColors.current
    val context = LocalContext.current
    val annotated = buildAnnotatedString {
        for (span in spans) {
            when (span) {
                is Span.TextSpan -> append(span.text)
                is Span.BoldSpan -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(span.text)
                }
                is Span.LinkSpan -> withLink(
                    LinkAnnotation.Clickable(
                        tag = span.url,
                        styles = TextLinkStyles(
                            style = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)
                        ),
                    ) {
                        openExternalLink(context, span.url)
                    }
                ) {
                    append(span.text)
                }
            }
        }
    }
    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        onClick = {},
        modifier = modifier,
    )
}
