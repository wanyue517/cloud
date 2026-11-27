<script setup lang="ts">
import { computed } from 'vue'
import { ActionSheet as VanActionSheet, type ActionSheetAction } from 'vant'

export interface MobileActionItem {
  key: string
  name: string
  subname?: string
  danger?: boolean
  disabled?: boolean
  loading?: boolean
}

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    description?: string
    actions: MobileActionItem[]
    cancelText?: string
  }>(),
  {
    title: '',
    description: '',
    cancelText: '取消',
  },
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  select: [item: MobileActionItem]
  cancel: []
}>()

const vanActions = computed<(ActionSheetAction & { _raw: MobileActionItem })[]>(() =>
  props.actions.map(item => ({
    name: item.name,
    subname: item.subname,
    color: item.danger ? 'var(--destructive)' : undefined,
    disabled: item.disabled,
    loading: item.loading,
    _raw: item,
  })),
)

function handleSelect(action: ActionSheetAction) {
  const raw = (action as ActionSheetAction & { _raw?: MobileActionItem })._raw
  emit('update:open', false)
  if (raw) emit('select', raw)
}

function handleCancel() {
  emit('update:open', false)
  emit('cancel')
}
</script>

<template>
  <VanActionSheet
    :show="props.open"
    :title="props.title || undefined"
    :description="props.description || undefined"
    :actions="vanActions"
    :cancel-text="props.cancelText"
    close-on-click-action
    safe-area-inset-bottom
    teleport="body"
    @update:show="emit('update:open', $event)"
    @select="handleSelect"
    @cancel="handleCancel"
  />
</template>
