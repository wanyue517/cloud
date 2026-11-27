<script setup lang="ts">
import { List as VanList } from 'vant'

const props = withDefaults(
  defineProps<{
    loading: boolean
    finished: boolean
    error?: boolean
    finishedText?: string
    errorText?: string
    immediateCheck?: boolean
  }>(),
  {
    error: false,
    finishedText: '没有更多了',
    errorText: '加载失败，点击重试',
    immediateCheck: false,
  },
)

const emit = defineEmits<{
  'update:loading': [value: boolean]
  'update:error': [value: boolean]
  load: []
}>()
</script>

<template>
  <VanList
    :loading="props.loading"
    :finished="props.finished"
    :error="props.error"
    loading-text="加载中…"
    :finished-text="props.finishedText"
    :error-text="props.errorText"
    :immediate-check="props.immediateCheck"
    @update:loading="emit('update:loading', $event)"
    @update:error="emit('update:error', $event)"
    @load="emit('load')"
  >
    <slot />
  </VanList>
</template>
