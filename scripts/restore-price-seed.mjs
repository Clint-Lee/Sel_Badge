/**
 * 把运行库里的报价快照回填为种子文件（离线可用，不发起任何网络请求）。
 * 用途：在采集脚本无法联网时，保留已抓到的双来源数据作为种子基线。
 * 用法：node scripts/restore-price-seed.mjs
 */
import { readFile, writeFile } from 'node:fs/promises';

const SOURCE = new URL('../backend/data/portal/prices.json', import.meta.url);
const TARGET = new URL('../backend/src/main/resources/portal/prices.json', import.meta.url);

const snapshot = JSON.parse((await readFile(SOURCE, 'utf8')).replace(/^\uFEFF/, ''));
if (!Array.isArray(snapshot.offers) || !snapshot.offers.length) {
  throw new Error('运行库快照为空，无法回填');
}

const bySource = {};
for (const offer of snapshot.offers) bySource[offer.source] = (bySource[offer.source] || 0) + 1;

// 重新计算来源状态里的条数与规格数，避免沿用过期的统计。
snapshot.sources = (snapshot.sources || []).map((source) => {
  const rows = snapshot.offers.filter((x) => x.source === source.source);
  return {
    ...source,
    offers: rows.length,
    details: {
      products: new Set(rows.map((x) => x.product)).size,
      rows: rows.length,
    },
  };
});
snapshot.totalLinks = snapshot.offers.length;

await writeFile(TARGET, `${JSON.stringify(snapshot)}\n`, 'utf8');
console.log(
  `restored offers=${snapshot.offers.length} ${JSON.stringify(bySource)} -> resources/portal/prices.json`,
);
