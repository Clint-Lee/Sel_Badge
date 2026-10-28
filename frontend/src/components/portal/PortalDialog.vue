<template>
  <dialog
    ref="dialog"
    class="portal-dialog"
    :aria-labelledby="id"
    @cancel.prevent="!busy && emit('close')"
    @click="onBackdrop"
  >
    <div class="dialog-heading">
      <div>
        <p v-if="eyebrow" class="portal-eyebrow">{{ eyebrow }}</p>
        <h2 :id="id">{{ title }}</h2>
      </div>
      <button
        class="portal-icon-btn"
        aria-label="关闭弹窗"
        :disabled="busy"
        @click="emit('close')"
      >
        <X :size="19" />
      </button>
    </div>
    <slot />
  </dialog>
</template>
<script setup lang="ts">
import { ref, onMounted, onUnmounted, useId } from "vue";
import { X } from "lucide-vue-next";
const props = defineProps<{
  title: string;
  eyebrow?: string;
  busy?: boolean;
}>();
const emit = defineEmits<{ close: [] }>();
const id = useId();
const dialog = ref<HTMLDialogElement>();
const onBackdrop = (event: MouseEvent) => {
  if (event.target === dialog.value && !props.busy) {
    const rect = dialog.value!.getBoundingClientRect();
    if (
      event.clientX < rect.left ||
      event.clientX > rect.right ||
      event.clientY < rect.top ||
      event.clientY > rect.bottom
    )
      emit("close");
  }
};
let previousOverflow = "";
onMounted(() => {
  previousOverflow = document.body.style.overflow;
  document.body.style.overflow = "hidden";
  dialog.value?.showModal();
});
onUnmounted(() => {
  document.body.style.overflow = previousOverflow;
});
</script>
