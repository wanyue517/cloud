<script setup lang="ts">
import { PullRefresh as VanPullRefresh } from 'vant'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    disabled?: boolean
  }>(),
  {
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  refresh: []
}>()
</script>

<template>
  <VanPullRefresh
    :model-value="props.modelValue"
    :disabled="props.disabled"
    pulling-text="下拉即可刷新…"
    loosing-text="释放立即刷新…"
    loading-text="正在刷新…"
    success-text="已刷新"
    class="flex-1 min-h-0"
    @update:model-value="emit('update:modelValue', $event)"
    @refresh="emit('refresh')"
  >
    <slot />
  </VanPullRefresh>
</template>
