package com.badge.auth.service

import com.badge.auth.common.BusinessException
import com.badge.auth.dto.*
import com.badge.auth.entity.*
import com.badge.auth.repository.*
import com.badge.auth.security.CryptoUtils
import com.badge.auth.security.JwtTokenProvider
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val appClientRepository: AppClientRepository,
    private val userAppProfileRepository: UserAppProfileRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenProvider: JwtTokenProvider,
    private val auditLogService: AuditLogService
) {

    @Transactional
    fun login(request: LoginRequest, ip: String?, userAgent: String?): AuthResponse {
        val user = userRepository.findByUsername(request.username).orElse(null)

        if (user == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            auditLogService.log(
                userId = user?.id,
                username = request.username,
                action = "LOGIN_FAILED",
                clientId = request.clientId,
                ip = ip,
                userAgent = userAgent,
                status = "FAILED",
                details = "Authentication failed: invalid credentials"
            )
            throw BusinessException(401, "Invalid username or password")
        }

        if (user.status != "ACTIVE") {
            auditLogService.log(
                userId = user.id,
                username = user.username,
                action = "LOGIN_BLOCKED",
                clientId = request.clientId,
                ip = ip,
                userAgent = userAgent,
                status = "FAILED",
                details = "Account is ${user.status}"
            )
            throw BusinessException(403, "Account is disabled or locked")
        }

        // If login was initiated for a specific client app, update that app profile's lastLoginAt
        if (!request.clientId.isNullOrBlank()) {
            appClientRepository.findByClientId(request.clientId).ifPresent { app ->
                val profile = userAppProfileRepository.findByUserIdAndAppId(user.id!!, app.id!!)
                    .orElseGet {
                        UserAppProfile(
                            user = user,
                            app = app,
                            appNickname = user.username,
                            appRole = "MEMBER"
                        )
                    }
                profile.lastLoginAt = LocalDateTime.now()
                userAppProfileRepository.save(profile)
            }
        }

        val accessToken = tokenProvider.generateAccessToken(
            user.id!!,
            user.username,
            user.email,
            user.role
        )
        val refreshToken = tokenProvider.generateRefreshToken(user.id!!, user.username)

        auditLogService.log(
            userId = user.id,
            username = user.username,
            action = "LOGIN_SUCCESS",
            clientId = request.clientId,
            ip = ip,
            userAgent = userAgent,
            status = "SUCCESS",
            details = "Logged in successfully via JWT"
        )

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = toUserDto(user)
        )
    }

    @Transactional
    fun register(request: RegisterRequest, ip: String?, userAgent: String?): AuthResponse {
        if (userRepository.existsByUsername(request.username)) {
            throw BusinessException(400, "Username '${request.username}' is already taken")
        }
        if (userRepository.existsByEmail(request.email)) {
            throw BusinessException(400, "Email '${request.email}' is already registered")
        }

        val hashedPassword = passwordEncoder.encode(request.password)
        val user = User(
            username = request.username,
            email = request.email,
            phone = request.phone, // Automatically encrypted by AesGcmCryptoConverter
            passwordHash = hashedPassword,
            status = "ACTIVE",
            role = if (userRepository.count() == 0L) "ROLE_ADMIN" else "ROLE_USER",
            avatarUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=${request.username}"
        )
        val savedUser = userRepository.save(user)

        // Automatically bind to existing active public apps
        val apps = appClientRepository.findAllByStatus("ACTIVE")
        apps.forEach { app ->
            val profile = UserAppProfile(
                user = savedUser,
                app = app,
                appNickname = savedUser.username,
                appRole = if (savedUser.role == "ROLE_ADMIN") "ADMIN" else "MEMBER",
                appMetadata = "{ \"initialized\": true, \"joinTime\": \"${LocalDateTime.now()}\" }"
            )
            userAppProfileRepository.save(profile)
        }

        val accessToken = tokenProvider.generateAccessToken(
            savedUser.id!!,
            savedUser.username,
            savedUser.email,
            savedUser.role
        )
        val refreshToken = tokenProvider.generateRefreshToken(savedUser.id!!, savedUser.username)

        auditLogService.log(
            userId = savedUser.id,
            username = savedUser.username,
            action = "REGISTER",
            ip = ip,
            userAgent = userAgent,
            status = "SUCCESS",
            details = "New account registered"
        )

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = toUserDto(savedUser)
        )
    }

    @Transactional(readOnly = true)
    fun getCurrentUser(username: String): UserDto {
        val user = userRepository.findByUsername(username)
            .orElseThrow { BusinessException(404, "User not found") }
        return toUserDto(user)
    }

    /** 用 refresh token 换发新的会话令牌并轮换 refresh token；非 REFRESH 类型一律拒绝。 */
    fun refresh(request: RefreshRequest, ip: String?): AuthResponse {
        val claims = try {
            tokenProvider.getClaimsFromToken(request.refreshToken)
        } catch (_: Exception) {
            throw BusinessException(401, "登录状态已过期，请重新登录")
        }
        if (claims["tokenType"] != "REFRESH") {
            throw BusinessException(401, "令牌类型不正确，请使用 refresh token 换发")
        }
        val user = userRepository.findByUsername(claims.subject)
            .orElseThrow { BusinessException(401, "用户不存在或已注销") }
        if (user.status != "ACTIVE") {
            throw BusinessException(403, "Account is disabled or locked")
        }

        val accessToken = tokenProvider.generateAccessToken(user.id!!, user.username, user.email, user.role)
        val refreshToken = tokenProvider.generateRefreshToken(user.id!!, user.username)

        auditLogService.log(
            userId = user.id,
            username = user.username,
            action = "TOKEN_REFRESH",
            ip = ip,
            status = "SUCCESS",
            details = "Access token refreshed"
        )

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = toUserDto(user)
        )
    }

    @Transactional
    fun changePassword(username: String, request: ChangePasswordRequest, ip: String?, userAgent: String?) {
        val user = userRepository.findByUsername(username)
            .orElseThrow { BusinessException(404, "User not found") }

        if (!passwordEncoder.matches(request.oldPassword, user.passwordHash)) {
            throw BusinessException(400, "Current password is incorrect")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)

        auditLogService.log(
            userId = user.id,
            username = user.username,
            action = "PASSWORD_CHANGE",
            ip = ip,
            userAgent = userAgent,
            status = "SUCCESS",
            details = "Password updated successfully"
        )
    }

    @Transactional
    fun updateProfile(username: String, request: UpdateProfileRequest, ip: String?, userAgent: String?): UserDto {
        val user = userRepository.findByUsername(username)
            .orElseThrow { BusinessException(404, "User not found") }

        if (!request.email.isNullOrBlank()) {
            val trimmedEmail = request.email.trim()
            val existing = userRepository.findByEmail(trimmedEmail)
            if (existing.isPresent && existing.get().id != user.id) {
                throw BusinessException(400, "该邮箱已被其他账号占用")
            }
            user.email = trimmedEmail
        }

        if (request.phone != null) {
            user.phone = if (request.phone.isBlank()) null else request.phone.trim()
        }

        if (request.avatarUrl != null) {
            user.avatarUrl = if (request.avatarUrl.isBlank()) null else request.avatarUrl.trim()
        }

        val savedUser = userRepository.save(user)

        auditLogService.log(
            userId = savedUser.id,
            username = savedUser.username,
            action = "PROFILE_UPDATE",
            ip = ip,
            userAgent = userAgent,
            status = "SUCCESS",
            details = "Updated user basic profile"
        )

        return toUserDto(savedUser)
    }

    private fun toUserDto(user: User): UserDto {
        return UserDto(
            id = user.id!!,
            username = user.username,
            email = user.email,
            phoneMasked = CryptoUtils.maskPhone(user.phone),
            avatarUrl = user.avatarUrl,
            status = user.status,
            role = user.role,
            createdAt = user.createdAt
        )
    }
}

@Service
class AppService(
    private val appClientRepository: AppClientRepository,
    private val userAppProfileRepository: UserAppProfileRepository,
    private val userRepository: UserRepository
) {

    @Transactional(readOnly = true)
    fun listAppsForUser(username: String?): List<AppDto> {
        val apps = appClientRepository.findAll()
        val user = username?.let { userRepository.findByUsername(it).orElse(null) }

        val profilesMap = if (user != null) {
            userAppProfileRepository.findAllByUserIdWithApp(user.id!!)
                .associateBy { it.app!!.clientId }
        } else {
            emptyMap()
        }

        return apps.map { app ->
            val profile = profilesMap[app.clientId]
            AppDto(
                id = app.id!!,
                clientId = app.clientId,
                appName = app.appName,
                appCategory = app.appCategory,
                homepageUrl = app.homepageUrl,
                redirectUri = app.redirectUri,
                description = app.description,
                iconType = app.iconType,
                status = app.status,
                isBound = profile != null,
                boundNickname = profile?.appNickname,
                boundRole = profile?.appRole,
                appMetadata = profile?.appMetadata,
                lastLoginAt = profile?.lastLoginAt
            )
        }
    }

    @Transactional
    fun createApp(request: CreateAppRequest): AppDto {
        if (appClientRepository.existsByClientId(request.clientId)) {
            throw BusinessException(400, "Client ID '${request.clientId}' is already registered")
        }

        val rawSecret = if (request.clientSecret.isNullOrBlank()) {
            "sec_" + UUID.randomUUID().toString().replace("-", "")
        } else {
            request.clientSecret
        }

        val app = AppClient(
            clientId = request.clientId,
            clientSecret = rawSecret, // Automatically encrypted by AesGcmCryptoConverter in database
            appName = request.appName,
            appCategory = request.appCategory.uppercase(),
            homepageUrl = request.homepageUrl,
            redirectUri = request.redirectUri,
            description = request.description,
            iconType = request.iconType,
            status = "ACTIVE"
        )
        val saved = appClientRepository.save(app)

        // client secret 只在创建响应中出现这一次（落库即密文），错过后只能重置。
        return AppDto(
            id = saved.id!!,
            clientId = saved.clientId,
            appName = saved.appName,
            appCategory = saved.appCategory,
            homepageUrl = saved.homepageUrl,
            redirectUri = saved.redirectUri,
            description = saved.description,
            iconType = saved.iconType,
            status = saved.status,
            clientSecret = rawSecret
        )
    }
}

@Service
class UserAppProfileService(
    private val userAppProfileRepository: UserAppProfileRepository,
    private val userRepository: UserRepository,
    private val appClientRepository: AppClientRepository
) {

    @Transactional
    fun updateAppProfile(
        username: String,
        clientId: String,
        request: UpdateAppProfileRequest
    ): AppDto {
        val user = userRepository.findByUsername(username)
            .orElseThrow { BusinessException(404, "User not found") }
        val app = appClientRepository.findByClientId(clientId)
            .orElseThrow { BusinessException(404, "Application not found") }

        val profile = userAppProfileRepository.findByUserIdAndAppId(user.id!!, app.id!!)
            .orElseGet {
                UserAppProfile(
                    user = user,
                    app = app,
                    appNickname = user.username,
                    appRole = "MEMBER"
                )
            }

        if (request.appNickname != null) {
            profile.appNickname = request.appNickname
        }
        if (request.appMetadata != null) {
            profile.appMetadata = request.appMetadata // Automatically encrypted by converter
        }
        val savedProfile = userAppProfileRepository.save(profile)

        return AppDto(
            id = app.id!!,
            clientId = app.clientId,
            appName = app.appName,
            appCategory = app.appCategory,
            homepageUrl = app.homepageUrl,
            redirectUri = app.redirectUri,
            description = app.description,
            iconType = app.iconType,
            status = app.status,
            isBound = true,
            boundNickname = savedProfile.appNickname,
            boundRole = savedProfile.appRole,
            appMetadata = savedProfile.appMetadata,
            lastLoginAt = savedProfile.lastLoginAt
        )
    }
}

@Service
class SsoService(
    private val ssoTicketRepository: SsoTicketRepository,
    private val appClientRepository: AppClientRepository,
    private val userRepository: UserRepository,
    private val userAppProfileRepository: UserAppProfileRepository,
    private val tokenProvider: JwtTokenProvider,
    private val auditLogService: AuditLogService
) {

    @Transactional
    fun createAuthorizeTicket(
        username: String,
        request: SsoAuthorizeRequest,
        ip: String?,
        userAgent: String?
    ): SsoAuthorizeResponse {
        val user = userRepository.findByUsername(username)
            .orElseThrow { BusinessException(404, "User not found") }

        val app = appClientRepository.findByClientId(request.clientId)
            .orElseThrow { BusinessException(404, "App client '${request.clientId}' not found") }

        if (app.status != "ACTIVE") {
            throw BusinessException(403, "Application '${app.appName}' is currently under maintenance")
        }

        val targetRedirect = if (!request.redirectUri.isNullOrBlank()) {
            val requested = request.redirectUri.trim()
            // redirect_uri 是 SSO 最经典的安全边界：请求带来的回调地址必须与该应用
            // 登记的回调同源，否则 ticket 会被拼到任意站点上（开放重定向/票据泄露）。
            if (originOf(requested) == null || originOf(requested) != originOf(app.redirectUri)) {
                throw BusinessException(400, "redirect_uri 与应用登记的回调域不一致，已拒绝签发票据")
            }
            requested
        } else {
            app.redirectUri
        }

        // Generate cryptographically random single-use ticket
        val ticketValue = "TICKET_" + CryptoUtils.generateRandomToken(32)

        val ticket = SsoTicket(
            ticket = ticketValue,
            userId = user.id!!,
            clientId = app.clientId,
            redirectUri = targetRedirect,
            expiresAt = LocalDateTime.now().plusMinutes(5)
        )
        ssoTicketRepository.save(ticket)

        val separator = if (targetRedirect.contains("?")) "&" else "?"
        val finalRedirectUrl = "$targetRedirect${separator}ticket=$ticketValue&app=${app.clientId}"

        auditLogService.log(
            userId = user.id,
            username = user.username,
            action = "SSO_AUTHORIZE",
            clientId = app.clientId,
            ip = ip,
            userAgent = userAgent,
            status = "SUCCESS",
            details = "Generated SSO authorization ticket for ${app.appName}"
        )

        return SsoAuthorizeResponse(
            ticket = ticketValue,
            redirectUrl = finalRedirectUrl,
            expiresInSeconds = 300
        )
    }

    private fun originOf(url: String): String? = runCatching {
        val uri = URI(url)
        require(uri.scheme in listOf("http", "https"))
        "${uri.scheme}://${uri.authority.lowercase()}"
    }.getOrNull()

    // 过期 ticket 不清理会无限累积（每条含随机票据与回调地址），每天凌晨静默清扫一次。
    @Scheduled(cron = "0 0 3 * * ?")
    fun purgeExpiredTickets() {
        val removed = ssoTicketRepository.deleteByExpiresAtBefore(LocalDateTime.now().minusHours(1))
        if (removed > 0) println("[SSO] 清理过期票据 $removed 条")
    }

    @Transactional
    fun exchangeTicket(request: SsoTokenExchangeRequest): SsoTokenExchangeResponse {
        val ticket = ssoTicketRepository.findByTicketAndConsumedFalse(request.ticket)
            .orElseThrow { BusinessException(400, "Invalid or already consumed SSO ticket") }

        if (ticket.expiresAt.isBefore(LocalDateTime.now())) {
            throw BusinessException(400, "SSO ticket has expired")
        }

        if (ticket.clientId != request.clientId) {
            throw BusinessException(400, "Ticket does not match the requesting client ID")
        }

        val app = appClientRepository.findByClientId(request.clientId)
            .orElseThrow { BusinessException(404, "App client not found") }

        // Validate client secret (constant-time, never short-circuit on first differing byte)
        val secretMatches = MessageDigest.isEqual(
            app.clientSecret.toByteArray(Charsets.UTF_8),
            request.clientSecret.toByteArray(Charsets.UTF_8)
        )
        if (!secretMatches) {
            auditLogService.log(
                userId = null,
                username = null,
                action = "SSO_EXCHANGE_FAILED",
                clientId = request.clientId,
                status = "FAILED",
                details = "Invalid client secret"
            )
            throw BusinessException(401, "Invalid client secret for ${request.clientId}")
        }

        // Consume the ticket atomically: 只有当 ticket 仍处于未核销状态时才更新成功，
        // 并发重复兑换只会有一个请求拿到受影响行数 1，从数据库层面杜绝重放竞态。
        if (ssoTicketRepository.consumeOnce(request.ticket) != 1) {
            throw BusinessException(400, "Invalid or already consumed SSO ticket")
        }

        val user = userRepository.findById(ticket.userId)
            .orElseThrow { BusinessException(404, "User not found") }

        val profile = userAppProfileRepository.findByUserIdAndAppId(user.id!!, app.id!!)
            .orElseGet {
                val newProf = UserAppProfile(
                    user = user,
                    app = app,
                    appNickname = user.username,
                    appRole = "MEMBER"
                )
                userAppProfileRepository.save(newProf)
            }

        profile.lastLoginAt = LocalDateTime.now()
        userAppProfileRepository.save(profile)

        auditLogService.log(
            userId = user.id,
            username = user.username,
            action = "SSO_LOGIN_SUCCESS",
            clientId = app.clientId,
            status = "SUCCESS",
            details = "子应用成功换票登录：${app.appName} (${app.clientId})"
        )

        // Issue App-Scoped Token
        val scopedToken = tokenProvider.generateScopedAppToken(
            user.id!!,
            user.username,
            app.clientId,
            profile.appRole
        )

        return SsoTokenExchangeResponse(
            accessToken = scopedToken,
            user = UserInfoScopedDto(
                userId = user.id!!,
                username = user.username,
                email = user.email,
                appRole = profile.appRole,
                appNickname = profile.appNickname,
                appMetadata = profile.appMetadata
            )
        )
    }
}

@Service
class AuditLogService(
    private val auditLogRepository: AuditLogRepository,
    private val userRepository: UserRepository,
    private val appClientRepository: AppClientRepository
) {

    @Transactional
    fun log(
        userId: Long?,
        username: String?,
        action: String,
        clientId: String? = null,
        ip: String? = null,
        userAgent: String? = null,
        status: String = "SUCCESS",
        details: String? = null
    ) {
        val entry = AuditLog(
            userId = userId,
            username = username,
            action = action,
            clientId = clientId,
            // 拿不到真实来源时不编造回环地址，如实留空。
            ipAddress = ip,
            userAgent = userAgent,
            status = status,
            details = details
        )
        auditLogRepository.save(entry)
    }

    @Transactional(readOnly = true)
    fun getUserLogs(username: String): List<AuditLogDto> {
        val user = userRepository.findByUsername(username).orElse(null) ?: return emptyList()
        return auditLogRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.id!!).map {
            AuditLogDto(
                id = it.id!!,
                userId = it.userId,
                username = it.username,
                action = it.action,
                clientId = it.clientId,
                ipAddress = it.ipAddress,
                userAgent = it.userAgent,
                status = it.status,
                details = it.details,
                createdAt = it.createdAt
            )
        }
    }

    @Transactional(readOnly = true)
    fun getRecentLogs(): List<AuditLogDto> {
        return auditLogRepository.findTop50ByOrderByCreatedAtDesc().map {
            AuditLogDto(
                id = it.id!!,
                userId = it.userId,
                username = it.username,
                action = it.action,
                clientId = it.clientId,
                ipAddress = it.ipAddress,
                userAgent = it.userAgent,
                status = it.status,
                details = it.details,
                createdAt = it.createdAt
            )
        }
    }

    @Transactional(readOnly = true)
    fun getSecurityStats(): SecurityStatusDto {
        return SecurityStatusDto(
            activeUsersCount = userRepository.count(),
            activeAppsCount = appClientRepository.count(),
            totalAuditLogs = auditLogRepository.count(),
            failedAttemptsCount = auditLogRepository.countByStatus("FAILED")
        )
    }
}
