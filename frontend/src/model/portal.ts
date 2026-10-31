/** 门户数据模型：与后端 com.badge.auth.portal.dto 一一对应。 */

export interface Category {
  id: string;
  name: string;
  description: string;
  count: number;
}

export interface RelayOffer {
  amountCents: number;
  claimInstructions: string;
}

export interface Site {
  id: string;
  name: string;
  url: string;
  description: string;
  category: string;
  tags: string[];
  featured: boolean;
  visits: number;
  todayClicks: number;
  createdAt: string;
  sponsorPinUntil: string | null;
  relayOffer: RelayOffer | null;
  hasContact: boolean;
  source: string;
}

export interface SiteContact {
  siteId: string;
  qq: string;
  wechat: string;
  telegram: string;
  email: string;
  note: string;
}

export interface SiteSubmissionInput {
  name: string;
  url: string;
  category: string;
  description: string;
  tags: string[];
  kind: "website" | "merchant" | "qq-group";
  qq?: string;
  wechat?: string;
  telegram?: string;
  email?: string;
  relayOffer?: { amountCents: number; claimInstructions: string } | null;
}

export interface Submission {
  id: string;
  site: Site;
  username: string;
  kind: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  createdAt: string;
}

export interface Review {
  id: string;
  siteId: string;
  username: string;
  rating: number;
  content: string;
  createdAt: string;
}

export interface Preferences {
  toolFavorites: string[];
  productFavorites: string[];
  blockedUrls: string[];
  blockedKeywords: string[];
}

export interface Offer {
  id: string;
  groupId: string;
  product: string;
  category: string;
  productType: string;
  billingPeriod: string;
  title: string;
  shopName: string;
  url: string;
  price: number;
  currency: string;
  stock: number | null;
  available: boolean | null;
  minOrderQuantity: number | null;
  source: string;
  capturedAt: string | null;
  syncedAt: string;
  suspicious: boolean;
}

export interface SourceStatus {
  source: string;
  status: string;
  lastSuccessAt: string | null;
  attemptedAt: string | null;
  offers: number;
  message: string | null;
  details: Record<string, unknown>;
}

export interface CurrencyPrice {
  currency: string;
  price: number;
}

export interface PriceGroup {
  id: string;
  product: string;
  category: string;
  sources: string[];
  offers: number;
  lowestPrices: CurrencyPrice[];
  productType: string;
  billingPeriod: string;
}

export interface PriceCatalog {
  groups: PriceGroup[];
  totalLinks: number;
  sources: SourceStatus[];
  intervalMinutes: number;
  qualityFilter: boolean;
  updatedAt: string | null;
  queries: Record<string, number>;
}

export interface OfferPage {
  items: Offer[];
  total: number;
  page: number;
  pageSize: number;
}

export interface PriceSnapshot {
  offers: Offer[];
  sources: SourceStatus[];
  intervalMinutes: number;
  updatedAt: string | null;
  totalLinks: number;
}

export interface PortalStats {
  sites: number;
  categories: number;
  reviews: number;
  submissions: number;
  updatedAt: string;
}
