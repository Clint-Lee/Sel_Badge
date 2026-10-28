import {
  MessageSquare,
  Image,
  Clapperboard,
  Code2,
  AudioLines,
  Workflow,
  Cpu,
  BookOpen,
  LayoutGrid,
  Star,
} from "lucide-vue-next";
export const TOOL_CATEGORIES = [
  { id: "all", label: "全部工具", icon: LayoutGrid },
  { id: "assistant", label: "AI 对话", icon: MessageSquare },
  { id: "image", label: "图像设计", icon: Image },
  { id: "video", label: "视频创作", icon: Clapperboard },
  { id: "coding", label: "编程开发", icon: Code2 },
  { id: "audio", label: "音频音乐", icon: AudioLines },
  { id: "agent", label: "智能体与工作流", icon: Workflow },
  { id: "api", label: "模型与 API", icon: Cpu },
  { id: "learning", label: "开源与学习", icon: BookOpen },
  { id: "favorites", label: "我的收藏", icon: Star },
];
export const categoryFor = (id: string) =>
  TOOL_CATEGORIES.find((x) => x.id === id) || TOOL_CATEGORIES[0];
