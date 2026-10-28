package com.badge.auth.portal.controller

import com.badge.auth.common.ApiResponse
import com.badge.auth.portal.dto.PriceSnapshot
import com.badge.auth.portal.service.PriceFeedService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 匿名只读快照接口；刷新此接口不会触发对外采集。 */
@RestController
@RequestMapping("/api/portal/prices")
class PriceController(private val feeds: PriceFeedService) {
    @GetMapping
    fun prices(): ApiResponse<PriceSnapshot> = ApiResponse.success(feeds.snapshot())
}
