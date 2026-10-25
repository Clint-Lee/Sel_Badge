package com.badge.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class BadgeAuthApplication

fun main(args: Array<String>) {
    runApplication<BadgeAuthApplication>(*args)
}
