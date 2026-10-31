/**
 * 生成 AI工具（资源导航）种子数据。
 * 数据来源：公开可访问的站点目录接口 + 仓库内已有工具清单。
 * 用法：node scripts/build-directory-seed.mjs
 */
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';

// 直接读取公开站点目录接口；返回 GBK 头但正文是 UTF-8，按字节解码避免乱码。
const SITES_API = 'https://www.700sb.com/api/sites';
const CATEGORIES_API = 'https://www.700sb.com/api/categories';
const OUT = new URL('../backend/src/main/resources/portal/sites.json', import.meta.url);
const TOOLS = new URL('../backend/src/main/resources/portal/tools.json', import.meta.url);

// UUID 形态的分类标识替换为可读 id，便于前端路由与查询。
const CATEGORY_ALIAS = {
  '91f4b417-5df7-408e-aa07-3fc1484d754c': 'virtual-cards',
  'f8abfde1-3118-4e45-9f0f-931b48a6d117': 'skills',
};

// 旧工具清单的 8 个场景映射到导航分类。
const TOOL_CATEGORY = {
  assistant: 'ai-models',
  image: 'ai',
  video: 'ai-video',
  coding: 'gpt-tools',
  audio: 'ai',
  agent: 'ai',
  api: 'ai-models',
  learning: 'resources',
};

const slug = (value) =>
  createHash('sha1').update(value).digest('hex').slice(0, 12);

async function json(url) {
  if (typeof url !== 'string') return JSON.parse((await readFile(url, 'utf8')).replace(/^\uFEFF/, ''));
  const response = await fetch(url, {
    headers: { 'User-Agent': 'Badge-Directory-Sync/1.0', accept: 'application/json' },
    signal: AbortSignal.timeout(30000),
  });
  if (!response.ok) throw new Error(`${url} -> HTTP ${response.status}`);
  return JSON.parse(new TextDecoder('utf-8').decode(new Uint8Array(await response.arrayBuffer())));
}

const [sitePayload, categoryPayload, toolPayload] = await Promise.all([
  json(SITES_API),
  json(CATEGORIES_API),
  readFile(TOOLS, 'utf8').then((text) => JSON.parse(text)),
]);

const categories = categoryPayload.categories.map((item) => ({
  id: CATEGORY_ALIAS[item.id] || item.id,
  name: item.name,
  description: item.description,
}));

const known = new Set(categories.map((item) => item.id));
const sites = [];
const seen = new Set();
const push = (site) => {
  const key = site.url.replace(/\/$/, '').toLowerCase();
  if (seen.has(key)) return;
  seen.add(key);
  sites.push(site);
};

for (const item of sitePayload.sites) {
  const category = CATEGORY_ALIAS[item.category] || item.category;
  push({
    id: item.id,
    name: item.name,
    url: item.url,
    description: item.description || '',
    category: known.has(category) ? category : 'ai',
    tags: Array.isArray(item.tags) ? item.tags.filter(Boolean).slice(0, 10) : [],
    featured: Boolean(item.featured),
    visits: Number(item.visits) || 0,
    todayClicks: Number(item.todayClicks) || 0,
    createdAt: item.createdAt || new Date().toISOString(),
    sponsorPinUntil: null,
    relayOffer: item.relayOffer
      ? {
          amountCents: Number(item.relayOffer.amountCents) || 0,
          claimInstructions: item.relayOffer.claimInstructions || '',
        }
      : null,
    hasContact: Boolean(item.hasContact),
    source: 'directory',
  });
}

for (const tool of toolPayload.tools || []) {
  push({
    id: `tool-${slug(tool.url)}`,
    name: tool.name,
    url: tool.url,
    description: tool.description || '',
    category: TOOL_CATEGORY[tool.category] || 'ai',
    tags: Array.isArray(tool.tags) ? tool.tags.slice(0, 10) : [],
    featured: Boolean(tool.featured),
    visits: Number(tool.visits) || 0,
    todayClicks: 0,
    createdAt: tool.createdAt || new Date().toISOString(),
    sponsorPinUntil: null,
    relayOffer: null,
    hasContact: false,
    source: 'catalog',
  });
}

const counts = {};
for (const site of sites) counts[site.category] = (counts[site.category] || 0) + 1;

const seed = {
  updatedAt: new Date().toISOString(),
  categories: categories.map((item) => ({ ...item, count: counts[item.id] || 0 })),
  sites,
  submissions: [],
  reviews: [],
};

await mkdir(new URL('.', OUT), { recursive: true });
await writeFile(OUT, `${JSON.stringify(seed, null, 2)}\n`, 'utf8');

console.log(`sites=${sites.length} categories=${categories.length}`);
for (const item of seed.categories) console.log(`  ${item.id.padEnd(16)} ${item.count}  ${item.name}`);
