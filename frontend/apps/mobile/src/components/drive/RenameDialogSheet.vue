<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { Button as VanButton } from 'vant'
import { mainFilenameRange } from '@repo/drive-core/path'
import { BottomSheet } from '@/components/common'

const props = withDefaults(
  defineProps<{
    open: boolean
    mode: 'create-folder' | 'rename'
    initialName?: string
    isFolder?: boolean
    submitting?: boolean
  }>(),
  {
    initialName: '',
    isFolder: true,
    submitting: false,
  },
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  submit: [name: string]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const name = ref('')
const localError = ref('')

watch(
  () => props.open,
  async opened => {
    if (!opened) return
    name.value = props.mode === 'rename' ? props.initialName : ''
    localError.value = ''
    await nextTick()
    const el = inputRef.value
    if (!el) return
    el.focus()
    if (props.mode === 'rename' && name.value) {
      const [start, end] = mainFilenameRange(name.value, props.isFolder)
      try {
        el.setSelectionRange(start, end)
      } catch {
        // ignore unsupported input selection environments
      }
    }
  },
)

function handleSubmit() {
  const trimmed = name.value.trim()
  if (!trimmed) {
    localError.value = props.mode === 'create-folder' ? '请输入文件夹名称' : '名称不能为空'
    return
  }
  if (/[/\\]/.test(trimmed)) {
    localError.value = '名称不能包含 / 或 \\ 字符'
    return
  }
  localError.value = ''
  emit('submit', trimmed)
}
</script>

<template>
  <BottomSheet
    :open="props.open"
    :title="props.mode === 'create-folder' ? '新建文件夹' : '重命名'"
    @update:open="emit('update:open', $event)"
  >
    <form class="space-y-4 pb-2" @submit.prevent="handleSubmit">
      <div>
        <input
          ref="inputRef"
          v-model="name"
          type="text"
          maxlength="255"
          :placeholder="props.mode === 'create-folder' ? '输入新文件夹名称' : '输入新名称'"
          class="w-full rounded-xl border border-input bg-background px-3.5 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
        />
        <p v-if="localError" class="text-xs text-destructive mt-1.5">{{ localError }}</p>
      </div>

      <div class="flex gap-2">
        <VanButton
          block
          round
          plain
          native-type="button"
          :disabled="props.submitting"
          @click="emit('update:open', false)"
        >
          取消
        </VanButton>
        <VanButton
          block
          round
          type="primary"
          native-type="submit"
          :loading="props.submitting"
        >
          {{ props.mode === 'create-folder' ? '创建' : '保存' }}
        </VanButton>
      </div>
    </form>
  </BottomSheet>
</template>
