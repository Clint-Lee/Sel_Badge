package com.badge.auth.portal.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 门户目录数据独立于身份库；不存放子应用业务或会话。 */

/** 导航分类；count 由服务端按当前站点数计算，不从上游照抄。 */
data class Category(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val count: Int = 0
)

/** 中转站赠金：公开金额与领取说明。 */
data class RelayOffer(
    val amountCents: Int = 0,
    val claimInstructions: String = ""
)

/** 收录站点（AI工具 / 资源导航条目）。 */
data class Site(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val description: String = "",
    val category: String = "ai",
    val tags: List<String> = emptyList(),
    val featured: Boolean = false,
    val visits: Long = 0,
    val todayClicks: Int = 0,
    val createdAt: String = "",
    val sponsorPinUntil: String? = null,
    val relayOffer: RelayOffer? = null,
    val hasContact: Boolean = false,
    /** 站点来源：directory（收录）/ catalog（内置精选）/ submission（用户投稿通过）。 */
    val source: String = "directory"
)

/** 站点联系方式仅在登录后按站点单独读取，不随目录批量下发。 */
data class SiteContact(
    val siteId: String = "",
    val qq: String = "",
    val wechat: String = "",
    val telegram: String = "",
    val email: String = "",
    val note: String = ""
)

/** 联系方式落盘形态：用包装类型保住 Map 的值类型。 */
data class ContactBook(val contacts: Map<String, SiteContact> = emptyMap())

/** 站点点击计数落盘形态。 */
data class SiteHit(val visits: Long = 0, val todayClicks: Int = 0, val day: String = "")

data class HitBook(val counters: Map<String, SiteHit> = emptyMap())

data class SiteSubmission(
    @field:NotBlank @field:Size(max = 80) val name: String = "",
    @field:NotBlank @field:Size(max = 1000) val url: String = "",
    @field:NotBlank @field:Size(max = 40) val category: String = "",
    @field:NotBlank @field:Size(min = 10, max = 2000) val description: String = "",
    @field:Size(max = 10) val tags: List<@Size(max = 30) String> = emptyList(),
    /** website（网站收录）/ merchant（商家自荐）/ qq-group（QQ群收录）。 */
    @field:Pattern(regexp = "website|merchant|qq-group") val kind: String = "website",
    @field:Size(max = 40) val qq: String = "",
    @field:Size(max = 40) val wechat: String = "",
    @field:Size(max = 40) val telegram: String = "",
    @field:Size(max = 120) val email: String = "",
    val relayOffer: RelayOfferInput? = null
)

data class RelayOfferInput(
    @field:Min(101) @field:Max(100_000_000) val amountCents: Int = 0,
    @field:Size(min = 1, max = 500) val claimInstructions: String = ""
)

data class Submission(
    val id: String = "",
    val site: Site = Site(),
    val username: String = "",
    val kind: String = "website",
    val status: String = "PENDING",
    val createdAt: String = ""
)

data class ReviewRequest(
    @field:Min(1) @field:Max(5) val rating: Int = 5,
    @field:NotBlank @field:Size(min = 5, max = 1000) val content: String = ""
)

data class Review(
    val id: String = "",
    val siteId: String = "",
    val username: String = "",
    val rating: Int = 5,
    val content: String = "",
    val createdAt: String = ""
)

/** 门户落盘状态：站点、申请、评价。联系方式单独存 contacts.json。 */
data class DirectoryState(
    val updatedAt: String = "",
    val categories: List<Category> = emptyList(),
    val sites: List<Site> = emptyList(),
    val submissions: List<Submission> = emptyList(),
    val reviews: List<Review> = emptyList()
)

data class Preferences(
    @field:Size(max = 1000) val toolFavorites: Set<@Size(max = 200) String> = emptySet(),
    @field:Size(max = 1000) val productFavorites: Set<@Size(max = 200) String> = emptySet(),
    @field:Size(max = 500) val blockedUrls: Set<@Size(max = 2000) String> = emptySet(),
    @field:Size(max = 100) val blockedKeywords: Set<@Size(min = 1, max = 100) String> = emptySet()
)

/** 同一来源的标准商品 ID 才视为可比规格，不靠相似标题合并。 */
data class Offer(
    val id: String = "", val groupId: String = "", val product: String = "",
    val category: String = "", val productType: String = "unknown", val billingPeriod: String = "unknown",
    val title: String = "", val shopName: String = "", val url: String = "",
    val price: Double = 0.0, val currency: String = "CNY", val stock: Int? = null,
    val available: Boolean? = null, val minOrderQuantity: Int? = null,
    val source: String = "", val capturedAt: String? = null, val syncedAt: String = "",
    val suspicious: Boolean = false
)

data class SourceStatus(
    val source: String = "", val status: String = "waiting", val lastSuccessAt: String? = null,
    val attemptedAt: String? = null, val offers: Int = 0, val message: String? = null,
    val details: Map<String, Any> = emptyMap()
)

data class PriceSnapshot(
    val offers: List<Offer> = emptyList(), val sources: List<SourceStatus> = emptyList(),
    val intervalMinutes: Int = 15, val updatedAt: String? = null, val totalLinks: Int = 0
)

/** 选品台一行：一个来源标准规格及其可比最低价。 */
data class PriceGroup(
    val id: String = "",
    val product: String = "",
    val category: String = "",
    val sources: List<String> = emptyList(),
    val offers: Int = 0,
    val lowestPrices: List<CurrencyPrice> = emptyList(),
    /** 规格内出现最多的形态 / 周期，用于列表筛选标注。 */
    val productType: String = "unknown",
    val billingPeriod: String = "unknown"
)

data class CurrencyPrice(val currency: String = "CNY", val price: Double = 0.0)

data class PriceCatalog(
    val groups: List<PriceGroup> = emptyList(),
    val totalLinks: Int = 0,
    val sources: List<SourceStatus> = emptyList(),
    val intervalMinutes: Int = 15,
    val qualityFilter: Boolean = true,
    val updatedAt: String? = null,
    val queries: Map<String, Long> = emptyMap()
)

data class OfferPage(
    val items: List<Offer> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20
)

data class QueryCount(val group: String = "", val count: Long = 0)

/** 低价库查询计数落盘形态。 */
data class QueryCounters(val counts: Map<String, Long> = emptyMap())
