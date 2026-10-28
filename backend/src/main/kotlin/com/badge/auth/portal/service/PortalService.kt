package com.badge.auth.portal.service

import com.badge.auth.portal.dto.*
import com.badge.auth.portal.exception.PortalErrors
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Instant
import java.util.UUID

/** 工具收录、评价、审核及跨设备偏好；所有写操作以完整快照原子提交。 */
@Service
class PortalService(private val store: PortalFileStore) {
    companion object {
        private const val CATALOG_FILE = "catalog.json"
        val CATEGORIES = setOf("assistant", "image", "video", "coding", "audio", "agent", "api", "learning")
    }

    private var state = store.read(CATALOG_FILE, PortalState::class.java) {
        ClassPathResource("portal/tools.json").inputStream.use { store.mapper.readValue(it, PortalState::class.java) }
    }

    @Synchronized fun tools(): List<Tool> = state.tools

    @Synchronized fun reviews(toolId: String): List<Review> {
        requireTool(toolId)
        return state.reviews.filter { it.toolId == toolId }.sortedByDescending { it.createdAt }
    }

    @Synchronized fun submit(username: String, request: ToolSubmission): Submission {
        val url = validateUrl(request.url)
        if (request.category !in CATEGORIES) PortalErrors.invalid("请选择有效分类")
        if (request.tags.any { it.isBlank() || it.length > 30 }) PortalErrors.invalid("每个标签须为 1–30 个字符")
        if (state.tools.any { it.url == url } || state.submissions.any { it.tool.url == url && it.status == "PENDING" }) {
            PortalErrors.conflict("该网站已收录或正在审核")
        }
        val now = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        val submission = Submission(id, Tool(id, request.name, url, request.category, request.description, request.tags.distinct(), createdAt = now), username, createdAt = now)
        commit(state.copy(submissions = state.submissions + submission))
        return submission
    }

    @Synchronized fun submissions(username: String, admin: Boolean): List<Submission> =
        state.submissions.filter { admin || it.username == username }.sortedByDescending { it.createdAt }

    @Synchronized fun moderate(id: String, approve: Boolean): Submission {
        val submission = state.submissions.find { it.id == id } ?: PortalErrors.missing()
        if (submission.status != "PENDING") PortalErrors.conflict("此申请已经处理")
        if (approve && state.tools.any { it.url == submission.tool.url }) PortalErrors.conflict("该网站已收录")
        val updated = submission.copy(status = if (approve) "APPROVED" else "REJECTED")
        commit(state.copy(
            tools = if (approve) state.tools + submission.tool else state.tools,
            submissions = state.submissions.map { if (it.id == id) updated else it }
        ))
        return updated
    }

    @Synchronized fun review(username: String, toolId: String, request: ReviewRequest): Review {
        requireTool(toolId)
        if (state.reviews.any { it.username == username && it.toolId == toolId }) PortalErrors.conflict("你已经评价过这个工具")
        val review = Review(UUID.randomUUID().toString(), toolId, username, request.rating, request.content, Instant.now().toString())
        commit(state.copy(reviews = state.reviews + review))
        return review
    }

    @Synchronized fun preferences(username: String): Preferences = store.read(store.preferenceFile(username), Preferences::class.java) { Preferences() }

    @Synchronized fun preferences(username: String, preferences: Preferences): Preferences {
        val ids = preferences.toolFavorites + preferences.productFavorites
        if (ids.any { it.isBlank() || it.length > 200 } || preferences.blockedUrls.any { it.isBlank() || it.length > 2000 } ||
            preferences.blockedKeywords.any { it.isBlank() || it.length > 100 }) {
            PortalErrors.invalid("偏好内容无效：收藏标识、链接或关键词超出长度限制")
        }
        store.write(store.preferenceFile(username), preferences)
        return preferences
    }

    private fun requireTool(id: String) { if (state.tools.none { it.id == id }) PortalErrors.missing() }

    private fun commit(updated: PortalState) { store.write(CATALOG_FILE, updated); state = updated }

    fun validateUrl(value: String): String {
        val uri = runCatching { URI(value) }.getOrElse { PortalErrors.invalid("请输入完整的 http 或 https 网址") }
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) {
            PortalErrors.invalid("请输入完整的 http 或 https 网址，且不要包含账号密码")
        }
        return uri.toString().removeSuffix("/")
    }
}
