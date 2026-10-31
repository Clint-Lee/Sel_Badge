package com.badge.auth.portal.service

import com.badge.auth.portal.dto.*
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 低价库数据：直接读取公开报价来源，全量分页落本地快照。
 * 单次同步失败保留该来源上次成功快照；页面读取只碰本地快照，不放大外部流量。
 */
@Service
class PriceFeedService(
    private val store: PortalFileStore,
    @Value("\${badge.portal.sync-enabled:true}") private val syncEnabled: Boolean
) {
    companion object {
        private const val SNAPSHOT_FILE = "prices.json"
        private const val QUERY_FILE = "price-queries.json"
        private const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024
        private const val PICKAI_PAGE_SIZE = 50
        private const val MAX_PAGES = 80
        /** PriceAI 公开接口固定每页 30 条，且限流约 30 请求 / 35 秒。 */
        private const val PRICEAI_PAGE_SIZE = 30
        private const val PRICEAI_MAX_PAGES = 80
        private const val PRICEAI_DELAY_MS = 900L
        const val OFFER_PAGE_SIZE = 20

        /** 明确不是实际报价的占位 / 说明类标题。 */
        private val SUSPECT = Regex("勿拍|禁止下单|说明链接|非实际报价|测试商品|定金|补差价")

        /** 规格不符：标题与本标准规格明显不是同一商品（引流、跨产品、资格号等）。 */
        private val SPEC_MISMATCH = Regex(
            "muse|token\\s*成品号|资格号|只是资格|半价资格|非本商品|他站|引流|" +
                "补差价|定金|勿拍|联系客服|代拍|占位|测试商品",
            RegexOption.IGNORE_CASE
        )
    }

    private val log = LoggerFactory.getLogger(javaClass)
    private val client by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() }
    private val pool by lazy { Executors.newFixedThreadPool(6) }
    @Volatile private var syncing = false
    @Volatile private var snapshot = store.read(SNAPSHOT_FILE, PriceSnapshot::class.java) {
        ClassPathResource("portal/prices.json").inputStream.use { store.mapper.readValue(it, PriceSnapshot::class.java) }
    }
    @Volatile private var queryCounts: Map<String, Long> =
        store.read(QUERY_FILE, QueryCounters::class.java) { QueryCounters() }.counts
    private var priceAiGeneration: String? = null

    @Volatile private var cachedQuality: Boolean? = null
    @Volatile private var cachedCatalog: List<PriceGroup> = emptyList()

    fun snapshot(): PriceSnapshot = snapshot

    /**
     * 选品台：按来源标准规格分组，最低价排除明确售罄与异常报价。
     * 形态 / 周期 / 来源的细分筛选在前端完成，避免每次切换筛选都重算并传输全量分组。
     */
    fun catalog(quality: Boolean): PriceCatalog {
        val groups = groups(quality)
        return PriceCatalog(
            groups = groups,
            totalLinks = snapshot.offers.size,
            sources = snapshot.sources,
            intervalMinutes = snapshot.intervalMinutes,
            qualityFilter = quality,
            updatedAt = snapshot.updatedAt ?: snapshot.sources.mapNotNull { it.lastSuccessAt }.maxOrNull(),
            queries = queryCounts
        )
    }

    fun groups(quality: Boolean): List<PriceGroup> {
        if (cachedQuality != quality) {
            cachedCatalog = buildGroups(quality)
            cachedQuality = quality
        }
        return cachedCatalog
    }

    /**
     * 选品台分组：同一标准规格在不同来源下的报价合并成一张卡（参考站同样按规格合并），
     * 卡上的最低价再走异常低价过滤，避免把引流占位价当成真实最低价。
     */
    private fun buildGroups(quality: Boolean): List<PriceGroup> {
        val usable = snapshot.offers.filter { it.price.isFinite() && it.price >= 0 }
        return usable.groupBy { specKey(it) }.map { (key, rows) ->
            val minima = lowestByCurrency(rows, quality)
            PriceGroup(
                id = key,
                product = rows.first().product,
                category = rows.first().category,
                sources = rows.map { it.source }.distinct().sorted(),
                offers = if (quality) rows.count { isQuotable(it) } else rows.size,
                lowestPrices = minima,
                productType = rows.groupingBy { it.productType }.eachCount().maxByOrNull { it.value }?.key ?: "unknown",
                billingPeriod = rows.groupingBy { it.billingPeriod }.eachCount().maxByOrNull { it.value }?.key ?: "unknown"
            )
        }.sortedWith(compareBy({ it.category }, { it.product }, { it.id }))
    }

    /**
     * 合并键：只用标准规格名。名字本身已区分形态（「ChatGPT Plus」与「ChatGPT Plus 代充值」是两个规格），
     * 因此同名规格在不同来源下的报价归到同一张卡，与参考站一致。
     */
    private fun specKey(offer: Offer): String = normalizeSpec(offer.product)

    private fun normalizeSpec(value: String): String =
        value.lowercase().replace(Regex("[\\s/·、，,]+"), "").replace("／", "")

    /** 单条报价是否可作为公开最低价候选：有货、有正价、非占位、非规格不符。 */
    private fun isQuotable(offer: Offer, quality: Boolean = true): Boolean {
        if (offer.available == false) return false
        if (!offer.price.isFinite() || offer.price <= 0) return false
        if (!quality) return true
        if (offer.suspicious) return false
        return !SPEC_MISMATCH.containsMatchIn(offer.title)
    }

    /**
     * 最低价：排除售罄、零价，以及标题明显不是本文规格的报价（引流 / 跨产品 / 资格号等）。
     * 不做中位数阈值：那会把真实的低价一手货源一起误杀。
     */
    private fun lowestByCurrency(rows: List<Offer>, quality: Boolean): List<CurrencyPrice> =
        rows.groupBy { it.currency }.mapNotNull { (currency, list) ->
            val base = list.filter { isQuotable(it, quality) }
            if (base.isEmpty()) return@mapNotNull null
            CurrencyPrice(currency, base.minOf { it.price })
        }.sortedBy { it.currency }

    /** 商家报价分页：当前规格内按币种 + 价格升序，保留原始币种不换算。 */
    fun offers(groupId: String, page: Int, quality: Boolean, source: String?, stockOnly: Boolean, keyword: String?): OfferPage {
        var rows = snapshot.offers.asSequence().filter { specKey(it) == groupId }
        if (quality) rows = rows.filter { isQuotable(it) }
        if (stockOnly) rows = rows.filter { it.available == true }
        if (!source.isNullOrBlank() && source != "all") rows = rows.filter { it.source == source }
        if (!keyword.isNullOrBlank()) {
            val needle = keyword.trim().lowercase()
            rows = rows.filter { "${it.shopName} ${it.title}".lowercase().contains(needle) }
        }
        val sorted = rows.sortedWith(compareBy({ it.currency }, { it.price }, { it.shopName })).toList()
        val pageSize = OFFER_PAGE_SIZE
        val totalPages = maxOf(1, (sorted.size + pageSize - 1) / pageSize)
        val safePage = page.coerceIn(1, totalPages)
        val from = (safePage - 1) * pageSize
        return OfferPage(
            items = sorted.subList(from, minOf(from + pageSize, sorted.size)),
            total = sorted.size,
            page = safePage,
            pageSize = pageSize
        )
    }

    /** 记录一次有效查询，用于展示累计查询次数。 */
    @Synchronized fun recordQuery(groupId: String) {
        if (groupId.isBlank() || groupId.length > 200) return
        val next = queryCounts.toMutableMap()
        next[groupId] = (next[groupId] ?: 0) + 1
        queryCounts = next
        runCatching { store.write(QUERY_FILE, QueryCounters(next)) }
            .onFailure { log.warn("query counter write failed: {}", it.message) }
    }

    @Synchronized fun recordQueryPlan(source: String, requestId: String) {
        if (requestId.isBlank() || requestId.length > 80) return
        // API 对接待接：只累计请求次数，不产生任何出站调用。
        val key = "api:$source"
        val next = queryCounts.toMutableMap()
        next[key] = (next[key] ?: 0) + 1
        queryCounts = next
        runCatching { store.write(QUERY_FILE, QueryCounters(next)) }
    }

    /** 后台定时读取；前台刷新只读本地快照。 */
    @Scheduled(initialDelay = 20000, fixedDelay = 900000)
    fun sync() {
        if (!syncEnabled || syncing) return
        syncing = true
        try {
            syncSource("priceai") { priceAi() }
            syncSource("pickai") { pickAi() }
        } finally {
            syncing = false
        }
    }

    private fun syncSource(source: String, loader: () -> List<Offer>) {
        val attemptedAt = Instant.now().toString()
        val previous = snapshot.sources.find { it.source == source }
        try {
            val offers = loader()
            check(offers.isNotEmpty()) { "来源未返回有效报价，保留旧快照" }
            val note = if (source == "priceai") {
                "官方公开接口：每个标准规格的全部分页报价（每页 30 条）"
            } else {
                "每个标准规格的全部分页公开报价"
            }
            val details = mapOf("products" to offers.map { it.product }.distinct().size, "rows" to offers.size)
            val updated = snapshot.copy(
                offers = snapshot.offers.filter { it.source != source } + offers,
                sources = snapshot.sources.filter { it.source != source } +
                    SourceStatus(source, "complete", Instant.now().toString(), attemptedAt, offers.size, note, details),
                updatedAt = Instant.now().toString(),
                totalLinks = 0
            ).let { it.copy(totalLinks = it.offers.size) }
            store.write(SNAPSHOT_FILE, updated)
            snapshot = updated
            cachedQuality = null
        } catch (ex: Exception) {
            log.warn("Portal price feed {} failed: {}", source, ex.message)
            val failed = snapshot.copy(sources = snapshot.sources.filter { it.source != source } +
                SourceStatus(
                    source, "failed", previous?.lastSuccessAt, attemptedAt,
                    snapshot.offers.count { it.source == source }, "同步失败，保留上次快照", previous?.details ?: emptyMap()
                ))
            store.write(SNAPSHOT_FILE, failed)
            snapshot = failed
            cachedQuality = null
        }
    }

    private fun json(url: String): JsonNode {
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Badge-PriceRadar/1.0").header("Accept", "application/json").GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
            val bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1)
            check(bytes.size <= MAX_RESPONSE_BYTES) { "来源数据超过大小限制" }
            return store.mapper.readTree(bytes)
        }
    }

    /**
     * PriceAI：先读官方快照拿到产品清单与基准价，再按产品逐个走公开分页接口取全量报价。
     * 分页接口限流约 30 请求 / 35 秒，这里串行 + 间隔等待；单产品失败保留该产品上一轮数据。
     */
    private fun priceAi(): List<Offer> {
        val pointer = json("https://data.priceai.cc/latest.json")
        val generation = pointer.path("snapshot_id").asText()
        val uri = URI(pointer.path("snapshot_url").asText())
        // 只跟随官方快照域，避免来源响应把请求导向内网或其他站点。
        check(uri.scheme == "https" && uri.host == "data.priceai.cc" && uri.userInfo == null && uri.port == -1 && uri.path.startsWith("/v1/snapshots/")) { "无效的官方快照地址" }
        val root = json(uri.toString())
        check(root.path("schema_version").asText() == "price-radar.v1" && root.path("products").isArray) { "Price Radar 格式变化" }
        val previous = snapshot.offers.filter { it.source == "priceai" }
        if (generation.isNotBlank() && generation == priceAiGeneration && previous.isNotEmpty()) return previous

        val collected = mutableListOf<Offer>()
        var failedProducts = 0
        for (product in root.path("products")) {
            val slug = product.path("id").asText()
            if (slug.isBlank()) continue
            val name = product.path("name").asText()
            val category = product.path("platform").asText("其他")
            val typeText = "${product.path("product_type").asText()} $name"
            try {
                val rows = priceAiProductOffers(slug)
                if (rows.isEmpty()) {
                    // 该产品这一轮没有公开报价：保留上一轮同名产品数据，避免整块消失。
                    collected += previous.filter { normalizeSpec(it.product) == normalizeSpec(name) }
                    continue
                }
                collected += rows.mapNotNull { row ->
                    val title = row.path("sourceTitle").asText(row.path("sourceName").asText())
                    val price = row.path("price").asDouble(-1.0)
                    val url = safeUrl(row.path("url").asText()) ?: return@mapNotNull null
                    if (!price.isFinite() || price < 0) return@mapNotNull null
                    // hidden=true 是来源自己标记的不可展示报价，直接跳过。
                    if (row.path("hidden").asBoolean(false)) return@mapNotNull null
                    val stock = row.intOrNull("stockCount")?.takeIf { it >= 0 }
                    val effective = row.path("effectiveStatus").asText()
                    val status = row.path("status").asText()
                    val available = stock?.let { it > 0 } ?: when {
                        effective == "available" || status == "in_stock" || status == "low_stock" -> true
                        status == "out_of_stock" || effective == "unavailable" -> false
                        else -> null
                    }
                    Offer(
                        id = "priceai:${row.path("id").asText()}",
                        groupId = "priceai:$slug",
                        product = name,
                        category = category,
                        productType = productType(typeText, title),
                        billingPeriod = billingPeriod("${product.path("spec").asText()} $title"),
                        title = title,
                        shopName = row.path("sourceStoreName").asText(row.path("sourceName").asText()),
                        url = url,
                        price = price,
                        currency = row.path("currency").asText("CNY"),
                        stock = stock,
                        available = available,
                        minOrderQuantity = row.intOrNull("minOrderQuantity"),
                        source = "priceai",
                        capturedAt = row.textOrNull("capturedAt"),
                        syncedAt = Instant.now().toString(),
                        suspicious = price == 0.0 || SUSPECT.containsMatchIn(title)
                    )
                }
            } catch (ex: Exception) {
                failedProducts++
                log.warn("PriceAI 规格 {} 采集失败：{}", slug, ex.message)
                collected += previous.filter { it.groupId == "priceai:$slug" }
            }
        }
        val offers = collected.distinctBy { "${it.groupId}|${it.url}" }
        check(offers.isNotEmpty()) { "PriceAI 未返回有效报价" }
        if (failedProducts == 0) priceAiGeneration = generation
        return offers
    }

    /** 单个产品全部分页；失败重试两次，仍失败则抛出交由上层保留旧数据。 */
    private fun priceAiProductOffers(slug: String): List<JsonNode> {
        val rows = mutableListOf<JsonNode>()
        var offset = 0
        var page = 0
        while (page < PRICEAI_MAX_PAGES) {
            val payload = retryJson("https://priceai.cc/api/products/$slug/offers?offset=$offset")
            val items = payload.path("offers")
            check(items.isArray) { "PriceAI 报价格式变化" }
            if (items.size() == 0) break
            for (item in items) rows.add(item)
            val total = payload.path("total").asInt(0)
            offset += items.size()
            page++
            if (rows.size >= total || items.size() < PRICEAI_PAGE_SIZE) break
            Thread.sleep(PRICEAI_DELAY_MS)
        }
        return rows
    }

    /** 公开接口有频率限制，命中后按 retryAfter 等待再试。 */
    private fun retryJson(url: String): JsonNode {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return json(url)
            } catch (ex: Exception) {
                lastError = ex
                Thread.sleep(1200L + attempt * 1500L)
            }
        }
        throw lastError ?: IllegalStateException("请求失败")
    }

    private fun pickAi(): List<Offer> {
        val types = json("https://pickai.cc/api/backend/product-types")
        check(types.isArray && types.size() in 1..200) { "PickAI 商品规格格式变化" }
        val collected = java.util.Collections.synchronizedList(mutableListOf<Offer>())
        val tasks = types.map { type ->
            val id = type.path("id").asInt()
            val name = type.path("name").asText()
            val category = type.path("category").asText("其他")
            Runnable {
                try {
                    var page = 1
                    while (page <= MAX_PAGES) {
                        val rows = json("https://pickai.cc/api/backend/quotes?product_type_id=$id&page=$page&page_size=$PICKAI_PAGE_SIZE")
                        val items = rows.path("items")
                        check(items.isArray) { "PickAI 报价格式变化" }
                        if (items.isEmpty) break
                        for (row in items) {
                            val amount = row.path("price").asText()
                            val price = amount.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: continue
                            val currency = when {
                                amount.startsWith("¥") || amount.startsWith("￥") -> "CNY"
                                amount.startsWith("$") -> "USD"
                                else -> continue
                            }
                            if (!price.isFinite() || price < 0) continue
                            val title = row.path("raw_name").asText()
                            val url = safeUrl(row.path("item_url").asText()) ?: continue
                            val stockText = row.path("stock").asText()
                            val stock = Regex("库存\\s*([\\d,]+)").find(stockText)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
                            val available = stock?.let { it > 0 } ?: if (stockText.contains("无货") || stockText.contains("售罄")) false else null
                            collected += Offer(
                                id = "pickai:${row.path("id").asText()}", groupId = "pickai:$id", product = name,
                                category = category, productType = productType(name, title), billingPeriod = billingPeriod("$name $title"),
                                title = title, shopName = row.path("shop_name").asText(),
                                url = url, price = price, currency = currency, stock = stock, available = available, source = "pickai",
                                // PickAI 公开接口时间为 UTC 无偏移格式，统一补足 UTC 后交给客户端显示。
                                capturedAt = utcTimestamp(row.textOrNull("updated_at")), syncedAt = Instant.now().toString(),
                                suspicious = price == 0.0 || SUSPECT.containsMatchIn(title)
                            )
                        }
                        if (items.size() < PICKAI_PAGE_SIZE) break
                        page++
                    }
                } catch (ex: Exception) {
                    log.warn("PickAI 规格 {} 采集失败：{}", id, ex.message)
                }
            }
        }
        val futures = tasks.map { pool.submit(it) }
        futures.forEach { runCatching { it.get(120, TimeUnit.SECONDS) } }
        check(collected.isNotEmpty()) { "PickAI 未返回有效报价" }
        return collected.distinctBy { "${it.groupId}|${it.url}" }
    }

    /**
     * 形态推断：先看标准规格名，再看商品标题。
     * 只做粗分类，判断不了就落 unknown，不猜。
     */
    private fun productType(value: String, title: String = ""): String {
        val text = "$value $title"
        return when {
            Regex("成品号|普号|独享号|账号|号池|兑换号|接码|成品").containsMatchIn(text) -> "account"
            Regex("充值|代充|开通|升级").containsMatchIn(text) -> "recharge"
            Regex("合租|共享|拼车").containsMatchIn(text) -> "shared"
            Regex("API|CDK|额度|token", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "api"
            Regex("兑换码|礼品卡|激活码|卡密").containsMatchIn(text) -> "code"
            else -> "unknown"
        }
    }

    private fun billingPeriod(value: String): String = when {
        Regex("年|annual|year", RegexOption.IGNORE_CASE).containsMatchIn(value) -> "yearly"
        Regex("月|month", RegexOption.IGNORE_CASE).containsMatchIn(value) -> "monthly"
        Regex("日|天|周|短期|试用|日抛").containsMatchIn(value) -> "short"
        else -> "unknown"
    }

    private fun safeUrl(value: String): String? = runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null
    }?.toString()

    private fun utcTimestamp(value: String?): String? = value?.let {
        runCatching { Instant.parse(it).toString() }.getOrElse { _ ->
            runCatching { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC).toString() }.getOrNull()
        }
    }

    private fun JsonNode.textOrNull(key: String): String? = get(key)?.takeUnless { it.isNull }?.asText()
    private fun JsonNode.intOrNull(key: String): Int? = get(key)?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.asInt()
}
