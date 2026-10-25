package com.badge.auth.entity

import com.badge.auth.security.AesGcmCryptoConverter
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "app_clients",
    indexes = [
        Index(name = "idx_client_id", columnList = "client_id", unique = true)
    ]
)
class AppClient(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "client_id", nullable = false, unique = true, length = 64)
    var clientId: String = "",

    // AES-256-GCM encrypted client secret
    @Convert(converter = AesGcmCryptoConverter::class)
    @Column(name = "client_secret_encrypted", nullable = false, length = 255)
    var clientSecret: String = "",

    @Column(name = "app_name", nullable = false, length = 128)
    var appName: String = "",

    @Column(name = "app_category", nullable = false, length = 32)
    var appCategory: String = "SERVICE", // PORTAL, GAME, SERVICE, TOOL, WIKI

    @Column(name = "homepage_url", nullable = false, length = 512)
    var homepageUrl: String = "",

    @Column(name = "redirect_uri", nullable = false, length = 512)
    var redirectUri: String = "",

    @Column(length = 512)
    var description: String? = null,

    @Column(name = "icon_type", length = 64)
    var iconType: String = "globe", // globe, gamepad, server, shield, book

    @Column(nullable = false, length = 32)
    var status: String = "ACTIVE", // ACTIVE, MAINTENANCE, INACTIVE

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
