import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { transformWithOxc } from "vite";

const filename = new URL("../src/utils/portal.ts", import.meta.url);
const { code } = await transformWithOxc(
  await readFile(filename, "utf8"),
  filename.pathname,
  { lang: "ts" },
);
const { groupOffers, filterOffers, sortOffers, safeLink, emptyPreferences } =
  await import(
    `data:text/javascript;base64,${Buffer.from(code).toString("base64")}`
  );
const offer = (values = {}) => ({
  id: "1",
  groupId: "exact-spec",
  product: "Monthly",
  category: "AI",
  source: "pickai",
  title: "Regular subscription",
  shopName: "Shop",
  url: "https://example.com/product",
  price: 10,
  currency: "CNY",
  available: true,
  stock: 2,
  capturedAt: "2026-10-28T00:00:00Z",
  suspicious: false,
  ...values,
});

test("minima remain separate by currency and exclude sold-out offers", () => {
  const groups = groupOffers([
    offer(),
    offer({ id: "2", price: 2, currency: "USD" }),
    offer({ id: "3", price: 1, available: false }),
    offer({ id: "4", price: 8, available: null }),
  ]);
  assert.deepEqual(groups[0].prices, [
    { currency: "CNY", price: 8 },
    { currency: "USD", price: 2 },
  ]);
});

test("similarly named specifications from different sources never merge", () => {
  assert.equal(
    groupOffers([
      offer(),
      offer({ groupId: "other-source-spec", source: "priceai" }),
    ]).length,
    2,
  );
});

test("unknown stock does not pass the explicitly-in-stock filter", () => {
  assert.equal(
    filterOffers(
      [offer({ stock: null, available: null }), offer()],
      emptyPreferences(),
      true,
      true,
    ).length,
    1,
  );
});

test("blacklists match merchant names and new product titles case-insensitively", () => {
  const prefs = {
    ...emptyPreferences(),
    blockedKeywords: ["shop"],
    blockedUrls: ["https://example.com/blocked"],
  };
  assert.equal(
    filterOffers(
      [
        offer(),
        offer({ shopName: "Other", url: "https://example.com/blocked" }),
        offer({ shopName: "Other", title: "SHOP new offer" }),
      ],
      prefs,
    ).length,
    0,
  );
});

test("suspect and unsafe offers are filtered without inventing a price", () => {
  const offers = [
    offer({ suspicious: true }),
    offer({ url: "javascript:alert(1)" }),
    offer({ price: NaN }),
  ];
  assert.equal(filterOffers(offers, emptyPreferences()).length, 0);
  assert.equal(filterOffers(offers, emptyPreferences(), false).length, 1);
  assert.equal(safeLink("https://user:password@example.com"), "");
  assert.equal(safeLink("//example.com"), "");
});

test("price sorting groups currencies and stock sorting places unknown counts last", () => {
  const rows = [
    offer({ price: 9, currency: "USD", stock: null }),
    offer({ id: "2", price: 20, stock: 3 }),
    offer({ id: "3", price: 5, stock: 1 }),
  ];
  assert.deepEqual(
    sortOffers(rows, "price").map((x) => x.price),
    [5, 20, 9],
  );
  assert.deepEqual(
    sortOffers(rows, "stock").map((x) => x.stock),
    [3, 1, null],
  );
});
