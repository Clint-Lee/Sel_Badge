/**
 * 门户视觉与交互验收脚本：用 HotPot 里的 puppeteer 打开本地前端，
 * 截图 AI工具与低价库两个模块，并输出控制台错误与关键 DOM 计数。
 * 用法：node scripts/verify-portal.mjs [BASE_URL]
 */
import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const require = createRequire('D:/Extends/antigravityProj/HotPot/');
const puppeteer = require('puppeteer');

const BASE = process.argv[2] || 'http://127.0.0.1:5173';
// 截图写到系统临时目录，不污染仓库。
const OUT = join(tmpdir(), 'badge-portal-shots');
await mkdir(OUT, { recursive: true });
const shot = (name) => join(OUT, `${name}.png`);

const browser = await puppeteer.launch({
  headless: 'window',
  args: ['--no-sandbox', '--disable-dev-shm-usage', '--lang=zh-CN'],
});
const errors = [];

async function openPage(viewport) {
  const page = await browser.newPage();
  await page.setViewport(viewport);
  page.setDefaultNavigationTimeout(60000);
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });
  page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));
  return page;
}

async function visit(page, path, name, actions) {
  await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page
    .waitForFunction(() => document.querySelector('.portal-page, .price-page'), { timeout: 40000 })
    .catch(() => {});
  await new Promise((resolve) => setTimeout(resolve, 900));
  const info = await page.evaluate(() => ({
    h1: document.querySelector('h1')?.innerText || '',
    cards: document.querySelectorAll('.site-card').length,
    sidebarItems: document.querySelectorAll('.side-item').length,
    categoryColumns: document.querySelectorAll('.category-column').length,
    productCards: document.querySelectorAll('.price-product-card').length,
    offerRows: document.querySelectorAll('.offer-table tbody tr').length,
    categoryTabs: document.querySelectorAll('.price-categories button').length,
    firstSite: document.querySelector('.site-card-brand h3 button')?.innerText || '',
    firstProduct: document.querySelector('.price-card-title')?.innerText || '',
    firstPrice: document.querySelector('.price-card-minimum strong')?.innerText || '',
  }));
  if (actions) await actions();
  await page.screenshot({ path: shot(name), fullPage: true });
  console.log(name, JSON.stringify(info));
}

const desktop = { width: 1440, height: 1000 };
let page = await openPage(desktop);
await visit(page, '/tools', 'tools-desktop');

page = await openPage(desktop);
await visit(page, '/tools?view=category&category=ai-video', 'tools-category');

page = await openPage(desktop);
await visit(page, '/tools?q=接码', 'tools-search');

page = await openPage(desktop);
await visit(page, '/suppliers', 'suppliers-desktop');

page = await openPage(desktop);
await visit(page, '/suppliers', 'suppliers-detail', async () => {
  const handle = await page.$('.price-product-card');
  if (!handle) return;
  await handle.click();
  await page
    .waitForSelector('.offer-table tbody tr, .price-detail .portal-state', { timeout: 40000 })
    .catch(() => {});
  await new Promise((resolve) => setTimeout(resolve, 1200));
  const detail = await page.evaluate(() => ({
    rows: document.querySelectorAll('.offer-table tbody tr').length,
    shop: document.querySelector('.offer-product-cell strong')?.innerText || '',
    price: document.querySelector('.offer-price-cell strong')?.innerText || '',
    stock: document.querySelector('.stock-badge')?.innerText || '',
    title: (document.querySelector('.offer-product-cell p')?.innerText || '').slice(0, 36),
    total: document.querySelector('.price-time-note')?.innerText || '',
  }));
  console.log('suppliers-detail-data', JSON.stringify(detail));
});

const mobile = { width: 390, height: 844, isMobile: true, hasTouch: true };
page = await openPage(mobile);
await visit(page, '/tools', 'tools-mobile');

page = await openPage(mobile);
await visit(page, '/suppliers', 'suppliers-mobile');

console.log('console errors:', errors.length ? JSON.stringify(errors.slice(0, 8)) : 'none');
await browser.close();
