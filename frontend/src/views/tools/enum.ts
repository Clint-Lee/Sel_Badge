import {
  LayoutGrid,
  Star,
  Clock,
  Sparkles,
  BookOpen,
  Image,
  Clapperboard,
  Cpu,
  Package,
  Wallet,
  MessageSquare,
  KeyRound,
  ShieldCheck,
  Mail,
  Code2,
  Flame,
  Globe,
  Tags,
  Users,
  Network,
} from "lucide-vue-next";

/** 侧栏固定入口：全部 / 今日精选 / 收藏 / 最近 30 天。 */
export const SITE_VIEWS = [
  { key: "all", label: "全部网站", icon: LayoutGrid },
  { key: "featured", label: "今日精选", icon: Sparkles },
  { key: "favorites", label: "我的收藏", icon: Star },
  { key: "recent", label: "最近收录", icon: Clock },
] as const;

/** 分类图标按 id 映射；未登记的分类用默认图标。 */
const ICONS: Record<string, typeof LayoutGrid> = {
  ai: Sparkles,
  design: Image,
  productivity: BookOpen,
  resources: Package,
  reading: BookOpen,
  "gpt-tools": MessageSquare,
  "api-relays": Network,
  "digital-cards": Wallet,
  "sms-services": KeyRound,
  "ai-models": Cpu,
  "mail-services": Mail,
  "reverse": ShieldCheck,
  "ai-deals": Flame,
  intl: Globe,
  "qq-groups": Users,
  "ip-services": Network,
  blog: Code2,
  "upi-zone": Wallet,
  "virtual-cards": Wallet,
  skills: Tags,
  "ai-video": Clapperboard,
};

export const categoryIcon = (id: string) => ICONS[id] || LayoutGrid;

export const SITE_SORTS = [
  { value: "recommended", label: "推荐排序" },
  { value: "popular", label: "访问最多" },
  { value: "newest", label: "最新收录" },
  { value: "name", label: "名称排序" },
];

/** 收录类型：普通网站 / 商家自荐 / QQ群。 */
export const SUBMISSION_KINDS = [
  { value: "website", label: "网站收录" },
  { value: "merchant", label: "商家自荐" },
  { value: "qq-group", label: "QQ群收录" },
];
