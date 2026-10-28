package com.badge.auth.portal.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 门户目录数据独立于身份库；不存放子应用业务或会话。 */
data class Tool(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val category: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val featured: Boolean = false,
    val createdAt: String = "",
    val visits: Long = 0
)

data class ToolSubmission(
    @field:NotBlank @field:Size(max = 80) val name: String = "",
    @field:NotBlank @field:Size(max = 1000) val url: String = "",
    @field:NotBlank @field:Size(max = 40) val category: String = "",
    @field:NotBlank @field:Size(min = 10, max = 1000) val description: String = "",
    @field:Size(max = 10) val tags: List<@Size(max = 30) String> = emptyList()
)

data class Submission(
    val id: String = "", val tool: Tool = Tool(), val username: String = "",
    val status: String = "PENDING", val createdAt: String = ""
)

data class ReviewRequest(
    @field:Min(1) @field:Max(5) val rating: Int = 5,
    @field:NotBlank @field:Size(min = 5, max = 1000) val content: String = ""
)

data class Review(
    val id: String = "", val toolId: String = "", val username: String = "",
    val rating: Int = 5, val content: String = "", val createdAt: String = ""
)

data class PortalState(
    val tools: List<Tool> = emptyList(), val submissions: List<Submission> = emptyList(),
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
    val attemptedAt: String? = null, val offers: Int = 0, val message: String? = null
)

data class PriceSnapshot(
    val offers: List<Offer> = emptyList(), val sources: List<SourceStatus> = emptyList(),
    val intervalMinutes: Int = 15
)
