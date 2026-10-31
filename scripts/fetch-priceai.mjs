/**
 * PriceAI 全量报价采集（带断点续传，避开公开接口限流）。
 * 输出：.cache-priceai/<slug>.json，供 build-price-seed.mjs 合并。
 * 用法：node scripts/fetch-priceai.mjs [--delay=1200] [--force]
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises';

const CACHE = new URL('../.cache-priceai/', import.meta.url);
const POINTER = 'https://data.priceai.cc/latest.json';
const PAGE_SIZE = 30;
const UA = 'Badge-PriceRadar/1.0';
const delay = Number(process.argv.find((x) => x.startsWith('--delay='))?.split('=')[1] || 1200);
const force = process.argv.includes('--force');
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

await mkdir(CACHE, { recursive: true });

async function getJson(url) {
  const response = await fetch(url, {
    headers: { 'User-Agent': UA, accept: 'application/json' },
    signal: AbortSignal.timeout(30000),
  });
  if (!response.ok) {
    const error = new Error(`${url} -> HTTP ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return JSON.parse(new TextDecoder('utf-8').decode(new Uint8Array(await response.arrayBuffer())));
}

const pointer = await getJson(POINTER);
const snapshot = await getJson(pointer.snapshot_url);
if (snapshot.schema_version !== 'price-radar.v1') throw new Error('快照格式变化');

/** 抓一个产品；429 时退避重试，仍然失败就放弃该产品并记录。 */
async function fetchProduct(product) {
  const file = new URL(`${product.id}.json`, CACHE);
  if (!force) {
    const existing = await readFile(file, 'utf8').catch(() => null);
    if (existing) {
      const rows = JSON.parse(existing);
      if (Array.isArray(rows) && rows.length) return { slug: product.id, rows: rows.length, cached: true };
    }
  }
  const rows = [];
  let offset = 0;
  let total = Infinity;
  for (let page = 0; page < 400 && rows.length < total; page += 1) {
    let payload = null;
    for (let attempt = 0; attempt < 6; attempt += 1) {
      try {
        payload = await getJson(
          `https://priceai.cc/api/products/${product.id}/offers?offset=${offset}`,
        );
        break;
      } catch (error) {
        if (error.status !== 429 && attempt >= 2) throw error;
        await sleep(delay * (attempt + 2));
      }
    }
    if (!payload) throw new Error('持续限流');
    const items = Array.isArray(payload.offers) ? payload.offers : [];
    if (!items.length) break;
    rows.push(...items);
    total = Number(payload.total) || rows.length;
    offset += items.length;
    if (items.length < PAGE_SIZE) break;
    await sleep(delay);
  }
  await writeFile(
    file,
    JSON.stringify({ product: { id: product.id, name: product.name, platform: product.platform, spec: product.spec, product_type: product.product_type }, rows }),
    'utf8',
  );
  return { slug: product.id, rows: rows.length, cached: false };
}

const products = snapshot.products.filter((p) => p.id);
console.log(`products=${products.length} delay=${delay}ms force=${force}`);
let done = 0;
let failed = 0;
for (const product of products) {
  try {
    const result = await fetchProduct(product);
    done += 1;
    process.stdout.write(
      `\r[${done}/${products.length}] ${result.slug} ${result.rows} rows${result.cached ? ' (cached)' : ''}        `,
    );
    if (!result.cached) await sleep(delay);
  } catch (error) {
    failed += 1;
    console.log(`\n  ${product.id} failed: ${error.message}`);
    await sleep(delay * 3);
  }
}
console.log(`\ndone products=${done} failed=${failed}`);
