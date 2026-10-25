package com.badge.auth.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Short-lived single-use authorization ticket for SSO redirection flows.
 * Similar to OAuth2 authorization code.
 */
@Entity
@Table(
    name = "sso_tickets",
    indexes = [
        Index(name = "idx_ticket_val", columnList = "ticket", unique = true),
        Index(name = "idx_ticket_expires", columnList = "expires_at")
    ]
)
class SsoTicket(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 128)
    var ticket: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0L,

    @Column(name = "client_id", nullable = false, length = 64)
    var clientId: String = "",

    @Column(name = "redirect_uri", nullable = false, length = 512)
    var redirectUri: String = "",

    @Column(nullable = false)
    var consumed: Boolean = false,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(5),

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
