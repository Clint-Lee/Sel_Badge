package com.badge.auth.portal.controller

import com.badge.auth.common.ApiResponse
import com.badge.auth.portal.dto.*
import com.badge.auth.portal.service.PortalService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * 公共目录只读；评价、申请、偏好、联系方式绑定 Badge 账号，审核仅管理员可用。
 * 全部路径位于 api/portal 前缀下，前端可在生产环境整体反代到同一后端。
 */
@RestController
@RequestMapping("/api/portal")
class PortalController(private val service: PortalService) {

    @GetMapping("/categories")
    fun categories(): ApiResponse<List<Category>> = ApiResponse.success(service.categories())

    @GetMapping("/sites")
    fun sites(): ApiResponse<List<Site>> = ApiResponse.success(service.sites())

    @GetMapping("/sites/{id}")
    fun site(@PathVariable id: String): ApiResponse<Site> = ApiResponse.success(service.site(id))

    @GetMapping("/sites/{id}/contact")
    fun contact(@PathVariable id: String): ApiResponse<SiteContact> = ApiResponse.success(service.contact(id))

    @PostMapping("/sites/{id}/visit")
    fun visit(@PathVariable id: String): ApiResponse<Site> = ApiResponse.success(service.visit(id))

    @GetMapping("/sites/{id}/reviews")
    fun reviews(@PathVariable id: String): ApiResponse<List<Review>> = ApiResponse.success(service.reviews(id))

    @PostMapping("/sites/{id}/reviews")
    fun review(@PathVariable id: String, @Valid @RequestBody request: ReviewRequest, auth: Authentication): ApiResponse<Review> =
        ApiResponse.success(service.review(auth.name, id, request), "评价已发布")

    @PostMapping("/submissions")
    fun submit(@Valid @RequestBody request: SiteSubmission, auth: Authentication): ApiResponse<Submission> =
        ApiResponse.success(service.submit(auth.name, request), "收录申请已提交")

    @GetMapping("/submissions")
    fun submissions(auth: Authentication): ApiResponse<List<Submission>> =
        ApiResponse.success(service.submissions(auth.name, auth.authorities.any { it.authority == "ROLE_ADMIN" }))

    @PutMapping("/submissions/{id}/approval")
    @PreAuthorize("hasRole('ADMIN')")
    fun approve(@PathVariable id: String): ApiResponse<Submission> = ApiResponse.success(service.moderate(id, true), "已收录")

    @PutMapping("/submissions/{id}/rejection")
    @PreAuthorize("hasRole('ADMIN')")
    fun reject(@PathVariable id: String): ApiResponse<Submission> = ApiResponse.success(service.moderate(id, false), "已处理")

    @GetMapping("/preferences")
    fun preferences(auth: Authentication): ApiResponse<Preferences> = ApiResponse.success(service.preferences(auth.name))

    @PutMapping("/preferences")
    fun savePreferences(@Valid @RequestBody request: Preferences, auth: Authentication): ApiResponse<Preferences> =
        ApiResponse.success(service.preferences(auth.name, request))

    @GetMapping("/stats")
    fun stats(): ApiResponse<Map<String, Any>> = ApiResponse.success(service.stats())
}
