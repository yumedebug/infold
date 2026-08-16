package jp.infold.news.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

// ============================================================
// INFOLD API のレスポンスモデル（Cloudflare Worker / D1）
// ============================================================

/**
 * D1 は INTEGER(0/1) で返すが、モデルは Boolean で扱いたいときに使う。
 * JSON の 0/1/true/false のどれでも受け付ける。
 */
object BooleanIntSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BooleanOrInt", PrimitiveKind.BOOLEAN)

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }

    override fun deserialize(decoder: Decoder): Boolean {
        if (decoder !is JsonDecoder) return decoder.decodeBoolean()
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.isString ->
                element.content.equals("true", ignoreCase = true)
            element is JsonPrimitive ->
                element.content == "1" || element.content.equals("true", ignoreCase = true)
            else -> false
        }
    }
}

@Serializable
data class Article(
    val id: Long,
    val title: String = "",
    val description: String = "",
    val thumbnail: String? = null,
    val category: String = "other",
    @Serializable(with = BooleanIntSerializer::class) val featured: Boolean = false,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @Serializable(with = BooleanIntSerializer::class)
    @SerialName("is_automated") val isAutomated: Boolean = false,
)

@Serializable
data class ArticleDetail(
    val id: Long,
    val title: String = "",
    val description: String = "",
    val content: String = "",
    val thumbnail: String? = null,
    val category: String = "other",
    @Serializable(with = BooleanIntSerializer::class) val featured: Boolean = false,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @Serializable(with = BooleanIntSerializer::class)
    @SerialName("is_automated") val isAutomated: Boolean = false,
)

@Serializable
data class ArticleListResponse(
    val articles: List<Article> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val limit: Int = 12,
)

@Serializable
data class ArticleDetailResponse(
    val article: ArticleDetail,
    val related: List<Article> = emptyList(),
)

@Serializable
data class Category(
    val id: Long,
    val name: String = "",
    @SerialName("name_en") val nameEn: String = "",
    val slug: String = "",
    @SerialName("article_count") val articleCount: Long = 0,
)

@Serializable
data class CategoriesResponse(
    val categories: List<Category> = emptyList(),
)

@Serializable
data class User(
    val id: Long,
    val email: String = "",
    val name: String = "",
)

@Serializable
data class MeResponse(
    val user: User? = null,
)

@Serializable
data class AuthResponse(
    val ok: Boolean = false,
    val user: User? = null,
)

@Serializable
data class ApiErrorBody(
    val error: String = "",
    val message: String? = null,
    val points: Long? = null,
    @SerialName("nextAvailableAt") val nextAvailableAt: String? = null,
)

@Serializable
data class PointsSummary(
    val points: Long = 0,
    @SerialName("adFree") val adFree: AdFreeStatus? = null,
    val plans: List<AdFreePlan> = emptyList(),
)

@Serializable
data class AdFreeStatus(
    val active: Boolean = false,
    @SerialName("expiresAt") val expiresAt: String? = null,
    @SerialName("remainingSeconds") val remainingSeconds: Long? = null,
)

@Serializable
data class AdFreePlan(
    val points: Long = 0,
    @SerialName("durationHours") val durationHours: Long = 0,
    @SerialName("durationLabel") val durationLabel: String = "",
)

@Serializable
data class PointsHistoryResponse(
    val history: List<PointEntry> = emptyList(),
)

@Serializable
data class PointEntry(
    val id: Long = 0,
    val amount: Long = 0,
    val type: String = "",
    @SerialName("article_id") val articleId: Long? = null,
    val reason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CompleteResponse(
    val awarded: Boolean = false,
    val points: Long? = null,
    @SerialName("nextAvailableAt") val nextAvailableAt: String? = null,
)

@Serializable
data class SpendAdFreeResponse(
    val ok: Boolean = false,
    val plan: Long? = null,
    @SerialName("expiresAt") val expiresAt: String? = null,
    val points: Long? = null,
    val error: String? = null,
)

@Serializable
data class AdFreeStatusResponse(
    val active: Boolean = false,
    @SerialName("expiresAt") val expiresAt: String? = null,
    @SerialName("remainingSeconds") val remainingSeconds: Long? = null,
)

@Serializable
data class OkResponse(
    val ok: Boolean = false,
)
