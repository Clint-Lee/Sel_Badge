-- ====================================================================
-- BADGE IDENTITY & AUTHENTICATION PLATFORM - MYSQL 8.0 DDL SCHEMA
-- ====================================================================

CREATE DATABASE IF NOT EXISTS `badge_auth` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `badge_auth`;

-- 1. Core Users Table
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(64) NOT NULL UNIQUE COMMENT 'Unique login handle',
    `email` VARCHAR(128) NOT NULL UNIQUE COMMENT 'User email',
    `phone_encrypted` VARCHAR(255) NULL COMMENT 'AES-256-GCM encrypted phone number',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password (cost 12)',
    `avatar_url` VARCHAR(512) NULL COMMENT 'Avatar image link',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, LOCKED, DISABLED',
    `role` VARCHAR(32) NOT NULL DEFAULT 'ROLE_USER' COMMENT 'ROLE_USER, ROLE_ADMIN',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_users_username` (`username`),
    INDEX `idx_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Central User Master Table';

-- 2. Registered Sub-Applications Table (SSO OAuth Clients)
CREATE TABLE IF NOT EXISTS `app_clients` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `client_id` VARCHAR(64) NOT NULL UNIQUE COMMENT 'Unique application identifier',
    `client_secret_encrypted` VARCHAR(255) NOT NULL COMMENT 'AES-256-GCM encrypted client secret',
    `app_name` VARCHAR(128) NOT NULL COMMENT 'Display name of application',
    `app_category` VARCHAR(32) NOT NULL DEFAULT 'SERVICE' COMMENT 'PORTAL, GAME, SERVICE, WIKI',
    `homepage_url` VARCHAR(512) NOT NULL COMMENT 'Homepage / direct entry URL',
    `redirect_uri` VARCHAR(512) NOT NULL COMMENT 'OAuth2/SSO callback URI',
    `description` VARCHAR(512) NULL COMMENT 'Application summary',
    `icon_type` VARCHAR(64) DEFAULT 'globe' COMMENT 'Icon visual token',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, MAINTENANCE',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_app_clients_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Registered Sub-Applications Table';

-- 3. Multi-Application User Profile & Platform Data Binding Table
-- (Standard industry multi-tenant platform user mapping)
CREATE TABLE IF NOT EXISTS `user_app_profiles` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT 'Foreign key to users.id',
    `app_id` BIGINT NOT NULL COMMENT 'Foreign key to app_clients.id',
    `app_nickname` VARCHAR(64) NULL COMMENT 'Application-specific customized alias/gamer tag',
    `app_role` VARCHAR(32) NOT NULL DEFAULT 'MEMBER' COMMENT 'Application-specific role (e.g. VIP_PLAYER, OPERATOR)',
    `app_metadata_encrypted` TEXT NULL COMMENT 'AES-256-GCM encrypted app JSON data (e.g. game level, inventory, quota)',
    `last_login_at` DATETIME NULL COMMENT 'Last time user accessed this app',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_app` (`user_id`, `app_id`),
    CONSTRAINT `fk_uap_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_uap_app` FOREIGN KEY (`app_id`) REFERENCES `app_clients` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Multi-Application User Profile Table';

-- 4. Short-lived Single-use SSO Authorization Tickets
CREATE TABLE IF NOT EXISTS `sso_tickets` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `ticket` VARCHAR(128) NOT NULL UNIQUE,
    `user_id` BIGINT NOT NULL,
    `client_id` VARCHAR(64) NOT NULL,
    `redirect_uri` VARCHAR(512) NOT NULL,
    `consumed` BOOLEAN NOT NULL DEFAULT FALSE,
    `expires_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_sso_ticket_val` (`ticket`),
    INDEX `idx_sso_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Temporary SSO Authorization Code Tickets';

-- 5. Security Audit & Login Logs Table
CREATE TABLE IF NOT EXISTS `audit_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NULL,
    `username` VARCHAR(64) NULL,
    `action` VARCHAR(64) NOT NULL COMMENT 'LOGIN_SUCCESS, LOGIN_FAILED, SSO_AUTHORIZE, etc.',
    `client_id` VARCHAR(64) NULL,
    `ip_address` VARCHAR(64) NULL,
    `user_agent` VARCHAR(512) NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    `details` VARCHAR(1024) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_audit_user_id` (`user_id`),
    INDEX `idx_audit_action` (`action`),
    INDEX `idx_audit_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Security Audit Log';
