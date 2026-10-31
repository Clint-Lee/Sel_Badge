/**
 * 生成低价库报价种子快照（与后端 PriceFeedService 的采集与过滤规则保持一致）。
 * 数据来源：
 *  - PickAI 公开接口 /api/backend/product-types 与 /api/backend/quotes（每个规格全部分页）
 *  - PriceAI 官方 /api/products/{slug}/offers（每个产品全部分页，每页 30 条，有限流）
 * 用法：node scripts/build-price-seed.mjs [--max-pages=80] [--skip-priceai]
 */
import { writeFile } from 'node:fs/promises';

const OUT = new URL('../backend/src/main/resources/portal/prices.json', import.meta.url);
const PICKAI_PAGE_SIZE = 50;
const PRICEAI_PAGE_SIZE = 30;
const CONCURRENCY = 6;
const PRICEAI_DELAY_MS = 900;
const MAX_PAGES = Number(
  process.argv.find((x) => x.startsWith('--max-pages='))?.split('=')[1] || 80,
);
const SKIP_PRICEAI = process.argv.includes('--skip-priceai');

const UA = 'Badge-PriceRadar/1.0';
const SUSPECT = /勿拍|禁止下单|说明链接|非实际报价|测试商品|定金|补差价/;
/** 规格不符：标题与本标准规格明显不是同一商品（引流、跨产品、资格号等）。 */
const SPEC_MISMATCH =
  /muse|token\s*成品号|资格号|只是资格|半价资格|非本商品|他站|引流|补差价|定金|勿拍|联系客服|代拍|占位|测试商品/i;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** 上游返回 GBK 头但正文是 UTF-8，按字节解码避免乱码。 */
async function getJson(url, attempts = 3) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const response = await fetch(url, {
        headers: { 'User-Agent': UA, accept: 'application/json' },
        signal: AbortSignal.timeout(30000),
      });
      if (!response.ok) throw new Error(`${url} -> HTTP ${response.status}`);
      const bytes = new Uint8Array(await response.arrayBuffer());
      return JSON.parse(new TextDecoder('utf-8').decode(bytes));
    } catch (error) {
      if (attempt === attempts - 1) throw error;
      await sleep(1200 + attempt * 1500);
    }
  }
}

async function pool(items, limit, worker) {
  const results = new Array(items.length);
  let cursor = 0;
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor;
      cursor += 1;
      results[index] = await worker(items[index], index);
    }
  });
  await Promise.all(runners);
  return results;
}

const safeUrl = (value) => {
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password
      ? url.toString()
      : null;
  } catch {
    return null;
  }
};

const utcTimestamp = (value) => {
  if (!value) return null;
  const withZone = /Z$|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}Z`;
  const parsed = Date.parse(withZone);
  return Number.isFinite(parsed) ? new Date(parsed).toISOString() : null;
};

const productType = (value, title = '') => {
  const text = `${value} ${title}`;
  if (/成品号|普号|独享号|账号|号池|兑换号|接码|成品/.test(text)) return 'account';
  if (/充值|代充|开通|升级/.test(text)) return 'recharge';
  if (/合租|共享|拼车/.test(text)) return 'shared';
  if (/API|CDK|额度|token/i.test(text)) return 'api';
  if (/兑换码|礼品卡|激活码|卡密/.test(text)) return 'code';
  return 'unknown';
};

const billingPeriod = (value) => {
  if (/年|annual|year/i.test(value)) return 'yearly';
  if (/月|month/i.test(value)) return 'monthly';
  if (/日|天|周|短期|试用|日抛/.test(value)) return 'short';
  return 'unknown';
};

const syncedAt = new Date().toISOString();

// ---------- PickAI ----------
const types = await getJson('https://pickai.cc/api/backend/product-types');
console.log(`pickai product-types: ${types.length}`);
const pickaiOffers = [];

await pool(types, CONCURRENCY, async (type) => {
  let page = 1;
  while (page <= MAX_PAGES) {
    const payload = await getJson(
      `https://pickai.cc/api/backend/quotes?product_type_id=${type.id}&page=${page}&page_size=${PICKAI_PAGE_SIZE}`,
    );
    const items = Array.isArray(payload.items) ? payload.items : [];
    if (!items.length) break;
    for (const row of items) {
      const amount = String(row.price ?? '');
      const price = Number.parseFloat(amount.replace(/[^0-9.]/g, ''));
      const currency = amount.startsWith('¥') || amount.startsWith('￥')
        ? 'CNY'
        : amount.startsWith('$')
          ? 'USD'
          : '';
      const url = safeUrl(row.item_url);
      if (!Number.isFinite(price) || price < 0 || !currency || !url) continue;
      const stockText = String(row.stock ?? '');
      const stockMatch = /库存\s*([\d,]+)/.exec(stockText);
      const stock = stockMatch ? Number(stockMatch[1].replace(/,/g, '')) : null;
      const available = stock !== null ? stock > 0 : /无货|售罄/.test(stockText) ? false : null;
      const title = String(row.raw_name ?? '');
      pickaiOffers.push({
        id: `pickai:${row.id}`,
        groupId: `pickai:${type.id}`,
        product: type.name,
        category: type.category,
        productType: productType(type.name, title),
        billingPeriod: billingPeriod(`${type.name} ${title}`),
        title,
        shopName: String(row.shop_name ?? ''),
        url,
        price,
        currency,
        stock,
        available,
        minOrderQuantity: null,
        source: 'pickai',
        capturedAt: utcTimestamp(row.updated_at),
        syncedAt,
        suspicious: price === 0 || SUSPECT.test(title),
      });
    }
    if (items.length < PICKAI_PAGE_SIZE || !payload.has_more) break;
    page += 1;
  }
});
console.log(`pickai offers: ${pickaiOffers.length}`);

// ---------- PriceAI ----------
let priceaiOffers = [];
let priceaiNote = '官方公开接口：每个标准规格的全部分页报价（每页 30 条）';
if (!SKIP_PRICEAI) {
  try {
    const pointer = await getJson('https://data.priceai.cc/latest.json');
    const snapshotUrl = new URL(pointer.snapshot_url);
    if (snapshotUrl.host !== 'data.priceai.cc' || !snapshotUrl.pathname.startsWith('/v1/snapshots/')) {
      throw new Error('官方快照地址异常');
    }
    const root = await getJson(snapshotUrl.toString());
    if (root.schema_version !== 'price-radar.v1' || !Array.isArray(root.products)) {
      throw new Error('Price Radar 格式变化');
    }
    let failed = 0;
    for (const product of root.products) {
      const slug = product.id;
      if (!slug) continue;
      try {
        let offset = 0;
        let rows = 0;
        const collected = [];
        for (let page = 0; page < MAX_PAGES; page += 1) {
          const payload = await getJson(
            `https://priceai.cc/api/products/${slug}/offers?offset=${offset}`,
          );
          const items = Array.isArray(payload.offers) ? payload.offers : [];
          if (!items.length) break;
          collected.push(...items);
          const total = Number(payload.total) || 0;
          offset += items.length;
          rows = collected.length;
          if (rows >= total || items.length < PRICEAI_PAGE_SIZE) break;
          await sleep(PRICEAI_DELAY_MS);
        }
        for (const row of collected) {
          if (row.hidden) continue;
          const title = String(row.sourceTitle ?? row.sourceName ?? '');
          const price = Number(row.price);
          const url = safeUrl(row.url);
          if (!url || !Number.isFinite(price) || price < 0) continue;
          const stock = Number.isInteger(row.stockCount) && row.stockCount >= 0 ? row.stockCount : null;
          const available =
            stock !== null
              ? stock > 0
              : ['in_stock', 'low_stock'].includes(row.status) || row.effectiveStatus === 'available'
                ? true
                : row.status === 'out_of_stock'
                  ? false
                  : null;
          priceaiOffers.push({
            id: `priceai:${row.id}`,
            groupId: `priceai:${slug}`,
            product: product.name,
            category: product.platform || '其他',
            productType: productType(`${product.product_type ?? ''} ${product.name}`, title),
            billingPeriod: billingPeriod(`${product.spec ?? ''} ${title}`),
            title,
            shopName: String(row.sourceStoreName ?? row.sourceName ?? ''),
            url,
            price,
            currency: row.currency || 'CNY',
            stock,
            available,
            minOrderQuantity: Number.isInteger(row.minOrderQuantity) ? row.minOrderQuantity : null,
            source: 'priceai',
            capturedAt: utcTimestamp(row.capturedAt),
            syncedAt,
            suspicious: price === 0 || SUSPECT.test(title),
          });
        }
        process.stdout.write(`\r  priceai ${slug}: ${rows} rows        `);
      } catch (error) {
        failed += 1;
        console.log(`\n  priceai ${slug} skipped: ${error.message}`);
      }
    }
    console.log(`\npriceai offers: ${priceaiOffers.length} (failed products: ${failed})`);
  } catch (error) {
    console.warn(`priceai skipped: ${error.message}`);
    priceaiNote = '本次生成未取到 PriceAI 快照，运行时同步会重新读取';
  }
}

const dedupe = (rows) => {
  const map = new Map();
  for (const row of rows) map.set(`${row.groupId}|${row.url}`, row);
  return [...map.values()];
};

const offers = dedupe([...priceaiOffers, ...pickaiOffers]);
const sources = [
  {
    source: 'priceai',
    status: priceaiOffers.length ? 'complete' : 'failed',
    lastSuccessAt: priceaiOffers.length ? syncedAt : null,
    attemptedAt: syncedAt,
    offers: priceaiOffers.length,
    message: priceaiNote,
    details: {
      products: new Set(priceaiOffers.map((x) => x.product)).size,
      rows: priceaiOffers.length,
    },
  },
  {
    source: 'pickai',
    status: pickaiOffers.length ? 'complete' : 'failed',
    lastSuccessAt: pickaiOffers.length ? syncedAt : null,
    attemptedAt: syncedAt,
    offers: pickaiOffers.length,
    message: '每个标准规格的全部分页公开报价',
    details: {
      products: new Set(pickaiOffers.map((x) => x.product)).size,
      rows: pickaiOffers.length,
    },
  },
];

const snapshot = {
  offers,
  sources,
  intervalMinutes: 15,
  updatedAt: syncedAt,
  totalLinks: offers.length,
};

await writeFile(OUT, JSON.stringify(snapshot) + '\n', 'utf8');

/** 与后端 buildGroups 相同的合并与最低价规则，用于自检。 */
const normalizeSpec = (value) => value.toLowerCase().replace(/[\s/·、，,]+/g, '').replace(/／/g, '');
const isQuotable = (offer, quality = true) => {
  if (offer.available === false) return false;
  if (!Number.isFinite(offer.price) || offer.price <= 0) return false;
  if (!quality) return true;
  return !offer.suspicious && !SPEC_MISMATCH.test(offer.title);
};
const groups = new Map();
for (const offer of offers) {
  const key = `${normalizeSpec(offer.product)}#${offer.productType}`;
  if (!groups.has(key)) groups.set(key, { id: key, product: offer.product, rows: [] });
  groups.get(key).rows.push(offer);
}
let withMinima = 0;
for (const group of groups.values()) {
  const byCurrency = new Map();
  for (const row of group.rows.filter((x) => isQuotable(x))) {
    if (!byCurrency.has(row.currency)) byCurrency.set(row.currency, []);
    byCurrency.get(row.currency).push(row.price);
  }
  if (byCurrency.size) withMinima += 1;
}

console.log(
  `offers=${offers.length} groups=${groups.size} withMinima=${withMinima} file=${(
    JSON.stringify(snapshot).length / 1024 / 1024
  ).toFixed(2)} MB`,
);
