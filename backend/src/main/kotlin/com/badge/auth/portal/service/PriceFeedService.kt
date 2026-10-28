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

/** 直接读取公开数据源；单次同步失败保留该来源上次成功快照。 */
@Service
class PriceFeedService(
    private val store: PortalFileStore,
    @Value("\${badge.portal.sync-enabled:true}") private val syncEnabled: Boolean
) {
    companion object {
        private const val SNAPSHOT_FILE = "prices.json"
        private const val MAX_RESPONSE_BYTES = 12 * 1024 * 1024
        private const val PICKAI_PAGE_SIZE = 50
        private val SUSPECT = Regex("勿拍|禁止下单|说明链接|非实际报价|测试商品|定金|补差价")
    }

    private val log = LoggerFactory.getLogger(javaClass)
    private val client by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() }
    @Volatile private var syncing = false
    @Volatile private var snapshot = store.read(SNAPSHOT_FILE, PriceSnapshot::class.java) {
        ClassPathResource("portal/prices.json").inputStream.use { store.mapper.readValue(it, PriceSnapshot::class.java) }
    }
    private var priceAiGeneration: String? = null

    fun snapshot(): PriceSnapshot = snapshot

    /** 后台定时读取；前台刷新只读本地快照，避免用户请求放大采集流量。 */
    @Scheduled(initialDelay = 20000, fixedDelay = 900000)
    fun sync() {
        if (!syncEnabled || syncing) return
        syncing = true
        try {
            for (source in listOf("priceai", "pickai")) {
                val attemptedAt = Instant.now().toString()
                val previous = snapshot.sources.find { it.source == source }
                try {
                    val offers = if (source == "priceai") priceAi() else pickAi()
                    check(offers.isNotEmpty()) { "来源未返回有效报价，保留旧快照" }
                    val note = if (source == "priceai") "官方 Price Radar：每规格 Top 5 与公开预设，非全量报价" else "各标准规格价格排序前 50 条，非全量报价"
                    val updated = snapshot.copy(
                        offers = snapshot.offers.filter { it.source != source } + offers,
                        sources = snapshot.sources.filter { it.source != source } + SourceStatus(source, "success", Instant.now().toString(), attemptedAt, offers.size, note)
                    )
                    store.write(SNAPSHOT_FILE, updated)
                    snapshot = updated
                } catch (ex: Exception) {
                    log.warn("Portal price feed {} failed: {}", source, ex.message)
                    val failed = snapshot.copy(sources = snapshot.sources.filter { it.source != source } +
                        SourceStatus(source, "error", previous?.lastSuccessAt, attemptedAt, snapshot.offers.count { it.source == source }, "同步失败，保留上次快照"))
                    store.write(SNAPSHOT_FILE, failed)
                    snapshot = failed
                }
            }
        } finally { syncing = false }
    }

    private fun json(url: String): JsonNode {
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(25))
            .header("User-Agent", "Badge-PriceRadar/1.0").header("Accept", "application/json").GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
            val bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1)
            check(bytes.size <= MAX_RESPONSE_BYTES) { "来源数据超过大小限制" }
            return store.mapper.readTree(bytes)
        }
    }

    private fun priceAi(): List<Offer> {
        val pointer = json("https://data.priceai.cc/latest.json")
        val generation = pointer.path("snapshot_id").asText()
        if (generation.isNotBlank() && generation == priceAiGeneration) return snapshot.offers.filter { it.source == "priceai" }
        val uri = URI(pointer.path("snapshot_url").asText())
        // 只跟随官方快照域，避免来源响应将请求导向内网或其他站点。
        check(uri.scheme == "https" && uri.host == "data.priceai.cc" && uri.userInfo == null && uri.port == -1 && uri.path.startsWith("/v1/snapshots/")) { "无效的官方快照地址" }
        val root = json(uri.toString())
        check(root.path("schema_version").asText() == "price-radar.v1" && root.path("products").isArray) { "Price Radar 格式变化" }
        val offers = root.path("products").flatMap { product ->
            val entries = product.path("top_offers").toList() + product.path("presets").flatMap { it.path("top_offers").toList() }
            entries.mapNotNull { row ->
                val title = row.path("title").asText()
                val price = row.path("price").asDouble(-1.0)
                val url = safeUrl(row.path("url").asText()) ?: return@mapNotNull null
                if (!price.isFinite() || price < 0) return@mapNotNull null
                val stock = row.intOrNull("stock_count")?.takeIf { it >= 0 }
                val status = row.path("status").asText()
                val available = stock?.let { it > 0 } ?: when (status) { "in_stock", "low_stock" -> true; "out_of_stock" -> false; else -> null }
                Offer(
                    id = "priceai:${row.path("id").asText()}", groupId = "priceai:${product.path("id").asText()}",
                    product = product.path("name").asText(), category = product.path("platform").asText("其他"),
                    productType = productType(product.path("product_type").asText()),
                    title = title, shopName = row.path("source_store_name").asText(row.path("source_name").asText()),
                    url = url, price = price, currency = row.path("currency").asText("CNY"), stock = stock,
                    available = available, minOrderQuantity = row.intOrNull("min_order_quantity"), source = "priceai",
                    capturedAt = row.textOrNull("captured_at") ?: product.textOrNull("snapshot_generated_at"),
                    syncedAt = Instant.now().toString(), suspicious = price == 0.0 || SUSPECT.containsMatchIn(title)
                )
            }
        }.distinctBy { "${it.groupId}|${it.url}" }
        if (offers.isNotEmpty()) priceAiGeneration = generation
        return offers
    }

    private fun pickAi(): List<Offer> {
        val types = json("https://pickai.cc/api/backend/product-types")
        check(types.isArray && types.size() in 1..200) { "PickAI 商品规格格式变化" }
        val offers = mutableListOf<Offer>()
        for (type in types) {
            val id = type.path("id").asInt()
            val rows = json("https://pickai.cc/api/backend/quotes?product_type_id=$id&page=1&page_size=$PICKAI_PAGE_SIZE")
            check(rows.path("items").isArray) { "PickAI 报价格式变化" }
            for (row in rows.path("items")) {
                val amount = row.path("price").asText()
                val price = amount.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: continue
                val currency = when { amount.startsWith("¥") || amount.startsWith("￥") -> "CNY"; amount.startsWith("$") -> "USD"; else -> continue }
                if (!price.isFinite() || price < 0) continue
                val title = row.path("raw_name").asText()
                val url = safeUrl(row.path("item_url").asText()) ?: continue
                val stockText = row.path("stock").asText()
                val stock = Regex("库存\\s*(\\d+)").find(stockText)?.groupValues?.get(1)?.toIntOrNull()
                val available = stock?.let { it > 0 } ?: if (stockText.contains("无货") || stockText.contains("售罄")) false else null
                offers += Offer(
                    id = "pickai:${row.path("id").asText()}", groupId = "pickai:$id", product = type.path("name").asText(),
                    category = type.path("category").asText("其他"), title = title, shopName = row.path("shop_name").asText(),
                    url = url, price = price, currency = currency, stock = stock, available = available, source = "pickai",
                    // PickAI 公开接口时间为 UTC 无偏移格式，统一补足 UTC 后交给客户端显示。
                    capturedAt = utcTimestamp(row.textOrNull("updated_at")), syncedAt = Instant.now().toString(),
                    suspicious = price == 0.0 || SUSPECT.containsMatchIn(title)
                )
            }
        }
        return offers.distinctBy { "${it.groupId}|${it.url}" }
    }

    private fun productType(value: String): String = when {
        value.contains("账号") -> "account"; value.contains("充值") -> "recharge"; value.contains("合租") -> "shared"
        value.contains("API", true) -> "api"; value.contains("码") -> "code"; else -> "unknown"
    }

    private fun safeUrl(value: String): String? = runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null
    }?.toString()

    private fun utcTimestamp(value: String?): String? = value?.let {
        runCatching { Instant.parse(it).toString() }.getOrElse { _ -> runCatching { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC).toString() }.getOrNull() }
    }

    private fun JsonNode.textOrNull(key: String): String? = get(key)?.takeUnless { it.isNull }?.asText()
    private fun JsonNode.intOrNull(key: String): Int? = get(key)?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.asInt()
}
