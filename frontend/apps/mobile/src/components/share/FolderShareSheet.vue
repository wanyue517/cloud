<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button as VanButton, showToast } from 'vant'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import type { DriveNode, Space } from '@/api/drive'
import { mobileShareApi } from '@/api/share'
import { BottomSheet } from '@/components/common'

const props = defineProps<{
  open: boolean
  node: DriveNode | null
  space?: Space | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  shared: []
}>()

const target = ref('')
const permission = ref<'VIEWER' | 'EDITOR'>('VIEWER')
const submitting = ref(false)

const blockedReason = computed(() => {
  if (!props.node) return ''
  if (props.node.hidden) return '隐藏项不可共享给成员'
  if (props.space?.kind === 'PRIVATE') return '私密空间不可共享给成员'
  if (props.node.nodeType !== 'FOLDER') return '成员协作共享仅支持文件夹，单文件请使用公开分享链接'
  return ''
})

watch(
  () => props.open,
  opened => {
    if (opened) {
      target.value = ''
      permission.value = 'VIEWER'
    }
  },
)

async function submit() {
  if (!props.node || blockedReason.value) return
  const cleanTarget = target.value.trim()
  if (!cleanTarget) {
    showToast('请输入对方邮箱或用户 ID')
    return
  }
  submitting.value = true
  try {
    await mobileShareApi.shareFolder(props.node.spaceId, props.node.id, {
      target: cleanTarget,
      permission: permission.value,
    })
    showToast('已发送文件夹共享邀请')
    emit('update:open', false)
    emit('shared')
  } catch (err) {
    showToast(normalizeErrorMessage(err, '共享失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <BottomSheet
    :open="props.open"
    title="共享文件夹给成员"
    :subtitle="props.node?.name"
    @update:open="emit('update:open', $event)"
  >
    <div v-if="blockedReason" class="py-6 text-center text-sm text-destructive">
      {{ blockedReason }}
    </div>

    <form v-else class="space-y-4 pb-2" @submit.prevent="submit">
      <div class="space-y-1.5">
        <label class="text-xs font-medium text-muted-foreground">被共享成员（邮箱或用户 ID）</label>
        <input
          v-model="target"
          type="text"
          placeholder="例如 user@example.com"
          class="w-full rounded-xl border border-input bg-background px-3.5 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
        />
      </div>

      <div class="space-y-1.5">
        <label class="text-xs font-medium text-muted-foreground">授权权限</label>
        <div class="grid grid-cols-2 gap-2">
          <button
            type="button"
            class="rounded-xl border py-2.5 text-xs font-medium"
            :class="
              permission === 'VIEWER'
                ? 'border-primary bg-primary/10 text-primary'
                : 'border-border bg-background text-foreground'
            "
            @click="permission = 'VIEWER'"
          >
            仅查看（VIEWER）
          </button>
          <button
            type="button"
            class="rounded-xl border py-2.5 text-xs font-medium"
            :class="
              permission === 'EDITOR'
                ? 'border-primary bg-primary/10 text-primary'
                : 'border-border bg-background text-foreground'
            "
            @click="permission = 'EDITOR'"
          >
            可编辑（EDITOR）
          </button>
        </div>
      </div>

      <VanButton block round type="primary" native-type="submit" :loading="submitting">
        发送共享
      </VanButton>
    </form>
  </BottomSheet>
</template>
