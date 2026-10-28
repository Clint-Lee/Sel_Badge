<template>
  <article class="tool-card">
    <div class="tool-card-top">
      <span class="tool-monogram" :class="tool.category">{{
        tool.name.slice(0, 1)
      }}</span>
      <div class="tool-card-brand">
        <h3>
          <button @click="emit('detail', tool)">{{ tool.name }}</button
          ><span v-if="tool.featured" class="tool-featured" title="编辑精选"
            ><Sparkles :size="12"
          /></span>
        </h3>
        <span>{{ hostName(tool.url) }}</span>
      </div>
      <button
        class="portal-icon-btn favorite-btn"
        :class="{ saved }"
        :aria-label="`${saved ? '取消收藏' : '收藏'} ${tool.name}`"
        :aria-pressed="saved"
        :disabled="busy"
        @click="emit('favorite', tool.id)"
      >
        <Star :size="17" :fill="saved ? 'currentColor' : 'none'" />
      </button>
    </div>
    <p class="tool-card-description">{{ tool.description }}</p>
    <div class="portal-tags">
      <button v-for="tag in tool.tags" :key="tag" @click="emit('tag', tag)">
        {{ tag }}
      </button>
    </div>
    <div class="tool-card-footer">
      <span>{{ categoryFor(tool.category).label }}</span
      ><button class="tool-detail-link" @click="emit('detail', tool)">
        查看详情 <ArrowUpRight :size="14" />
      </button>
    </div>
  </article>
</template>
<script setup lang="ts">
import { Star, Sparkles, ArrowUpRight } from "lucide-vue-next";
import { hostName } from "../../../utils/portal";
import { categoryFor } from "../enum";
import type { Tool } from "../../../model/portal";
defineProps<{ tool: Tool; saved: boolean; busy: boolean }>();
const emit = defineEmits<{
  detail: [tool: Tool];
  favorite: [id: string];
  tag: [tag: string];
}>();
</script>
