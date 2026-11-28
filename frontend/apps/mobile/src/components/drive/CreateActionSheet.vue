<script setup lang="ts">
import { Camera, FolderPlus, ImagePlus, Mic, Upload, Video } from '@lucide/vue'
import { BottomSheet } from '@/components/common'
import { supportsNativeLivePhoto } from '@/native/live-photo'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'create-folder': []
  'upload-media': []
  'upload-live': []
  'capture-photo': []
  'capture-video': []
  'record-voice': []
  'upload-files': []
}>()

function pick(
  event:
    | 'create-folder'
    | 'upload-media'
    | 'upload-live'
    | 'capture-photo'
    | 'capture-video'
    | 'record-voice'
    | 'upload-files',
) {
  emit('update:open', false)
  if (event === 'create-folder') emit('create-folder')
  else if (event === 'upload-media') emit('upload-media')
  else if (event === 'upload-live') emit('upload-live')
  else if (event === 'capture-photo') emit('capture-photo')
  else if (event === 'capture-video') emit('capture-video')
  else if (event === 'record-voice') emit('record-voice')
  else emit('upload-files')
}
</script>

<template>
  <BottomSheet
    :open="props.open"
    title="新建与上传"
    @update:open="emit('update:open', $event)"
  >
    <div class="grid grid-cols-3 gap-2.5 pb-2">
      <button v-if="supportsNativeLivePhoto()" type="button"
        class="flex flex-col items-center gap-2 rounded-2xl border border-border bg-background p-3.5 active:bg-accent"
        @click="pick('upload-live')">
        <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-600"><ImagePlus :size="22" /></span>
        <span class="text-xs font-medium text-foreground">实况照片</span>
      </button>
      <button
        type="button"
        class="flex flex-col items-center gap-2 rounded-2xl border border-border bg-background p-3.5 active:bg-accent"
        @click="pick('create-folder')"
      >
        <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary/10 text-primary">
          <FolderPlus :size="22" />
        </span>
        <span class="text-xs font-medium text-foreground">新建文件夹</span>
      </button>

      <button
        type="button"
        class="flex flex-col items-center gap-2 rounded-2xl border border-border bg-background p-3.5 active:bg-accent"
        @click="pick('upload-media')"
      >
        <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
          <ImagePlus :size="22" />
        </span>
        <span class="text-xs font-medium text-foreground">照片/视频</span>
      </button>

      <button
        type="button"
        class="flex flex-col items-center gap-2 rounded-2xl border border-border bg-background p-3.5 active:bg-accent"
        @click="pick('upload-files')"
      >
        <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-sky-500/10 text-sky-600 dark:text-sky-400">
          <Upload :size="22" />
        </span>
        <span class="text-xs font-medium text-foreground">本地文件</span>
      </button>

      <button
        type="button"
        class="flex flex-col items-center gap-2 rounded-2xl border border-border bg-background p-3.5 active:bg-accent"
        @click="pick('capture-photo')"
      >
        <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-amber-500/10 text-amber-600 dark:text-amber-400">
          <Camera :size="22" />
        </span>
        <span class="text-xs font-medium text-foreground">拍照上传</span>
      </button>

      <button
        type="button"
        class="flex flex-col items-center gap-2 rounded-2xl border border-border bg-background p-3.5 active:bg-accent"
        @click="pick('capture-video')"
      >
        <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-purple-500/10 text-purple-600 dark:text-purple-400">
          <Video :size="22" />
        </span>
        <span class="text-xs font-medium text-foreground">录像上传</span>
      </button>

      <button
        type="button"
        class="flex flex-col items-center gap-2 rounded-2xl border border-border bg-background p-3.5 active:bg-accent"
        @click="pick('record-voice')"
      >
        <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-pink-500/10 text-pink-600 dark:text-pink-400">
          <Mic :size="22" />
        </span>
        <span class="text-xs font-medium text-foreground">语音备忘录</span>
      </button>
    </div>
  </BottomSheet>
</template>
