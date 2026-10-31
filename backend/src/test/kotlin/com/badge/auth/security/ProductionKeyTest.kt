package com.badge.auth.security

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

/** Production must reject the public development keys before initializing crypto. */
class ProductionKeyTest {
    private fun production() = MockEnvironment().apply { setActiveProfiles("prod") }

    @Test
    fun rejectsDevelopmentMasterKey() {
        assertThrows(IllegalArgumentException::class.java) {
            CryptoKeyInitializer("BadgeAuthPlatform" + "x".repeat(40), "x".repeat(64), production())
        }
    }

    @Test
    fun rejectsDevelopmentSigningKey() {
        assertThrows(IllegalArgumentException::class.java) {
            CryptoKeyInitializer("x".repeat(64), "BadgeAuthSuper" + "x".repeat(40), production())
        }
    }
}
