import type { Offer, Preferences, ProductGroup } from "../model/portal";

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
export const dateTime = (value: string | null) =>
  value && Number.isFinite(Date.parse(value))
    ? new Date(value).toLocaleString("zh-CN", {
        timeZone: "Asia/Shanghai",
        hour12: false,
      })
    : "时间未公开";
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
