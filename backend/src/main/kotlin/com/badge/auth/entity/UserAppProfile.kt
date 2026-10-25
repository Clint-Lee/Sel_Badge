package com.badge.auth.entity

import com.badge.auth.security.AesGcmCryptoConverter
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Standard multi-application user profile mapping.
 * Each user can have customized profiles, roles, and encrypted private data
 * dedicated to each registered application (Portal, Game, Service, etc.).
 */
@Entity
@Table(
    name = "user_app_profiles",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_app", columnNames = ["user_id", "app_id"])
    ],
    indexes = [
        Index(name = "idx_uap_user_id", columnList = "user_id"),
        Index(name = "idx_uap_app_id", columnList = "app_id")
    ]
)
class UserAppProfile(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    var app: AppClient? = null,

    // App-specific customized nickname (e.g. Gamer Tag, Forum Handle)
    @Column(name = "app_nickname", length = 64)
    var appNickname: String? = null,

    // App-specific role within this application (e.g. VIP_PLAYER, OPERATOR, MEMBER)
    @Column(name = "app_role", nullable = false, length = 32)
    var appRole: String = "MEMBER",

    // AES-256-GCM encrypted application-specific private metadata (JSON string)
    // E.g. game level, coins, service quota, preferences
    @Convert(converter = AesGcmCryptoConverter::class)
    @Column(name = "app_metadata_encrypted", columnDefinition = "TEXT")
    var appMetadata: String? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        this.updatedAt = LocalDateTime.now()
    }
}
