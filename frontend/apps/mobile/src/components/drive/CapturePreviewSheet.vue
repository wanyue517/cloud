<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { Button as VanButton } from 'vant'
import { formatBytes } from '@repo/drive-core/format'
import { buildCaptureFilename } from '@/lib/media-capture'
import { BottomSheet } from '@/components/common'

const props = defineProps<{
  open: boolean
  file: File | null
  kind: 'photo' | 'video'
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  confirm: [file: File]
}>()

const filename = ref('')
const previewUrl = ref<string | null>(null)
const errorText = ref('')

function revokePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = null
  }
}

watch(
  () => [props.open, props.file] as const,
  ([opened, file]) => {
    revokePreview()
    errorText.value = ''
    if (opened && file) {
      filename.value = buildCaptureFilename(props.kind, file.type, file.name)
      previewUrl.value = URL.createObjectURL(file)
    }
  },
)

onBeforeUnmount(() => {
  revokePreview()
})

function handleSubmit() {
  const file = props.file
  if (!file) return
  const trimmed = filename.value.trim()
  if (!trimmed) {
    errorText.value = '文件名不能为空'
    return
  }
  if (/[/\\]/.test(trimmed)) {
    errorText.value = '文件名不能包含 / 或 \\'
    return
  }
  const renamed = new File([file], trimmed, {
    type: file.type || (props.kind === 'photo' ? 'image/jpeg' : 'video/mp4'),
    lastModified: file.lastModified || Date.now(),
  })
  emit('update:open', false)
  emit('confirm', renamed)
}
</script>

<template>
  <BottomSheet
    :open="props.open"
    :title="props.kind === 'photo' ? '拍照预览与上传' : '录像预览与上传'"
    :subtitle="props.file ? formatBytes(props.file.size) : undefined"
    @update:open="emit('update:open', $event)"
  >
    <form v-if="props.file" class="space-y-4 pb-2" @submit.prevent="handleSubmit">
      <div class="rounded-2xl overflow-hidden bg-muted flex items-center justify-center max-h-60">
        <img
          v-if="props.kind === 'photo' && previewUrl"
          :src="previewUrl"
          :alt="filename"
          class="max-h-60 w-full object-contain"
        />
        <video
          v-else-if="props.kind === 'video' && previewUrl"
          :src="previewUrl"
          controls
          playsinline
          class="max-h-60 w-full bg-black"
        />
      </div>

      <div>
        <label class="block text-xs font-medium text-muted-foreground mb-1">上传文件名</label>
        <input
          v-model="filename"
          type="text"
          maxlength="255"
          class="w-full rounded-xl border border-input bg-background px-3.5 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
        />
        <p v-if="errorText" class="text-xs text-destructive mt-1">{{ errorText }}</p>
      </div>

      <div class="flex gap-2">
        <VanButton
          block
          round
          plain
          native-type="button"
          @click="emit('update:open', false)"
        >
          取消
        </VanButton>
        <VanButton block round type="primary" native-type="submit">
          加入上传队列
        </VanButton>
      </div>
    </form>
  </BottomSheet>
</template>
