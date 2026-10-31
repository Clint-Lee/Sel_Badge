package com.badge.auth.portal.controller

import com.badge.auth.common.ApiResponse
import com.badge.auth.portal.dto.OfferPage
import com.badge.auth.portal.dto.PriceCatalog
import com.badge.auth.portal.dto.PriceSnapshot
import com.badge.auth.portal.service.PriceFeedService
import org.springframework.web.bind.annotation.*

/**
 * 低价库公开接口。读路径全部命中本地快照，刷新页面不会触发对外采集；
 * 查询计数（catalog/query）只累计访问次数，不改变报价内容。
 */
@RestController
@RequestMapping("/api/portal/prices")
class PriceController(private val feeds: PriceFeedService) {

    @GetMapping
    fun prices(): ApiResponse<PriceSnapshot> = ApiResponse.success(feeds.snapshot())

    /** 选品台：标准规格 + 可比最低价 + 来源状态。细分筛选在前端完成。 */
    @GetMapping("/catalog")
    fun catalog(@RequestParam(defaultValue = "on") quality: String): ApiResponse<PriceCatalog> =
        ApiResponse.success(feeds.catalog(quality != "off"))

    /** 商家报价分页，保留原始币种与来源采集时间。 */
    @GetMapping("/groups/{groupId}/offers")
    fun offers(
        @PathVariable groupId: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "on") quality: String,
        @RequestParam(required = false) source: String?,
        @RequestParam(defaultValue = "all") stock: String,
        @RequestParam(required = false) keyword: String?
    ): ApiResponse<OfferPage> = ApiResponse.success(
        feeds.offers(groupId, page, quality != "off", source, stock == "in-stock", keyword)
    )

    /** 记录一次规格查询（前端在首次打开规格报价时调用）。 */
    @PostMapping("/catalog/query")
    fun query(@RequestBody body: Map<String, String>): ApiResponse<Map<String, Long>> {
        feeds.recordQuery(body["group"] ?: "")
        return ApiResponse.success(mapOf("count" to 1))
    }

    /** 保留的 API 对接待接端点：只登记意向，不出站请求。 */
    @PostMapping("/catalog/api-plan")
    fun apiPlan(@RequestBody body: Map<String, String>): ApiResponse<Boolean> {
        feeds.recordQueryPlan("plan", body["requestId"] ?: "")
        return ApiResponse.success(true, "已登记对接意向")
    }
}
