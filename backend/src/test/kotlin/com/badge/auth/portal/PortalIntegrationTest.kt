package com.badge.auth.portal

import com.badge.auth.portal.dto.Preferences
import com.badge.auth.portal.dto.SiteSubmission
import com.badge.auth.portal.service.PortalFileStore
import com.badge.auth.portal.service.PortalService
import com.badge.auth.portal.service.PriceFeedService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Files
import java.util.UUID

/** 隔离身份测试库与门户目录，验证真实 HTTP 权限、审核与持久化。 */
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:portal-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
    "spring.sql.init.mode=never",
    "badge.portal.sync-enabled=false"
])
@AutoConfigureMockMvc
class PortalIntegrationTest {
    companion object {
        private val directory = Files.createTempDirectory("badge-portal-test-")
        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) { registry.add("badge.portal.data-dir") { directory.toString() } }

        /** 与 PriceFeedService 一致的「规格不符」标题规则，用于核对卡片最低价。 */
        private val SPEC_MISMATCH = Regex(
            "muse|token\\s*成品号|资格号|只是资格|半价资格|非本商品|他站|引流|" +
                "补差价|定金|勿拍|联系客服|代拍|占位|测试商品",
            RegexOption.IGNORE_CASE
        )
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var store: PortalFileStore
    @Autowired private lateinit var feeds: PriceFeedService

    @Test
    fun publicDirectoryIsReadableWithoutLeakingPreferencesOrContacts() {
        mvc.perform(get("/api/portal/categories")).andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].id").exists())
        mvc.perform(get("/api/portal/sites")).andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].name").exists())
        mvc.perform(get("/api/portal/stats")).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.sites").isNumber)
        // 联系方式与偏好必须登录，且联系方式按站点单独读取。
        mvc.perform(get("/api/portal/preferences")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/portal/sites/not-a-site/contact")).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/portal/submissions").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun visitCounterAccumulatesWithoutChangingCatalog() {
        val site = PortalService(store).sites().first()
        val before = site.visits
        mvc.perform(post("/api/portal/sites/${site.id}/visit")).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.visits").value(before + 1))
            .andExpect(jsonPath("$.data.name").value(site.name))
        assertEquals(before + 1, PortalService(store).sites().first { it.id == site.id }.visits)
    }

    @Test
    fun preferencesAreIsolatedAndPersistOnDisk() {
        val preferences = Preferences(toolFavorites = setOf("deepseek"), blockedKeywords = setOf("广告测试"))
        mvc.perform(put("/api/portal/preferences").with(user("alice")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(preferences)))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.toolFavorites[0]").value("deepseek"))
        mvc.perform(get("/api/portal/preferences").with(user("bob"))).andExpect(status().isOk).andExpect(jsonPath("$.data.toolFavorites").isEmpty)
        assertEquals(preferences, PortalService(store).preferences("alice"))
        assertFalse(store.preferenceFile("../alice").contains(".."))
    }

    @Test
    fun submissionIsPrivateUntilAdministratorApprovesIt() {
        val suffix = UUID.randomUUID().toString()
        val data = SiteSubmission(
            name = "测试站点-$suffix",
            url = "https://example.com/$suffix",
            category = "coding-tools-$suffix".let { "ai" },
            description = "这是用于验证审核流程的真实测试申请内容",
            tags = listOf("开发"),
            kind = "website",
            qq = "12345678"
        )
        val result = mvc.perform(post("/api/portal/submissions").with(user("author")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(data)))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.site.hasContact").value(true)).andReturn()
        val id = mapper.readTree(result.response.contentAsByteArray).path("data").path("id").asText()
        assertFalse(PortalService(store).sites().any { it.id == id })
        // 联系方式只在登录后按站点读取，目录里只暴露 hasContact 标记。
        mvc.perform(get("/api/portal/sites/$id/contact")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/portal/sites/$id/contact").with(user("someone"))).andExpect(status().isNotFound)
        mvc.perform(get("/api/portal/submissions").with(user("unrelated"))).andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isEmpty)
        mvc.perform(put("/api/portal/submissions/$id/approval").with(user("author"))).andExpect(status().isForbidden)
        mvc.perform(put("/api/portal/submissions/$id/approval").with(user("admin").roles("ADMIN"))).andExpect(status().isOk)
        assertEquals(data.name, PortalService(store).sites().find { it.id == id }?.name)
        mvc.perform(get("/api/portal/sites/$id/contact").with(user("someone"))).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.qq").value("12345678"))
        mvc.perform(put("/api/portal/submissions/$id/rejection").with(user("admin").roles("ADMIN"))).andExpect(status().isConflict)
        mvc.perform(post("/api/portal/submissions").with(user("author")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(data)))
            .andExpect(status().isConflict)
    }

    @Test
    fun validatesLinksTagsAndRelayOfferBounds() {
        val invalidUrl = SiteSubmission(
            name = "危险链接", url = "javascript:alert(1)", category = "ai",
            description = "这是一个验证危险网址被正确拒绝的申请内容"
        )
        mvc.perform(post("/api/portal/submissions").with(user("reviewer")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(invalidUrl)))
            .andExpect(status().isBadRequest)

        val tooManyTags = SiteSubmission(
            name = "标签过多", url = "https://example.com/tags-${UUID.randomUUID()}", category = "ai",
            description = "这是一个验证标签数量上限的申请内容",
            tags = (1..11).map { "tag$it" }
        )
        mvc.perform(post("/api/portal/submissions").with(user("reviewer")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(tooManyTags)))
            .andExpect(status().isBadRequest)

        val merchantWithoutContact = SiteSubmission(
            name = "商家无联系方式", url = "https://example.com/merchant-${UUID.randomUUID()}", category = "ai",
            description = "这是一个验证商家自荐必须提供联系方式的申请", kind = "merchant"
        )
        mvc.perform(post("/api/portal/submissions").with(user("reviewer")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(merchantWithoutContact)))
            .andExpect(status().isBadRequest)

        val review = """{"rating":5,"content":"使用体验不错，适合开发工作。"}"""
        val siteId = PortalService(store).sites().first().id
        mvc.perform(post("/api/portal/sites/$siteId/reviews").with(user("reviewer")).contentType(MediaType.APPLICATION_JSON).content(review)).andExpect(status().isOk)
        mvc.perform(post("/api/portal/sites/$siteId/reviews").with(user("reviewer")).contentType(MediaType.APPLICATION_JSON).content(review)).andExpect(status().isConflict)
        mvc.perform(post("/api/portal/sites/$siteId/reviews").with(user("other-reviewer")).contentType(MediaType.APPLICATION_JSON).content("""{"rating":6,"content":"超过五星的无效评价。"}""")).andExpect(status().isBadRequest)
        mvc.perform(put("/api/portal/preferences").with(user("other-reviewer")).contentType(MediaType.APPLICATION_JSON).content("""{"blockedKeywords":[" "]}""")).andExpect(status().isBadRequest)
    }

    @Test
    fun priceCatalogMergesSameSpecAcrossSourcesAndPagesInsideOneSpec() {
        val groups = feeds.groups(true)
        assertTrue(groups.isNotEmpty(), "种子快照应包含标准规格")
        // 同一标准规格跨来源合并为一张卡：分组标识不再是来源前缀，而是规格本身。
        assertTrue(groups.none { it.id.contains(":") }, "分组标识应为标准规格，而不是来源前缀")
        assertEquals(groups.size, groups.map { it.id }.toSet().size, "分组标识不得重复")

        // 合并后的规格能同时列出两个来源的报价。
        val merged = groups.first { it.sources.size > 1 }
        assertTrue(merged.sources.contains("pickai") && merged.sources.contains("priceai"))

        // 卡片上的最低价必须等于该规格内可报价的最低一条（排除售罄、零价与规格不符标题）。
        val quoted = feeds.snapshot().offers.filter { normalize(it.product) == merged.id }
            .filter { it.available != false && it.price > 0 }
            .filter { !SPEC_MISMATCH.containsMatchIn(it.title) }
        assertEquals(quoted.minOf { it.price }, merged.lowestPrices.first().price, "卡片最低价应等于可报价的最小值")

        // 报价分页只返回本规格，且「仅明确有库存」不会带出售罄或未知库存。
        val page = feeds.offers(merged.id, 1, true, null, false, null)
        assertTrue(page.items.isNotEmpty() && page.total > 0)
        assertTrue(page.items.all { normalize(it.product) == merged.id }, "分页结果必须属于同一规格")
        val inStock = feeds.offers(merged.id, 1, true, null, true, null)
        assertTrue(inStock.items.all { it.available == true }, "仅明确有库存时不应出现售罄或未知库存")

        // 页码越界自动回到最后一页。
        val outOfRange = feeds.offers(merged.id, 9_999, true, null, false, null)
        assertEquals(maxOf(1, (outOfRange.total + outOfRange.pageSize - 1) / outOfRange.pageSize), outOfRange.page)
    }

    private fun normalize(value: String) =
        value.lowercase().replace(Regex("[\\s/·、，,]+"), "").replace("／", "")
}
