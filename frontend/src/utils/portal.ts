import type { Offer, Preferences } from "../model/portal";

/** 本地分组结构：与后端 PriceGroup 分离，仅用于快照兜底聚合。 */
export interface ProductGroup {
  id: string;
  product: string;
  category: string;
  source: string;
  offers: Offer[];
  prices: { currency: string; price: number }[];
}

export const sourceName = (source: string) =>
  ({ pickai: "PickAI", priceai: "PriceAI" })[source] || source;
export const safeLink = (value: string) => {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) &&
      !url.username &&
      !url.password
      ? url.href
      : "";
  } catch {
    return "";
  }
};
export const hostName = (value: string) => {
  try {
    return new URL(value).hostname.replace(/^www\./, "");
  } catch {
    return "网址不可用";
  }
};
export const money = (value: number, currency: string) =>
  `${currency === "CNY" ? "¥" : currency === "USD" ? "$" : currency + " "}${value.toLocaleString("zh-CN", { maximumFractionDigits: 4 })}`;
/** 卡片上把币种单独展示，金额不再重复币种符号。 */
export const priceNumber = (value: number) =>
  value.toLocaleString("zh-CN", { maximumFractionDigits: 4 });
export const dateTime = (value: string | null | undefined) =>
  value && Number.isFinite(Date.parse(value))
    ? new Date(value).toLocaleString("zh-CN", {
        timeZone: "Asia/Shanghai",
        hour12: false,
      })
    : "时间未公开";
export const dateOnly = (value: string | null | undefined) =>
  value && Number.isFinite(Date.parse(value))
    ? new Date(value).toLocaleDateString("zh-CN", { timeZone: "Asia/Shanghai" })
    : "日期未公开";
export const isStale = (value: string | null, minutes = 15) =>
  !value ||
  !Number.isFinite(Date.parse(value)) ||
  Date.now() - Date.parse(value) > minutes * 60000;
export const emptyPreferences = (): Preferences => ({
  toolFavorites: [],
  productFavorites: [],
  blockedUrls: [],
  blockedKeywords: [],
});

/** 赠金金额以分为单位存储，展示为元。 */
export const amountYuan = (cents: number) => (cents / 100).toFixed(2);

/** 站点域名首字母，用于没有 logo 时的字母块。 */
export const siteInitial = (name: string, url: string) => {
  const trimmed = (name || "").trim();
  if (trimmed) return trimmed.slice(0, 1).toUpperCase();
  const host = hostName(url);
  return host === "网址不可用" ? "?" : host.slice(0, 1).toUpperCase();
};

/** 收录 30 天内视为新站。 */
export const isRecent = (createdAt: string, days = 30) => {
  const time = Date.parse(createdAt);
  return Number.isFinite(time) && Date.now() - time <= days * 86400000;
};

/** 广告置顶仍在有效期内。 */
export const isPinned = (site: { sponsorPinUntil: string | null }) =>
  Boolean(site.sponsorPinUntil && Date.parse(site.sponsorPinUntil) > Date.now());

export function filterOffers(
  offers: Offer[],
  prefs: Preferences,
  quality = true,
  stockOnly = false,
) {
  return offers.filter(
    (offer) =>
      Number.isFinite(offer.price) &&
      offer.price >= 0 &&
      safeLink(offer.url) &&
      (!quality || !offer.suspicious) &&
      (!stockOnly || offer.available === true) &&
      !prefs.blockedUrls.includes(offer.url) &&
      !prefs.blockedKeywords.some((keyword) =>
        `${offer.title} ${offer.shopName}`
          .toLocaleLowerCase()
          .includes(keyword.toLocaleLowerCase()),
      ),
  );
}

export function groupOffers(offers: Offer[]): ProductGroup[] {
  const groups = new Map<string, ProductGroup>();
  for (const offer of offers) {
    if (!groups.has(offer.groupId))
      groups.set(offer.groupId, {
        id: offer.groupId,
        product: offer.product,
        category: offer.category,
        source: offer.source,
        offers: [],
        prices: [],
      });
    groups.get(offer.groupId)!.offers.push(offer);
  }
  for (const group of groups.values()) {
    const minima = new Map<string, number>();
    for (const offer of group.offers.filter((x) => x.available !== false))
      minima.set(
        offer.currency,
        Math.min(minima.get(offer.currency) ?? Infinity, offer.price),
      );
    group.prices = [...minima].map(([currency, price]) => ({
      currency,
      price,
    }));
  }
  return [...groups.values()].sort(
    (a, b) =>
      a.product.localeCompare(b.product, "zh-CN") ||
      a.source.localeCompare(b.source),
  );
}

export function sortOffers(offers: Offer[], sort: string): Offer[] {
  return [...offers].sort((a, b) => {
    if (sort === "newest")
      return (
        (Date.parse(b.capturedAt || "") || 0) -
        (Date.parse(a.capturedAt || "") || 0)
      );
    if (sort === "stock") return (b.stock ?? -1) - (a.stock ?? -1);
    return (
      a.currency.localeCompare(b.currency) ||
      (sort === "price-desc" ? b.price - a.price : a.price - b.price)
    );
  });
}

/** 稳定伪随机：同一 seed 下顺序一致，方便“换一批”可重复。 */
const hashString = (value: string) => {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
};

export function shuffleBy<T>(items: T[], seed: string, key: (item: T) => string) {
  return [...items].sort(
    (a, b) => hashString(`${seed}:${key(a)}`) - hashString(`${seed}:${key(b)}`),
  );
}
