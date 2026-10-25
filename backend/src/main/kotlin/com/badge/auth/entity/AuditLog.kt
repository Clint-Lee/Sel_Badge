package com.badge.auth.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "audit_logs",
    indexes = [
        Index(name = "idx_audit_user_id", columnList = "user_id"),
        Index(name = "idx_audit_action", columnList = "action"),
        Index(name = "idx_audit_created_at", columnList = "created_at")
    ]
)
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "user_id")
    var userId: Long? = null,

    @Column(length = 64)
    var username: String? = null,

    @Column(nullable = false, length = 64)
    var action: String = "", // LOGIN_SUCCESS, LOGIN_FAILED, REGISTER, SSO_AUTHORIZE, PROFILE_UPDATE

    @Column(name = "client_id", length = 64)
    var clientId: String? = null,

    @Column(name = "ip_address", length = 64)
    var ipAddress: String? = null,

    @Column(name = "user_agent", length = 512)
    var userAgent: String? = null,

    @Column(nullable = false, length = 32)
    var status: String = "SUCCESS", // SUCCESS, FAILED

    @Column(length = 1024)
    var details: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
