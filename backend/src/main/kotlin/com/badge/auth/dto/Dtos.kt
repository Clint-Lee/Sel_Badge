package com.badge.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class LoginRequest(
    @field:NotBlank(message = "用户名不能为空")
    val username: String,

    @field:NotBlank(message = "密码不能为空")
    val password: String,

    val clientId: String? = null
)

data class RefreshRequest(
    @field:NotBlank(message = "refreshToken 不能为空")
    val refreshToken: String
)

data class RegisterRequest(
    @field:NotBlank(message = "用户名不能为空")
    @field:Size(min = 3, max = 32, message = "用户名长度需在 3-32 个字符之间")
    val username: String,

    @field:NotBlank(message = "邮箱不能为空")
    @field:Email(message = "邮箱格式不正确")
    val email: String,

    @field:NotBlank(message = "密码不能为空")
    @field:Size(min = 8, max = 64, message = "密码长度需在 8-64 个字符之间")
    val password: String,

    val phone: String? = null
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "当前密码不能为空")
    val oldPassword: String,

    @field:NotBlank(message = "新密码不能为空")
    @field:Size(min = 8, max = 64, message = "新密码长度需在 8-64 个字符之间")
    val newPassword: String
)

data class UpdateProfileRequest(
    @field:Email(message = "Invalid email format")
    val email: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null
)

data class UserDto(
    val id: Long,
    val username: String,
    val email: String,
    val phoneMasked: String?,
    val avatarUrl: String?,
    val status: String,
    val role: String,
    val createdAt: LocalDateTime
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 7200,
    val user: UserDto
)

data class AppDto(
    val id: Long,
    val clientId: String,
    val appName: String,
    val appCategory: String,
    val homepageUrl: String,
    val redirectUri: String,
    val description: String?,
    val iconType: String,
    val status: String,
    // 仅在管理员创建应用的响应中出现一次（落库即密文）；其余查询恒为 null。
    val clientSecret: String? = null,
    val isBound: Boolean = false,
    val boundNickname: String? = null,
    val boundRole: String? = null,
    val appMetadata: String? = null,
    val lastLoginAt: LocalDateTime? = null
)

data class CreateAppRequest(
    @field:NotBlank(message = "Application name cannot be empty")
    val appName: String,

    @field:NotBlank(message = "Client ID cannot be empty")
    val clientId: String,

    val clientSecret: String? = null,

    val appCategory: String = "SERVICE",

    @field:NotBlank(message = "Homepage URL cannot be empty")
    val homepageUrl: String,

    @field:NotBlank(message = "Redirect URI cannot be empty")
    val redirectUri: String,

    val description: String? = null,
    val iconType: String = "globe"
)

data class UpdateAppProfileRequest(
    val appNickname: String?,
    val appMetadata: String? // Plain JSON string from client, backend will encrypt it
)

data class SsoAuthorizeRequest(
    @field:NotBlank(message = "Client ID is required")
    val clientId: String,
    val redirectUri: String? = null
)

data class SsoAuthorizeResponse(
    val ticket: String,
    val redirectUrl: String,
    val expiresInSeconds: Int = 300
)

data class SsoTokenExchangeRequest(
    @field:NotBlank(message = "Ticket is required")
    val ticket: String,

    @field:NotBlank(message = "Client ID is required")
    val clientId: String,

    @field:NotBlank(message = "Client Secret is required")
    val clientSecret: String
)

data class SsoTokenExchangeResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val user: UserInfoScopedDto
)

data class UserInfoScopedDto(
    val userId: Long,
    val username: String,
    val email: String,
    val appRole: String,
    val appNickname: String?,
    val appMetadata: String? // Decrypted for authorized client
)

data class AuditLogDto(
    val id: Long,
    val userId: Long?,
    val username: String?,
    val action: String,
    val clientId: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val status: String,
    val details: String?,
    val createdAt: LocalDateTime
)

data class SecurityStatusDto(
    val activeUsersCount: Long,
    val activeAppsCount: Long,
    val totalAuditLogs: Long,
    val failedAttemptsCount: Long,
    val encryptionAlgorithm: String = "AES-256-GCM (Authenticated Encryption)",
    val passwordHasher: String = "BCrypt (Work Factor 12)",
    val ssoProtocol: String = "OAuth2 Authorization Code / Short-lived SSO Ticket Flow",
    val systemStatus: String = "OPTIMAL_SECURED"
)
