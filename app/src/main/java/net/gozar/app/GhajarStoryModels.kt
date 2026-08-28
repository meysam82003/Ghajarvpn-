package net.gozar.app

import org.json.JSONObject
import java.net.URI

internal data class GhajarStoryAttachment(val url: String, val name: String, val size: Long)
internal data class GhajarStorySlide(
    val title: String, val body: String, val background: String,
    val media: String?, val video: Boolean, val x: Float, val y: Float, val scale: Float, val rotation: Float,
    val ctaLabel: String, val ctaLink: String, val ctaColor: String,
    val code: String, val codeKind: String, val attachments: List<GhajarStoryAttachment>
)
internal data class GhajarStory(val id: String, val icon: String, val iconMedia: String?, val label: String,
    val sublabel: String, val tag: String, val tagColor: String, val slides: List<GhajarStorySlide>)
internal data class GhajarStoryRoute(val section: Int, val discountCode: String? = null, val giftCode: String? = null)

internal object GhajarStoryRules {
    private val base = URI(BrandConfig.STORE_URL)

    /** The bot emits flat relative public media paths, never authenticated API URLs. */
    fun mediaUrl(value: String): String? = runCatching {
        if (value.isBlank()) return null
        val uri = base.resolve(value.trim()).normalize()
        if (uri.scheme != "https" || !uri.host.equals(base.host, true) || uri.port !in listOf(-1, 443) ||
            uri.userInfo != null || uri.fragment != null) return null
        val prefix = base.path.trimEnd('/') + "/assets/story-media/"
        if (!uri.path.startsWith(prefix)) return null
        val filename = uri.path.removePrefix(prefix)
        if (!filename.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9._-]{0,199}")) || ".." in filename) return null
        uri.toASCIIString()
    }.getOrNull()

    fun externalLink(value: String): String? = runCatching {
        val uri = URI(value.trim())
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.port !in listOf(-1, 443)) null
        else uri.toASCIIString()
    }.getOrNull()

    fun route(value: String): GhajarStoryRoute? = when (value.trim().substringBefore('?').trimEnd('/')) {
        "#/recharge", "#/wallet" -> GhajarStoryRoute(3)
        "#/buy", "#/shop" -> GhajarStoryRoute(0)
        "#/services" -> GhajarStoryRoute(1)
        else -> null
    }

    fun parse(payload: JSONObject): List<GhajarStory> {
        val items = payload.optJSONArray("items") ?: return emptyList()
        return (0 until minOf(items.length(), 60)).mapNotNull { index ->
            val story = items.optJSONObject(index) ?: return@mapNotNull null
            val id = story.optString("id").take(120)
            val slides = story.optJSONArray("slides") ?: return@mapNotNull null
            if (id.isBlank()) return@mapNotNull null
            val parsed = (0 until minOf(slides.length(), 8)).mapNotNull slide@{ i ->
                val slide = slides.optJSONObject(i) ?: return@slide null
                val attachments = mutableListOf<GhajarStoryAttachment>()
                val array = slide.optJSONArray("attaches")
                if (array != null) for (j in 0 until minOf(array.length(), 4)) {
                    val attachment = array.optJSONObject(j) ?: continue
                    val url = mediaUrl(attachment.optString("url")) ?: continue
                    attachments += GhajarStoryAttachment(url, clean(attachment.optString("name"), 120).ifBlank { "فایل ضمیمه" },
                        attachment.optLong("size").coerceAtLeast(0))
                }
                if (attachments.isEmpty()) mediaUrl(slide.optString("attach"))?.let {
                    attachments += GhajarStoryAttachment(it, clean(slide.optString("attach_name"), 120).ifBlank { "فایل ضمیمه" },
                        slide.optLong("attach_size").coerceAtLeast(0))
                }
                fun number(key: String, default: Float, min: Float, max: Float): Float =
                    slide.optDouble(key, default.toDouble()).toFloat().takeIf { it.isFinite() }?.coerceIn(min, max) ?: default
                GhajarStorySlide(clean(slide.optString("title"), 200), clean(slide.optString("body"), 4000),
                    GhajarColorRules.normalize(slide.optString("bg")) ?: "#082F2B",
                    mediaUrl(slide.optString("media")), slide.optString("media_type") == "video",
                    number("media_x", 0f, -50f, 50f), number("media_y", 0f, -50f, 50f),
                    number("media_scale", 1f, .5f, 3f), number("media_rotate", 0f, -180f, 180f),
                    clean(slide.optString("cta_label"), 120), slide.optString("cta_link").take(2000),
                    GhajarColorRules.normalize(slide.optString("cta_color")) ?: "#C79E48",
                    slide.optString("code").trim().take(200), slide.optString("code_kind").takeIf { it in listOf("gift", "discount") }.orEmpty(),
                    attachments)
            }
            if (parsed.isEmpty()) null else GhajarStory(id, clean(story.optString("icon"), 16).ifBlank { "🎁" },
                mediaUrl(story.optString("icon_media")), clean(story.optString("label"), 80),
                clean(story.optString("sublabel"), 120), clean(story.optString("tag"), 30),
                GhajarColorRules.normalize(story.optString("tag_color")) ?: "#C79E48", parsed)
        }.distinctBy { it.id }
    }

    private fun clean(value: String, limit: Int): String = BrandConfig.sanitizePublicText(value)
        .replace(Regex("<[^>]*>"), "").take(limit)
}
