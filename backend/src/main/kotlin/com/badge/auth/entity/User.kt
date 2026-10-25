package com.badge.auth.entity

import com.badge.auth.security.AesGcmCryptoConverter
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_user_username", columnList = "username", unique = true),
        Index(name = "idx_user_email", columnList = "email", unique = true)
    ]
)
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 64)
    var username: String = "",

    @Column(nullable = false, unique = true, length = 128)
    var email: String = "",

    // AES-256-GCM encrypted sensitive data
    @Convert(converter = AesGcmCryptoConverter::class)
    @Column(name = "phone_encrypted", length = 255)
    var phone: String? = null,

    // BCrypt password hash (never stored in plaintext)
    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String = "",

    @Column(name = "avatar_url", length = 512)
    var avatarUrl: String? = null,

    @Column(nullable = false, length = 32)
    var status: String = "ACTIVE", // ACTIVE, LOCKED, DISABLED

    @Column(nullable = false, length = 32)
    var role: String = "ROLE_USER", // ROLE_USER, ROLE_ADMIN

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
