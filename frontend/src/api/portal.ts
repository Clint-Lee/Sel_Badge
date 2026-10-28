import { request } from "./client";
import type {
  Tool,
  Review,
  Submission,
  Preferences,
  PriceSnapshot,
} from "../model/portal";

export const portalApi = {
  tools: (): Promise<Tool[]> => request("/portal/tools"),
  reviews: (id: string): Promise<Review[]> =>
    request(`/portal/tools/${encodeURIComponent(id)}/reviews`),
  review: (
    id: string,
    data: { rating: number; content: string },
  ): Promise<Review> =>
    request(`/portal/tools/${encodeURIComponent(id)}/reviews`, {
      method: "POST",
      body: JSON.stringify(data),
    }),
  submit: (
    data: Pick<Tool, "name" | "url" | "category" | "description" | "tags">,
  ): Promise<Submission> =>
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
  preferences: (): Promise<Preferences> => request("/portal/preferences"),
  savePreferences: (data: Preferences): Promise<Preferences> =>
    request("/portal/preferences", {
      method: "PUT",
      body: JSON.stringify(data),
    }),
  prices: (): Promise<PriceSnapshot> => request("/portal/prices"),
};
