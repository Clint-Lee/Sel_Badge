package com.badge.auth.portal

import com.badge.auth.portal.dto.Preferences
import com.badge.auth.portal.service.PortalFileStore
import com.badge.auth.portal.service.PortalService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var store: PortalFileStore

    @Test
    fun publicCatalogDoesNotExposePreferences() {
        mvc.perform(get("/api/portal/tools")).andExpect(status().isOk).andExpect(jsonPath("$.data[0].name").exists())
        mvc.perform(get("/api/portal/prices")).andExpect(status().isOk).andExpect(jsonPath("$.data.sources[0].source").exists())
        mvc.perform(get("/api/portal/preferences")).andExpect(status().isForbidden)
        mvc.perform(post("/api/portal/submissions").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden)
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
        val data = mapOf("name" to "测试工具-$suffix", "url" to "https://example.com/$suffix", "category" to "coding", "description" to "这是用于验证审核流程的真实测试申请", "tags" to listOf("开发"))
        val result = mvc.perform(post("/api/portal/submissions").with(user("author")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(data)))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("PENDING")).andReturn()
        val id = mapper.readTree(result.response.contentAsByteArray).path("data").path("id").asText()
        assertFalse(PortalService(store).tools().any { it.id == id })
        mvc.perform(get("/api/portal/submissions").with(user("unrelated"))).andExpect(status().isOk).andExpect(jsonPath("$.data").isEmpty)
        mvc.perform(put("/api/portal/submissions/$id/approval").with(user("author"))).andExpect(status().isForbidden)
        mvc.perform(put("/api/portal/submissions/$id/approval").with(user("admin").roles("ADMIN"))).andExpect(status().isOk)
        assertEquals(data["name"], PortalService(store).tools().find { it.id == id }?.name)
        mvc.perform(put("/api/portal/submissions/$id/rejection").with(user("admin").roles("ADMIN"))).andExpect(status().isConflict)
        mvc.perform(post("/api/portal/submissions").with(user("author")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(data))).andExpect(status().isConflict)
    }

    @Test
    fun validatesLinksAndRejectsDuplicateReviews() {
        val invalid = mapOf("name" to "危险链接", "url" to "javascript:alert(1)", "category" to "coding", "description" to "这是一个验证危险网址被正确拒绝的申请")
        mvc.perform(post("/api/portal/submissions").with(user("reviewer")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(invalid))).andExpect(status().isBadRequest)
        val review = """{"rating":5,"content":"使用体验不错，适合开发工作。"}"""
        mvc.perform(post("/api/portal/tools/deepseek/reviews").with(user("reviewer")).contentType(MediaType.APPLICATION_JSON).content(review)).andExpect(status().isOk)
        mvc.perform(post("/api/portal/tools/deepseek/reviews").with(user("reviewer")).contentType(MediaType.APPLICATION_JSON).content(review)).andExpect(status().isConflict)
        mvc.perform(post("/api/portal/tools/deepseek/reviews").with(user("other-reviewer")).contentType(MediaType.APPLICATION_JSON).content("""{"rating":6,"content":"超过五星的无效评价。"}""")).andExpect(status().isBadRequest)
        mvc.perform(post("/api/portal/tools/deepseek/reviews").with(user("other-reviewer")).contentType(MediaType.APPLICATION_JSON).content("""{"rating":5,"content":"     "}""")).andExpect(status().isBadRequest)
        mvc.perform(put("/api/portal/preferences").with(user("other-reviewer")).contentType(MediaType.APPLICATION_JSON).content("""{"blockedKeywords":[" "]}""")).andExpect(status().isBadRequest)
    }
}
