export interface Tool {
  id: string;
  name: string;
  url: string;
  category: string;
  description: string;
  tags: string[];
  featured: boolean;
  createdAt: string;
}
export interface Review {
  id: string;
  toolId: string;
  username: string;
  rating: number;
  content: string;
  createdAt: string;
}
export interface Submission {
  id: string;
  tool: Tool;
  username: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
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
}
export interface PriceSnapshot {
  offers: Offer[];
  sources: SourceStatus[];
  intervalMinutes: number;
}
export interface ProductGroup {
  id: string;
  product: string;
  category: string;
  source: string;
  offers: Offer[];
  prices: { currency: string; price: number }[];
}
