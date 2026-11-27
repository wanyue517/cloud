<script setup lang="ts">
import { Button as VanButton } from 'vant'
import { Check, Folder, Plus, Settings2, Users } from '@lucide/vue'
import { roleLabel } from '@repo/drive-core/categories'
import type { Space } from '@/api/drive'
import { canEditSpace } from '@/lib/spaces'
import { BottomSheet } from '@/components/common'

defineProps<{
  open: boolean
  spaces: Space[]
  currentSpaceId: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  select: [space: Space]
  create: []
  manage: [space: Space]
}>()

function onSelect(space: Space) {
  emit('update:open', false)
  emit('select', space)
}

function onManage(space: Space) {
  emit('update:open', false)
  emit('manage', space)
}

function onCreate() {
  emit('update:open', false)
  emit('create')
}
</script>

<template>
  <BottomSheet :open="open" title="切换空间" subtitle="个人空间与共享空间按自定义顺序混排" @update:open="emit('update:open', $event)">
    <div class="space-y-2 pb-2">
      <div
        v-for="sp in spaces"
        :key="sp.id"
        class="flex items-center gap-3 rounded-xl border px-3 py-2.5 transition-colors"
        :class="
          sp.id === currentSpaceId
            ? 'border-primary bg-primary/8'
            : 'border-border bg-background'
        "
      >
        <button
          type="button"
          class="flex flex-1 items-center gap-3 min-w-0 text-left"
          @click="onSelect(sp)"
        >
          <span
            class="flex h-10 w-10 items-center justify-center rounded-xl text-white shrink-0"
            :style="{ backgroundColor: sp.color || '#2563eb' }"
          >
            <Users v-if="sp.kind === 'SHARED'" :size="18" />
            <Folder v-else :size="18" />
          </span>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-1.5">
              <span class="text-sm font-medium truncate text-foreground">{{ sp.name }}</span>
              <span
                v-if="sp.kind === 'SHARED'"
                class="rounded bg-primary/15 px-1.5 py-0.5 text-[10px] font-medium text-primary shrink-0"
              >
                共享 · {{ roleLabel(sp.role) }}
              </span>
            </div>
            <p v-if="sp.description" class="text-xs text-muted-foreground truncate mt-0.5">{{ sp.description }}</p>
          </div>
          <Check v-if="sp.id === currentSpaceId" :size="18" class="text-primary shrink-0" />
        </button>

        <button
          v-if="canEditSpace(sp)"
          type="button"
          class="inline-flex h-8 w-8 items-center justify-center rounded-lg text-muted-foreground active:bg-accent shrink-0"
          aria-label="空间设置"
          @click.stop="onManage(sp)"
        >
          <Settings2 :size="16" />
        </button>
      </div>
    </div>

    <template #footer>
      <VanButton type="primary" plain block round class="!h-10 !text-sm" @click="onCreate">
        <span class="inline-flex items-center gap-1.5">
          <Plus :size="16" />
          <span>新建空间</span>
        </span>
      </VanButton>
    </template>
  </BottomSheet>
</template>
