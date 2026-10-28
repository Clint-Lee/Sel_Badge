package com.badge.auth.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量内存滑动窗口限流：登录/注册/换票是匿名可打的高价值端点，
 * 没有限流时可以被无限暴力尝试。按"路径+客户端IP"计数。
 * 单实例内存实现即可覆盖当前部署形态；上多实例时再换 Redis。
 */
@Component
class RateLimitFilter : OncePerRequestFilter() {

    data class Rule(val limit: Int, val windowMs: Long)

    private val rules = mapOf(
        "POST /api/auth/login" to Rule(limit = 10, windowMs = 5 * 60 * 1000L),
        "POST /api/auth/register" to Rule(limit = 5, windowMs = 60 * 60 * 1000L),
        "POST /api/auth/refresh" to Rule(limit = 30, windowMs = 5 * 60 * 1000L),
        "POST /api/sso/token" to Rule(limit = 60, windowMs = 60 * 1000L),
        "POST /api/portal/submissions" to Rule(limit = 10, windowMs = 60 * 60 * 1000L)
    )

    private val hits = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val objectMapper = ObjectMapper()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val rule = rules["${request.method} ${request.requestURI}"]
        if (rule != null) {
            val key = "${request.requestURI}|${clientIp(request)}"
            val now = System.currentTimeMillis()
            val window = hits.computeIfAbsent(key) { ArrayDeque() }
            synchronized(window) {
                while (window.isNotEmpty() && now - window.first() > rule.windowMs) window.removeFirst()
                if (window.size >= rule.limit) {
                    response.status = 429
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.characterEncoding = "UTF-8"
                    objectMapper.writeValue(
                        response.writer,
                        mapOf("code" to 429, "message" to "尝试过于频繁，请稍后再试", "data" to null)
                    )
                    return
                }
                window.addLast(now)
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun clientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (forwarded.isNullOrBlank()) request.remoteAddr else forwarded.split(",")[0].trim()
    }
}
