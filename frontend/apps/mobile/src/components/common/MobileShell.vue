<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@lucide/vue'

const props = withDefaults(
  defineProps<{
    title?: string
    showBack?: boolean
    fallbackPath?: string
    padded?: boolean
  }>(),
  {
    title: '',
    showBack: true,
    fallbackPath: '/home',
    padded: false,
  },
)

const emit = defineEmits<{
  back: []
}>()

const router = useRouter()

function handleBack() {
  emit('back')
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.replace(props.fallbackPath)
  }
}
</script>

<template>
  <div class="flex-1 min-h-dvh flex flex-col bg-background text-foreground">
    <header class="sticky top-0 z-20 border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/85 safe-top">
      <div class="flex items-center justify-between gap-2 h-13 px-3.5">
        <div class="flex items-center gap-2 min-w-0 flex-1">
          <slot name="left">
            <button
              v-if="showBack"
              type="button"
              class="inline-flex items-center justify-center w-9 h-9 -ml-1.5 rounded-full text-foreground active:bg-accent transition-colors"
              aria-label="返回"
              @click="handleBack"
            >
              <ArrowLeft :size="20" />
            </button>
          </slot>
          <slot name="title">
            <h1 class="text-base font-semibold truncate">{{ title }}</h1>
          </slot>
        </div>
        <div class="flex items-center gap-1 shrink-0">
          <slot name="right" />
        </div>
      </div>
      <slot name="subheader" />
    </header>

    <main class="flex-1 min-h-0 flex flex-col" :class="padded ? 'px-4 py-4' : ''">
      <slot />
    </main>

    <div v-if="$slots.footer" class="sticky bottom-0 z-20 border-t border-border bg-background/95 backdrop-blur safe-bottom">
      <slot name="footer" />
    </div>
  </div>
</template>
