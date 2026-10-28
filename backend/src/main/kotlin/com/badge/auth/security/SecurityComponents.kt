package com.badge.auth.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${badge.jwt.secret:BadgeAuthSuperSecureJWTSecretKeyWithMinimum256BitsLengthForProduction2026!}")
    secret: String,
    @Value("\${badge.jwt.access-token-expiration-ms:7200000}")
    private val accessTokenExpirationMs: Long,
    @Value("\${badge.jwt.refresh-token-expiration-ms:604800000}")
    private val refreshTokenExpirationMs: Long
) {
    private val log = LoggerFactory.getLogger(JwtTokenProvider::class.java)
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    fun generateAccessToken(userId: Long, username: String, email: String, role: String): String {
        val extraClaims = mapOf(
            "userId" to userId,
            "email" to email,
            "role" to role
        )
        val now = Date()
        val expiryDate = Date(now.time + accessTokenExpirationMs)

        return Jwts.builder()
            .subject(username)
            .claims(extraClaims)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    fun generateScopedAppToken(userId: Long, username: String, clientId: String, appRole: String): String {
        val claims = mapOf(
            "userId" to userId,
            "clientId" to clientId,
            "appRole" to appRole,
            "tokenType" to "APP_SCOPED"
        )
        val now = Date()
        val expiryDate = Date(now.time + accessTokenExpirationMs)

        return Jwts.builder()
            .subject(username)
            .claims(claims)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(userId: Long, username: String): String {
        val now = Date()
        val expiryDate = Date(now.time + refreshTokenExpirationMs)

        return Jwts.builder()
            .subject(username)
            .claim("userId", userId)
            .claim("tokenType", "REFRESH")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    fun getClaimsFromToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun getUsernameFromToken(token: String): String {
        return getClaimsFromToken(token).subject
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
            true
        } catch (ex: Exception) {
            log.warn("Invalid JWT token: {}", ex.message)
            false
        }
    }
}

@Component
class JwtAuthenticationFilter(
    private val tokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {

    private val filterLogger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val jwt = getJwtFromRequest(request)
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt!!)) {
                val claims = tokenProvider.getClaimsFromToken(jwt)
                val tokenType = claims["tokenType"] as? String
                // 只有门户会话令牌（不带 tokenType 声明）能访问本平台 API。
                // REFRESH 令牌只用于换发；APP_SCOPED 令牌属于子应用，
                // 两者都不得反过来调用门户的用户/资料/审计接口。
                if (tokenType == null) {
                    val username = claims.subject
                    val role = (claims["role"] as? String) ?: "ROLE_USER"

                    val authority = SimpleGrantedAuthority(role)
                    val authentication = UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        Collections.singletonList(authority)
                    )
                    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                    SecurityContextHolder.getContext().authentication = authentication
                }
            }
        } catch (ex: Exception) {
            filterLogger.error("Could not set user authentication in security context: {}", ex.message)
        }

        filterChain.doFilter(request, response)
    }

    private fun getJwtFromRequest(request: HttpServletRequest): String? {
        // 只接受 Authorization 头；URL 查询参数会泄进日志、历史与 Referer，不再接受。
        val bearerToken = request.getHeader("Authorization")
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7)
        }
        return null
    }
}

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val rateLimitFilter: RateLimitFilter,
    @Value("\${badge.cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    allowedOriginsConfig: String
) {

    private val allowedOrigins = allowedOriginsConfig.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder(12)
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .headers { headers ->
                headers.frameOptions { it.sameOrigin() }
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.GET, "/api/portal/tools", "/api/portal/tools/*/reviews", "/api/portal/prices").permitAll()
                    .requestMatchers(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/refresh",
                        "/api/sso/token",
                        "/api/apps/public",
                        "/h2-console/**",
                        "/error"
                    ).permitAll()
                    // 签发票据必须已登录；换票是子应用后端对后端，保持公开。
                    .requestMatchers("/api/sso/authorize").authenticated()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        // 只允许显式登记的门户/子应用前端来源，不再对任意站点开放凭证跨域。
        configuration.allowedOrigins = allowedOrigins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf(
            "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "X-App-Id", "X-Client-Secret"
        )
        configuration.exposedHeaders = listOf("Authorization")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}

/**
 * AES 主密钥装配：application.yml 中的 badge.crypto.master-key 在此生效。
 * 此前该配置项没有任何消费者（CryptoUtils 硬编码默认值），改配置不会真正换钥。
 */
@Component
class CryptoKeyInitializer(@Value("\${badge.crypto.master-key}") masterKey: String) {
    init {
        CryptoUtils.initKey(masterKey)
        if (masterKey.startsWith("BadgeAuthPlatform")) {
            println("[Badge] 警告：正在使用内置开发默认主密钥；生产环境（mysql profile）必须配置 BADGE_CRYPTO_MASTER_KEY。")
        }
    }
}
