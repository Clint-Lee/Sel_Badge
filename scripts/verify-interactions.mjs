/**
 * 门户交互验收：游客收藏、登录后收录弹窗、低价库筛选与报价分页。
 * 依赖本地已启动的前后端，以及 HotPot 中的 puppeteer。
 * 用法：node scripts/verify-interactions.mjs
 */
import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const require = createRequire('D:/Extends/antigravityProj/HotPot/');
const puppeteer = require('puppeteer');

const OUT = join(tmpdir(), 'badge-portal-shots');
await mkdir(OUT, { recursive: true });
const BASE = process.argv[2] || 'http://127.0.0.1:5173';
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const browser = await puppeteer.launch({ headless: 'window', args: ['--no-sandbox'] });
const errors = [];

/** 每个检查点独立开页，避免上一页残留的 SPA 定时器影响下一次导航。 */
async function open(path) {
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 1000 });
  page.setDefaultNavigationTimeout(60000);
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });
  await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await wait(1600);
  return page;
}

// 1) 游客收藏保存在浏览器，切到「我的收藏」应只剩这一张卡。
let page = await open('/tools');
await page.click('.site-card .favorite-btn');
await wait(900);
const savedName = await page.evaluate(
  () => document.querySelector('.site-card .site-card-brand h3 button')?.innerText || '',
);
await page.close();

page = await open('/tools?view=favorites');
const favorites = await page.evaluate(() => ({
  cards: document.querySelectorAll('.site-card').length,
  first: document.querySelector('.site-card-brand h3 button')?.innerText || '',
  hint: [...document.querySelectorAll('.directory-section-heading p')].map((x) => x.innerText).join(' | '),
}));
console.log('favorites', JSON.stringify({ savedName, ...favorites }));
await page.screenshot({ path: join(OUT, 'tools-favorites.png') });
await page.close();

// 2) 登录后打开收录弹窗，确认三种收录类型与联系方式字段都在。
page = await open('/login');
const inputs = await page.$$('input');
await inputs[0].type('admin');
await inputs[1].type('Admin@123456');
await page.click('button[type="submit"], .btn-primary').catch(() => {});
await wait(2500);
const token = await page.evaluate(() => localStorage.getItem('badge_auth_token'));
console.log('login token:', Boolean(token));
await page.close();

page = await open('/tools?submit=1');
const dialog = await page.evaluate(() => ({
  open: Boolean(document.querySelector('dialog.portal-dialog')),
  title: document.querySelector('.dialog-heading h2')?.innerText || '',
  kinds: [...document.querySelectorAll('.kind-chip')].map((x) => x.innerText),
  legends: [...document.querySelectorAll('.contact-fieldset legend')].map((x) => x.innerText),
  fields: [...document.querySelectorAll('.portal-form > label')].map((x) => x.innerText.split('\n')[0]),
}));
console.log('submission dialog', JSON.stringify(dialog));
await page.screenshot({ path: join(OUT, 'submission-dialog.png') });
await page.evaluate(() => [...document.querySelectorAll('.kind-chip')][1]?.click());
await wait(600);
const merchantHint = await page.evaluate(() =>
  [...document.querySelectorAll('.contact-fieldset p')].map((x) => x.innerText).join(' | '),
);
console.log('merchant hint:', merchantHint.slice(0, 80));
await page.close();

// 3) 低价库形态筛选与关键词搜索。
page = await open('/suppliers?type=recharge');
const filtered = await page.evaluate(() => ({
  cards: document.querySelectorAll('.price-product-card').length,
  titles: [...document.querySelectorAll('.price-card-title')].slice(0, 4).map((x) => x.innerText),
}));
console.log('type=recharge', JSON.stringify(filtered));
await page.close();

page = await open('/suppliers?q=Claude');
const search = await page.evaluate(() => ({
  cards: document.querySelectorAll('.price-product-card').length,
  first: document.querySelector('.price-card-title')?.innerText || '',
}));
console.log('q=Claude', JSON.stringify(search));
await page.close();

// 4) 商家报价翻页：直接从选品台点开第一张规格卡，避免依赖固定分组 ID。
page = await open('/suppliers');
const picked = await page.evaluate(() => {
  const card = document.querySelector('.price-product-card');
  return {
    title: card?.querySelector('.price-card-title')?.innerText || '',
    lowest: card?.querySelector('.price-card-minimum strong')?.innerText || '',
    sources: card?.querySelector('.price-card-meta')?.innerText || '',
  };
});
await page.click('.price-product-card');
await wait(2200);
const page1 = await page.evaluate(() => ({
  rows: document.querySelectorAll('.offer-table tbody tr').length,
  note: document.querySelector('.price-time-note')?.innerText || '',
  firstPrice: document.querySelector('.offer-price-cell strong')?.innerText || '',
  search: location.search,
}));
await page.evaluate(() => {
  const buttons = [...document.querySelectorAll('.price-offer-pagination button')];
  buttons[buttons.length - 1]?.click();
});
await wait(1800);
const page2 = await page.evaluate(() => ({
  rows: document.querySelectorAll('.offer-table tbody tr').length,
  note: document.querySelector('.price-time-note')?.innerText || '',
  search: location.search,
}));
console.log('picked card', JSON.stringify(picked));
console.log('offers page1', JSON.stringify(page1));
console.log('offers page2', JSON.stringify(page2));
await page.close();

// 5) 分类看板截图。
page = await open('/tools');
await page.evaluate(() => document.querySelector('.category-columns')?.scrollIntoView({ block: 'start' }));
await wait(700);
await page.screenshot({ path: join(OUT, 'tools-categories.png') });
await page.close();

console.log('errors:', errors.length ? JSON.stringify(errors.slice(0, 6)) : 'none');
await browser.close();
