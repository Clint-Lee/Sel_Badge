package com.badge.auth.repository

import com.badge.auth.entity.AppClient
import com.badge.auth.entity.AuditLog
import com.badge.auth.entity.SsoTicket
import com.badge.auth.entity.User
import com.badge.auth.entity.UserAppProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): Optional<User>
    fun findByEmail(email: String): Optional<User>
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}

@Repository
interface AppClientRepository : JpaRepository<AppClient, Long> {
    fun findByClientId(clientId: String): Optional<AppClient>
    fun existsByClientId(clientId: String): Boolean
    fun findAllByStatus(status: String): List<AppClient>
}

@Repository
interface UserAppProfileRepository : JpaRepository<UserAppProfile, Long> {
    fun findByUserIdAndAppId(userId: Long, appId: Long): Optional<UserAppProfile>

    @Query("SELECT p FROM UserAppProfile p JOIN FETCH p.app WHERE p.user.id = :userId")
    fun findAllByUserIdWithApp(userId: Long): List<UserAppProfile>

    fun findByUserIdAndAppClientId(userId: Long, clientId: String): Optional<UserAppProfile>
}

@Repository
interface SsoTicketRepository : JpaRepository<SsoTicket, Long> {
    fun findByTicketAndConsumedFalse(ticket: String): Optional<SsoTicket>
    fun deleteByExpiresAtBefore(dateTime: LocalDateTime): Long

    /**
     * 原子核销：仅当票据仍为未消费状态时置为已消费，返回受影响行数。
     * 并发重复兑换只会有一个请求拿到 1，从数据库层面杜绝重放竞态。
     */
    @Modifying
    @Query("UPDATE SsoTicket t SET t.consumed = true WHERE t.ticket = :ticket AND t.consumed = false")
    fun consumeOnce(ticket: String): Int
}

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findTop50ByOrderByCreatedAtDesc(): List<AuditLog>
    fun findTop50ByUserIdOrderByCreatedAtDesc(userId: Long): List<AuditLog>
    fun countByStatus(status: String): Long
}
