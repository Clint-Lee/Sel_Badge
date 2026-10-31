export const PRICE_SORTS = [
  { value: "price", label: "同币种价格从低到高" },
  { value: "price-desc", label: "同币种价格从高到低" },
  { value: "stock", label: "库存从多到少" },
  { value: "newest", label: "最近同步优先" },
];
export const PRODUCT_TYPES = [
  { value: "all", label: "全部形态" },
  { value: "account", label: "成品账号" },
  { value: "recharge", label: "充值服务" },
  { value: "shared", label: "合租 / 共享" },
  { value: "api", label: "API" },
  { value: "code", label: "兑换码" },
  { value: "unknown", label: "形态未明确" },
];
export const BILLING_PERIODS = [
  { value: "all", label: "全部周期" },
  { value: "monthly", label: "月度" },
  { value: "yearly", label: "年度" },
  { value: "short", label: "短期" },
  { value: "unknown", label: "周期未明确" },
];
export const STOCK_FILTERS = [
  { value: "all", label: "全部库存" },
  { value: "in-stock", label: "仅明确有库存" },
  { value: "available", label: "隐藏售罄（含未公开）" },
];
/** 与后端 PriceFeedService.OFFER_PAGE_SIZE 保持一致。 */
export const PAGE_SIZE = 20;
