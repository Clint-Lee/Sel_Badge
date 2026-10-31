package com.badge.auth.portal.service

import com.badge.auth.portal.dto.*
import com.badge.auth.portal.exception.PortalErrors
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * 资源导航目录：站点、分类、收录申请、联系方式、访问统计与评价。
 * 所有写操作以完整快照原子提交，单实例部署下由 @Synchronized 保证串行。
 */
@Service
class PortalService(private val store: PortalFileStore) {
    companion object {
        private const val CATALOG_FILE = "catalog.json"
        private const val CONTACT_FILE = "contacts.json"
        /** 站点计数（访问、今日点击）单独落盘，避免每次点击重写大目录。 */
        private const val COUNTER_FILE = "site-counters.json"
        private const val MAX_TAGS = 10
        private const val MAX_TAG_LENGTH = 30
    }

    private var state = store.read(CATALOG_FILE, DirectoryState::class.java) {
        ClassPathResource("portal/sites.json").inputStream.use { store.mapper.readValue(it, DirectoryState::class.java) }
    }
    private var contacts: Map<String, SiteContact> =
        store.read(CONTACT_FILE, ContactBook::class.java) { ContactBook() }.contacts
    private var counters: Map<String, SiteHit> =
        store.read(COUNTER_FILE, HitBook::class.java) { HitBook() }.counters

    // ---------------- 读取 ----------------

    @Synchronized fun categories(): List<Category> {
        val counts = state.sites.groupingBy { it.category }.eachCount()
        val declared = state.categories.map { it.copy(count = counts[it.id] ?: 0) }
        // 未登记但被申请使用的分类补一条，避免站点“消失”。
        val missing = counts.keys - declared.map { it.id }.toSet()
        return declared + missing.sorted().map { Category(it, it, "用户投稿分类", counts[it] ?: 0) }
    }

    @Synchronized fun sites(): List<Site> = state.sites.map { withCounters(it) }

    @Synchronized fun site(id: String): Site = withCounters(state.sites.find { it.id == id } ?: PortalErrors.missing())

    @Synchronized fun contact(id: String): SiteContact {
        val site = state.sites.find { it.id == id } ?: PortalErrors.missing()
        if (!site.hasContact) PortalErrors.missing()
        return contacts[id] ?: PortalErrors.missing()
    }

    @Synchronized fun reviews(siteId: String): List<Review> {
        requireSite(siteId)
        return state.reviews.filter { it.siteId == siteId }.sortedByDescending { it.createdAt }
    }

    /** 访问计数：只累计已收录站点的点击，不改变目录内容。 */
    @Synchronized fun visit(id: String): Site {
        val site = state.sites.find { it.id == id } ?: PortalErrors.missing()
        val today = Instant.now().toString().take(10)
        val previous = counters[id] ?: SiteHit()
        val next = SiteHit(
            visits = previous.visits + 1,
            todayClicks = if (previous.day == today) previous.todayClicks + 1 else 1,
            day = today
        )
        counters = counters + (id to next)
        runCatching { store.write(COUNTER_FILE, HitBook(counters)) }
        return withCounters(site)
    }

    // ---------------- 写入 ----------------

    @Synchronized fun submit(username: String, request: SiteSubmission): Submission {
        val url = validateUrl(request.url)
        val categoryIds = categories().map { it.id }.toSet()
        if (request.category !in categoryIds) PortalErrors.invalid("请选择有效分类")
        validateTags(request.tags)
        val duplicate = state.sites.any { it.url.equals(url, true) } ||
            state.submissions.any { it.site.url.equals(url, true) && it.status == "PENDING" }
        if (duplicate) PortalErrors.conflict("该网站已收录或正在审核")
        request.relayOffer?.let {
            if (it.amountCents < 101 || it.amountCents > 100_000_000) PortalErrors.invalid("赠金须大于 1 元且不超过 1000000 元")
            if (it.claimInstructions.isBlank() || it.claimInstructions.length > 500) PortalErrors.invalid("请填写 1–500 字的赠金领取说明")
        }
        if (request.kind == "merchant" && listOf(request.qq, request.wechat, request.telegram).all { it.isBlank() }) {
            PortalErrors.invalid("商家自荐请至少填写 QQ、微信或飞机中的一种联系方式")
        }
        if (request.category == "api-relays" && !request.qq.matches(Regex("[1-9][0-9]{4,11}"))) {
            PortalErrors.invalid("中转站须填写联系 QQ（5–12 位数字）")
        }
        val now = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        val description = if (request.kind == "qq-group" && request.qq.isNotBlank() && !request.description.contains(request.qq)) {
            "${request.description.trim()}\n群号：${request.qq.trim()}"
        } else request.description.trim()
        val site = Site(
            id = id,
            name = request.name.trim(),
            url = url,
            description = description,
            category = request.category,
            tags = request.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            featured = false,
            visits = 0,
            todayClicks = 0,
            createdAt = now,
            relayOffer = request.relayOffer?.let { RelayOffer(it.amountCents, it.claimInstructions.trim()) },
            hasContact = listOf(request.qq, request.wechat, request.telegram, request.email).any { it.isNotBlank() },
            source = "submission"
        )
        val submission = Submission(id, site, username, request.kind, "PENDING", now)
        val nextContacts = if (site.hasContact) {
            contacts + (id to SiteContact(id, request.qq.trim(), request.wechat.trim(), request.telegram.trim(), request.email.trim(), "收录申请提交"))
        } else contacts
        store.write(CONTACT_FILE, ContactBook(nextContacts))
        contacts = nextContacts
        commit(state.copy(submissions = state.submissions + submission))
        return submission
    }

    @Synchronized fun submissions(username: String, admin: Boolean): List<Submission> =
        state.submissions.filter { admin || it.username == username }.sortedByDescending { it.createdAt }

    @Synchronized fun moderate(id: String, approve: Boolean): Submission {
        val submission = state.submissions.find { it.id == id } ?: PortalErrors.missing()
        if (submission.status != "PENDING") PortalErrors.conflict("此申请已经处理")
        if (approve && state.sites.any { it.url.equals(submission.site.url, true) }) PortalErrors.conflict("该网站已收录")
        val updated = submission.copy(status = if (approve) "APPROVED" else "REJECTED")
        commit(state.copy(
            sites = if (approve) state.sites + submission.site else state.sites,
            submissions = state.submissions.map { if (it.id == id) updated else it }
        ))
        return updated
    }

    @Synchronized fun review(username: String, siteId: String, request: ReviewRequest): Review {
        requireSite(siteId)
        // 管理后台审核过的站点同样限制一人一条，避免刷分。
        if (state.reviews.any { it.username == username && it.siteId == siteId }) PortalErrors.conflict("你已经评价过这个站点")
        val review = Review(UUID.randomUUID().toString(), siteId, username, request.rating, request.content.trim(), Instant.now().toString())
        commit(state.copy(reviews = state.reviews + review))
        return review
    }

    @Synchronized fun preferences(username: String): Preferences =
        store.read(store.preferenceFile(username), Preferences::class.java) { Preferences() }

    @Synchronized fun preferences(username: String, preferences: Preferences): Preferences {
        val ids = preferences.toolFavorites + preferences.productFavorites
        if (ids.any { it.isBlank() || it.length > 200 } || preferences.blockedUrls.any { it.isBlank() || it.length > 2000 } ||
            preferences.blockedKeywords.any { it.isBlank() || it.length > 100 }) {
            PortalErrors.invalid("偏好内容无效：收藏标识、链接或关键词超出长度限制")
        }
        store.write(store.preferenceFile(username), preferences)
        return preferences
    }

    /** 收录统计：供首页与侧栏展示真实条目数。 */
    @Synchronized fun stats(): Map<String, Any> = mapOf(
        "sites" to state.sites.size,
        "categories" to categories().count { it.count > 0 },
        "reviews" to state.reviews.size,
        "submissions" to state.submissions.size,
        "updatedAt" to state.updatedAt
    )

    // ---------------- 内部 ----------------

    private fun withCounters(site: Site): Site {
        val hit = counters[site.id] ?: return site
        val today = Instant.now().toString().take(10)
        return site.copy(
            visits = site.visits + hit.visits,
            todayClicks = if (hit.day == today) site.todayClicks + hit.todayClicks else site.todayClicks
        )
    }

    private fun requireSite(id: String) {
        if (state.sites.none { it.id == id }) PortalErrors.missing()
    }

    private fun commit(updated: DirectoryState) {
        store.write(CATALOG_FILE, updated)
        state = updated
    }

    private fun validateTags(tags: List<String>) {
        val cleaned = tags.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.size > MAX_TAGS || cleaned.any { it.length > MAX_TAG_LENGTH }) {
            PortalErrors.invalid("最多 $MAX_TAGS 个标签，每个不超过 $MAX_TAG_LENGTH 个字符")
        }
    }

    fun validateUrl(value: String): String {
        val uri = runCatching { URI(value.trim()) }.getOrElse { PortalErrors.invalid("请输入完整的 http 或 https 网址") }
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) {
            PortalErrors.invalid("请输入完整的 http 或 https 网址，且不要包含账号密码")
        }
        return uri.toString().removeSuffix("/")
    }
}
