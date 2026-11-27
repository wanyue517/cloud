<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CloudUpload, Play } from '@lucide/vue'
import { mobileUi } from '@/stores/mobile-ui'
import { activeUploadItems, resumeOfflinePausedTasks, uploadState } from '@/stores/uploads'

const route = useRoute()
const router = useRouter()

const currentUploading = computed(
  () => activeUploadItems.value.find(i => i.status === 'uploading') ?? activeUploadItems.value[0] ?? null,
)

const offlinePausedCount = computed(() => uploadState.offlinePausedIds.length)

const visible = computed(() => {
  if (route.path === '/tasks' || route.path.startsWith('/auth')) return false
  return activeUploadItems.value.length > 0 || offlinePausedCount.value > 0
})

const bottomOffset = computed(() =>
  mobileUi.tabBarHidden
    ? 'calc(4.75rem + var(--safe-bottom))'
    : 'calc(4.25rem + var(--safe-bottom))',
)
</script>

<template>
  <Transition name="van-slide-up">
    <div
      v-if="visible"
      class="fixed left-3.5 right-3.5 z-30 flex items-center gap-2.5 rounded-full border border-border bg-card/95 px-3.5 py-2 shadow-lg backdrop-blur"
      :style="{ bottom: bottomOffset }"
    >
      <button
        type="button"
        class="flex flex-1 min-w-0 items-center gap-2.5 text-left"
        @click="router.push('/tasks')"
      >
        <span class="flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-primary shrink-0">
          <CloudUpload :size="15" />
        </span>

        <div class="flex-1 min-w-0">
          <p class="text-xs font-medium text-foreground truncate">
            <template v-if="currentUploading">
              正在上传 {{ currentUploading.name }}（{{ activeUploadItems.length }} 项）
            </template>
            <template v-else>
              {{ offlinePausedCount }} 个上传任务因断网暂停
            </template>
          </p>
          <div v-if="currentUploading" class="mt-1 h-1 w-full rounded-full bg-muted overflow-hidden">
            <div
              class="h-full rounded-full bg-primary transition-all duration-150"
              :style="{ width: `${currentUploading.percent}%` }"
            />
          </div>
        </div>

        <span v-if="currentUploading" class="text-xs font-semibold text-primary shrink-0">
          {{ currentUploading.percent }}%
        </span>
      </button>

      <button
        v-if="!currentUploading && offlinePausedCount > 0"
        type="button"
        class="inline-flex items-center gap-1 rounded-full bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground shrink-0 active:opacity-90"
        @click="resumeOfflinePausedTasks"
      >
        <Play :size="12" />
        <span>续传</span>
      </button>
    </div>
  </Transition>
</template>
