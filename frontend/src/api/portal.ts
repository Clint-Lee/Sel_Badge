import { request } from "./client";
import type {
  Category,
  OfferPage,
  PortalStats,
  Preferences,
  PriceCatalog,
  PriceSnapshot,
  Review,
  Site,
  SiteContact,
  SiteSubmissionInput,
  Submission,
} from "../model/portal";

const qs = (values: Record<string, string | number | undefined>) => {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== "") params.set(key, String(value));
  }
  const text = params.toString();
  return text ? `?${text}` : "";
};

export const portalApi = {
  // ---- 资源导航（AI工具） ----
  categories: (): Promise<Category[]> => request("/portal/categories"),
  sites: (): Promise<Site[]> => request("/portal/sites"),
  site: (id: string): Promise<Site> =>
    request(`/portal/sites/${encodeURIComponent(id)}`),
  contact: (id: string): Promise<SiteContact> =>
    request(`/portal/sites/${encodeURIComponent(id)}/contact`),
  visit: (id: string): Promise<Site> =>
    request(`/portal/sites/${encodeURIComponent(id)}/visit`, { method: "POST" }),
  reviews: (id: string): Promise<Review[]> =>
    request(`/portal/sites/${encodeURIComponent(id)}/reviews`),
  review: (
    id: string,
    data: { rating: number; content: string },
  ): Promise<Review> =>
    request(`/portal/sites/${encodeURIComponent(id)}/reviews`, {
      method: "POST",
      body: JSON.stringify(data),
    }),
  submit: (data: SiteSubmissionInput): Promise<Submission> =>
    request("/portal/submissions", {
      method: "POST",
      body: JSON.stringify(data),
    }),
  submissions: (): Promise<Submission[]> => request("/portal/submissions"),
  moderate: (id: string, approve: boolean): Promise<Submission> =>
    request(
      `/portal/submissions/${encodeURIComponent(id)}/${approve ? "approval" : "rejection"}`,
      { method: "PUT" },
    ),
  stats: (): Promise<PortalStats> => request("/portal/stats"),

  // ---- 账号偏好 ----
  preferences: (): Promise<Preferences> => request("/portal/preferences"),
  savePreferences: (data: Preferences): Promise<Preferences> =>
    request("/portal/preferences", {
      method: "PUT",
      body: JSON.stringify(data),
    }),

  // ---- 低价库 ----
  prices: (): Promise<PriceSnapshot> => request("/portal/prices"),
  catalog: (quality: boolean): Promise<PriceCatalog> =>
    request(`/portal/prices/catalog${qs({ quality: quality ? "on" : "off" })}`),
  offers: (
    groupId: string,
    values: {
      page: number;
      quality: boolean;
      source: string;
      stock: string;
      keyword?: string;
    },
  ): Promise<OfferPage> =>
    request(
      `/portal/prices/groups/${encodeURIComponent(groupId)}/offers${qs({
        page: values.page,
        quality: values.quality ? "on" : "off",
        source: values.source === "all" ? undefined : values.source,
        stock: values.stock,
        keyword: values.keyword,
      })}`,
    ),
  recordQuery: (group: string): Promise<{ count: number }> =>
    request("/portal/prices/catalog/query", {
      method: "POST",
      body: JSON.stringify({ group }),
    }),
  requestApiPlan: (): Promise<boolean> =>
    request("/portal/prices/catalog/api-plan", {
      method: "POST",
      body: JSON.stringify({ requestId: crypto.randomUUID() }),
    }),
};
