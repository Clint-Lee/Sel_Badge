package com.badge.auth.config

import com.badge.auth.entity.AppClient
import com.badge.auth.entity.User
import com.badge.auth.entity.UserAppProfile
import com.badge.auth.repository.AppClientRepository
import com.badge.auth.repository.UserAppProfileRepository
import com.badge.auth.repository.UserRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime

@Configuration
class DataInitializer {

    private val log: Logger = LoggerFactory.getLogger(DataInitializer::class.java)

    @Bean
    fun initDatabase(
        userRepository: UserRepository,
        appClientRepository: AppClientRepository,
        userAppProfileRepository: UserAppProfileRepository,
        passwordEncoder: PasswordEncoder
    ): CommandLineRunner = CommandLineRunner {
        log.info("Checking and initializing default identity and application data...")

        // 1. Seed Registered Sub-Applications
        val apps = listOf(
            AppClient(
                clientId = "app_portal_01",
                clientSecret = "sec_portal_super_secret_2026",
                appName = "个人主页门户",
                appCategory = "PORTAL",
                homepageUrl = "http://localhost:5173",
                redirectUri = "http://localhost:5173/sso/callback",
                description = "全站导航主页与个人中心，汇集子应用入口与系统通知",
                iconType = "globe",
                status = "ACTIVE"
            ),
            AppClient(
                clientId = "app_game_arcade",
                clientSecret = "sec_game_arcade_secret_2026",
                appName = "网页小游戏平台",
                appCategory = "GAME",
                homepageUrl = "http://localhost:8081",
                redirectUri = "http://localhost:8081/auth/sso-receive",
                description = "轻量在线对战小游戏、休闲街机与天梯积分榜",
                iconType = "gamepad",
                status = "ACTIVE"
            ),
            AppClient(
                clientId = "app_cloud_service",
                clientSecret = "sec_cloud_service_secret_2026",
                appName = "微服务与工具中心",
                appCategory = "SERVICE",
                homepageUrl = "http://localhost:8082",
                redirectUri = "http://localhost:8082/oauth2/callback",
                description = "通用接口服务、调度任务与常用开发在线工具",
                iconType = "server",
                status = "ACTIVE"
            ),
            AppClient(
                clientId = "app_wiki_knowledge",
                clientSecret = "sec_wiki_secret_2026",
                appName = "知识与文档库",
                appCategory = "WIKI",
                homepageUrl = "http://localhost:8083",
                redirectUri = "http://localhost:8083/sso/login",
                description = "个人开发笔记、系统接口文档与架构演化记录",
                iconType = "book",
                status = "ACTIVE"
            ),
            AppClient(
                clientId = "app_hotpot_video",
                clientSecret = "sec_hotpot_video_secret_2026",
                appName = "万热观象 · 视频工作台",
                appCategory = "SERVICE",
                homepageUrl = "http://localhost:4000",
                redirectUri = "http://localhost:4000/auth/badge/callback",
                description = "多平台热点视频聚合、字幕转写与媒体工作台",
                iconType = "server",
                status = "ACTIVE"
            ),
            AppClient(
                clientId = "app_xiyou_game",
                clientSecret = "sec_xiyou_game_secret_2026",
                appName = "汉风西游",
                appCategory = "GAME",
                homepageUrl = "http://localhost:8081",
                redirectUri = "http://localhost:8081/auth/badge/callback",
                description = "正统2D西游题材探索游戏，Badge 登录后云存档",
                iconType = "gamepad",
                status = "ACTIVE"
            )
        )

        apps.forEach { app ->
            val existing = appClientRepository.findByClientId(app.clientId)
            if (existing.isEmpty) {
                appClientRepository.save(app)
                log.info("Initialized default app: {} ({})", app.appName, app.clientId)
            } else {
                val e = existing.get()
                e.appName = app.appName
                e.description = app.description
                appClientRepository.save(e)
            }
        }

        // 2. Seed Default Admin User if empty
        if (userRepository.count() == 0L) {
            val adminUser = User(
                username = "admin",
                email = "admin@badge.internal",
                phone = "13800138000", // Automatically AES-256 encrypted
                passwordHash = passwordEncoder.encode("Admin@123456"),
                avatarUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=admin_avatar",
                status = "ACTIVE",
                role = "ROLE_ADMIN"
            )
            val savedAdmin = userRepository.save(adminUser)
            log.info("Initialized default administrator: 'admin' / 'Admin@123456'")

            // 3. Create initial multi-app profile records with encrypted app metadata
            appClientRepository.findAll().forEach { app ->
                val metadataJson = when (app.appCategory) {
                    "GAME" -> """{"gamerTag":"NeoShadow","level":88,"rank":"CyberGrandmaster","equippedSkin":"NeonGhost"}"""
                    "SERVICE" -> """{"serviceTier":"Enterprise","apiQuotaDaily":100000,"accessScopes":["read:all","write:all"]}"""
                    "PORTAL" -> """{"themePreference":"cyber-dark","starredApps":["app_game_arcade","app_cloud_service"]}"""
                    else -> """{"role":"Maintainer","notes":"System core seed profile"}"""
                }

                val profile = UserAppProfile(
                    user = savedAdmin,
                    app = app,
                    appNickname = "Admin · ${app.appName.split(" ")[0]}",
                    appRole = if (app.appCategory == "GAME") "VIP_CHAMPION" else "ADMIN",
                    appMetadata = metadataJson, // Automatically encrypted by AES-256-GCM converter
                    lastLoginAt = LocalDateTime.now().minusHours(2)
                )
                userAppProfileRepository.save(profile)
            }
            log.info("Initialized user-app profile bindings with AES-256 encrypted metadata.")
        }
    }
}
