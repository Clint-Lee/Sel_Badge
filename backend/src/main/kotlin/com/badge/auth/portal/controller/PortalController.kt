package com.badge.auth.portal.controller

import com.badge.auth.common.ApiResponse
import com.badge.auth.portal.dto.*
import com.badge.auth.portal.service.PortalService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/** 公共目录只读；评价、申请、偏好绑定 Badge 账号，审核仅管理员可用。 */
@RestController
@RequestMapping("/api/portal")
class PortalController(private val service: PortalService) {
    @GetMapping("/tools")
    fun tools(): ApiResponse<List<Tool>> = ApiResponse.success(service.tools())

    @GetMapping("/tools/{id}/reviews")
    fun reviews(@PathVariable id: String): ApiResponse<List<Review>> = ApiResponse.success(service.reviews(id))

    @PostMapping("/tools/{id}/reviews")
    fun review(@PathVariable id: String, @Valid @RequestBody request: ReviewRequest, auth: Authentication): ApiResponse<Review> =
        ApiResponse.success(service.review(auth.name, id, request))

    @PostMapping("/submissions")
    fun submit(@Valid @RequestBody request: ToolSubmission, auth: Authentication): ApiResponse<Submission> =
        ApiResponse.success(service.submit(auth.name, request), "收录申请已提交")

    @GetMapping("/submissions")
    fun submissions(auth: Authentication): ApiResponse<List<Submission>> =
        ApiResponse.success(service.submissions(auth.name, auth.authorities.any { it.authority == "ROLE_ADMIN" }))

    @PutMapping("/submissions/{id}/approval")
    @PreAuthorize("hasRole('ADMIN')")
    fun approve(@PathVariable id: String): ApiResponse<Submission> = ApiResponse.success(service.moderate(id, true))

    @PutMapping("/submissions/{id}/rejection")
    @PreAuthorize("hasRole('ADMIN')")
    fun reject(@PathVariable id: String): ApiResponse<Submission> = ApiResponse.success(service.moderate(id, false))

    @GetMapping("/preferences")
    fun preferences(auth: Authentication): ApiResponse<Preferences> = ApiResponse.success(service.preferences(auth.name))

    @PutMapping("/preferences")
    fun preferences(@Valid @RequestBody request: Preferences, auth: Authentication): ApiResponse<Preferences> =
        ApiResponse.success(service.preferences(auth.name, request))
}
