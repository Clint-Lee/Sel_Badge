package com.badge.auth.controller

import com.badge.auth.common.ApiResponse
import com.badge.auth.dto.*
import com.badge.auth.service.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        servletRequest: HttpServletRequest
    ): ApiResponse<AuthResponse> {
        val ip = getClientIp(servletRequest)
        val userAgent = servletRequest.getHeader("User-Agent")
        val response = authService.login(request, ip, userAgent)
        return ApiResponse.success(response, "Login successful")
    }

    /** 用 refresh token 换发新的会话令牌；refresh 令牌本身不能访问任何其它接口。 */
    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
        servletRequest: HttpServletRequest
    ): ApiResponse<AuthResponse> {
        val response = authService.refresh(request, getClientIp(servletRequest))
        return ApiResponse.success(response, "Token refreshed")
    }

    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        servletRequest: HttpServletRequest
    ): ApiResponse<AuthResponse> {
        val ip = getClientIp(servletRequest)
        val userAgent = servletRequest.getHeader("User-Agent")
        val response = authService.register(request, ip, userAgent)
        return ApiResponse.success(response, "Registration successful")
    }

    @GetMapping("/me")
    fun me(authentication: Authentication): ApiResponse<UserDto> {
        val userDto = authService.getCurrentUser(authentication.name)
        return ApiResponse.success(userDto)
    }

    @PostMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        authentication: Authentication,
        servletRequest: HttpServletRequest
    ): ApiResponse<String> {
        val ip = getClientIp(servletRequest)
        val userAgent = servletRequest.getHeader("User-Agent")
        authService.changePassword(authentication.name, request, ip, userAgent)
        return ApiResponse.success("Password changed successfully")
    }

    @PutMapping("/profile")
    fun updateProfile(
        @Valid @RequestBody request: UpdateProfileRequest,
        authentication: Authentication,
        servletRequest: HttpServletRequest
    ): ApiResponse<UserDto> {
        val ip = getClientIp(servletRequest)
        val userAgent = servletRequest.getHeader("User-Agent")
        val updated = authService.updateProfile(authentication.name, request, ip, userAgent)
        return ApiResponse.success(updated, "Profile updated successfully")
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val xf = request.getHeader("X-Forwarded-For")
        return if (xf.isNullOrBlank()) request.remoteAddr else xf.split(",")[0].trim()
    }
}

@RestController
@RequestMapping("/api/apps")
class AppController(
    private val appService: AppService
) {

    @GetMapping
    fun listApps(authentication: Authentication?): ApiResponse<List<AppDto>> {
        val apps = appService.listAppsForUser(authentication?.name)
        return ApiResponse.success(apps)
    }

    @GetMapping("/public")
    fun listPublicApps(): ApiResponse<List<AppDto>> {
        val apps = appService.listAppsForUser(null)
        return ApiResponse.success(apps)
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    fun createApp(
        @Valid @RequestBody request: CreateAppRequest
    ): ApiResponse<AppDto> {
        val created = appService.createApp(request)
        return ApiResponse.success(created, "Application registered successfully")
    }
}

@RestController
@RequestMapping("/api/profiles")
class UserAppProfileController(
    private val userAppProfileService: UserAppProfileService
) {

    @PutMapping("/apps/{clientId}")
    fun updateAppProfile(
        @PathVariable clientId: String,
        @RequestBody request: UpdateAppProfileRequest,
        authentication: Authentication
    ): ApiResponse<AppDto> {
        val updated = userAppProfileService.updateAppProfile(authentication.name, clientId, request)
        return ApiResponse.success(updated, "App profile updated")
    }
}

@RestController
@RequestMapping("/api/sso")
class SsoController(
    private val ssoService: SsoService
) {

    @PostMapping("/authorize")
    fun authorize(
        @Valid @RequestBody request: SsoAuthorizeRequest,
        authentication: Authentication,
        servletRequest: HttpServletRequest
    ): ApiResponse<SsoAuthorizeResponse> {
        val ip = servletRequest.getHeader("X-Forwarded-For") ?: servletRequest.remoteAddr
        val userAgent = servletRequest.getHeader("User-Agent")
        val response = ssoService.createAuthorizeTicket(authentication.name, request, ip, userAgent)
        return ApiResponse.success(response, "SSO ticket generated")
    }

    @PostMapping("/token")
    fun exchangeToken(
        @Valid @RequestBody request: SsoTokenExchangeRequest
    ): ApiResponse<SsoTokenExchangeResponse> {
        val response = ssoService.exchangeTicket(request)
        return ApiResponse.success(response, "Ticket exchanged successfully")
    }
}

@RestController
@RequestMapping("/api/audit")
class AuditLogController(
    private val auditLogService: AuditLogService
) {

    /** 审计日志含全体用户的 IP/UA，仅运营者（管理员）可查看。 */
    @GetMapping("/logs")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    fun getLogs(): ApiResponse<List<AuditLogDto>> {
        val logs = auditLogService.getRecentLogs()
        return ApiResponse.success(logs)
    }
}

@RestController
@RequestMapping("/api/security")
class SecurityStatusController(
    private val auditLogService: AuditLogService
) {

    /** 站点安全统计面向登录用户展示（此前匿名可读用户/应用数量，属于信息泄露）。 */
    @GetMapping("/status")
    fun getStatus(): ApiResponse<SecurityStatusDto> {
        val status = auditLogService.getSecurityStats()
        return ApiResponse.success(status)
    }
}
