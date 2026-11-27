<script setup lang="ts">
import { ref, watch } from 'vue'
import { Button as VanButton, showToast } from 'vant'
import { Lock } from '@lucide/vue'
import { setPrivateSpaceToken } from '@repo/session/request'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { mobileDriveApi } from '@/api/drive'
import { BottomSheet } from '@/components/common'

const props = defineProps<{
  open: boolean
  spaceId: string
  spaceName?: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  unlocked: []
}>()

const password = ref('')
const idleMinutes = ref(15)
const submitting = ref(false)
const errorText = ref('')

watch(
  () => props.open,
  opened => {
    if (opened) {
      password.value = ''
      errorText.value = ''
    }
  },
)

async function submit() {
  if (!password.value) {
    errorText.value = '请输入账号密码以验证身份'
    return
  }
  submitting.value = true
  errorText.value = ''
  try {
    const res = await mobileDriveApi.unlockPrivateSpace(props.spaceId, password.value, idleMinutes.value)
    setPrivateSpaceToken(props.spaceId, res.privateToken)
    showToast('已解锁隐藏项')
    emit('update:open', false)
    emit('unlocked')
  } catch (err) {
    errorText.value = normalizeErrorMessage(err, '密码验证失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <BottomSheet
    :open="props.open"
    title="验证身份查看隐藏项"
    :subtitle="props.spaceName ? `空间：${props.spaceName}` : undefined"
    @update:open="emit('update:open', $event)"
  >
    <form class="space-y-4 pb-2" @submit.prevent="submit">
      <div class="flex items-start gap-2.5 rounded-xl bg-muted/60 p-3 text-xs text-muted-foreground">
        <Lock :size="16" class="shrink-0 mt-0.5 text-primary" />
        <span>显示隐藏文件需验证当前账号登录密码，解锁后空闲 {{ idleMinutes }} 分钟自动重新锁定。</span>
      </div>

      <div>
        <input
          v-model="password"
          type="password"
          autocomplete="current-password"
          placeholder="请输入登录密码"
          class="w-full rounded-xl border border-input bg-background px-3.5 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
        />
        <p v-if="errorText" class="text-xs text-destructive mt-1.5">{{ errorText }}</p>
      </div>

      <div class="flex items-center justify-between text-xs text-muted-foreground">
        <span>自动锁定时长</span>
        <div class="flex gap-1.5">
          <button
            v-for="m in [5, 15, 30]"
            :key="m"
            type="button"
            class="rounded-full px-2.5 py-1 border text-xs"
            :class="
              idleMinutes === m
                ? 'border-primary bg-primary/10 text-primary font-medium'
                : 'border-border text-muted-foreground'
            "
            @click="idleMinutes = m"
          >
            {{ m }} 分钟
          </button>
        </div>
      </div>

      <div class="flex gap-2">
        <VanButton
          block
          round
          plain
          native-type="button"
          :disabled="submitting"
          @click="emit('update:open', false)"
        >
          取消
        </VanButton>
        <VanButton
          block
          round
          type="primary"
          native-type="submit"
          :loading="submitting"
        >
          验证并显示
        </VanButton>
      </div>
    </form>
  </BottomSheet>
</template>
