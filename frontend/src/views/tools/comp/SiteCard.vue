<template>
  <article class="site-card">
    <div class="site-card-top">
      <span
        class="site-logo"
        :class="site.category"
        role="button"
        tabindex="0"
        :title="`查看 ${site.name} 详情`"
        @click="emit('detail', site)"
        @keydown.enter="emit('detail', site)"
      >
        {{ siteInitial(site.name, site.url) }}
      </span>
      <div class="site-card-brand">
        <h3>
          <button :title="site.name" @click="emit('detail', site)">{{ site.name }}</button>
        </h3>
        <span class="site-domain">{{ hostName(site.url) }}</span>
      </div>
      <button
        class="portal-icon-btn favorite-btn"
        :class="{ saved }"
        :aria-label="`${saved ? '取消收藏' : '收藏'} ${site.name}`"
        :aria-pressed="saved"
        :disabled="busy"
        @click="emit('favorite', site.id)"
      >
        <Star :size="15" :fill="saved ? 'currentColor' : 'none'" />
      </button>
    </div>

    <p
      class="site-card-description"
      role="button"
      tabindex="0"
      :title="`查看 ${site.name} 详情`"
      @click="emit('detail', site)"
      @keydown.enter="emit('detail', site)"
    >
      {{ site.description }}
    </p>

    <div class="site-card-footer">
      <span v-if="site.relayOffer" class="site-tag site-tag-offer">
        赠金 {{ amountYuan(site.relayOffer.amountCents) }}
      </span>
      <span v-else-if="site.featured" class="site-tag">精选</span>
      <span v-if="site.tags.length" class="site-card-tags">{{ site.tags.slice(0, 2).join(" · ") }}</span>
      <span v-else class="site-card-tags">{{ hostName(site.url) }}</span>
      <button class="site-open" :aria-label="`访问 ${site.name}`" @click="emit('open', site)">
        访问 <ExternalLink :size="13" />
      </button>
    </div>
  </article>
</template>
<script setup lang="ts">
import { Star, ExternalLink } from "lucide-vue-next";
import { hostName, siteInitial, amountYuan } from "../../../utils/portal";
import type { Site } from "../../../model/portal";

defineProps<{ site: Site; saved: boolean; busy: boolean }>();
const emit = defineEmits<{
  detail: [site: Site];
  favorite: [id: string];
  tag: [tag: string];
  open: [site: Site];
}>();
</script>
