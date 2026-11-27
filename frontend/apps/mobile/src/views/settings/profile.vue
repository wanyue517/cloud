<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Button as VanButton } from 'vant'
import { Camera } from '@lucide/vue'
import { toast } from '@repo/ui/toast'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { accountApi } from '@/api/account'
import { ActionSheet, MobileShell, type MobileActionItem } from '@/components/common'
import { fetchUserProfile, updateLocalProfile, userState } from '@/stores/user'

const displayName = ref('')
const avatarUrl = ref('')
const saving = ref(false)
const uploading = ref(false)
const avatarSheetOpen = ref(false)

const albumInput = ref<HTMLInputElement | null>(null)
const cameraInput = ref<HTMLInputElement | null>(null)

onMounted(async () => {
  const profile = await fetchUserProfile()
  if (profile) {
    displayName.value = profile.displayName
    avatarUrl.value = profile.avatarUrl || ''
  }
})

const initials = computed(() => (displayName.value.trim() || 'U').slice(0, 1).toUpperCase())

const avatarActions = computed<MobileActionItem[]>(() => {
  const items: MobileActionItem[] = [
    { key: 'album', name: '从相册选择' },
    { key: 'camera', name: '拍照上传' },
  ]
  if (avatarUrl.value) {
    items.push({ key: 'clear', name: '清除当前头像', danger: true })
  }
  return items
})

async function handleAvatarAction(item: MobileActionItem) {
  if (item.key === 'album') {
    albumInput.value?.click()
  } else if (item.key === 'camera') {
    cameraInput.value?.click()
  } else if (item.key === 'clear') {
    avatarUrl.value = ''
    await saveProfile()
  }
}

async function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  uploading.value = true
  try {
    const uploaded = await accountApi.uploadAvatar(file)
    avatarUrl.value = uploaded.url
    const name = displayName.value.trim() || userState.profile?.displayName || '用户'
    await accountApi.updateMe({ displayName: name, avatarUrl: uploaded.url })
    updateLocalProfile({ displayName: name, avatarUrl: uploaded.url })
    toast.success('头像已更新')
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '上传头像失败'))
  } finally {
    uploading.value = false
  }
}

async function saveProfile() {
  const trimmed = displayName.value.trim()
  if (!trimmed) {
    toast.error('昵称不能为空')
    return
  }
  if (trimmed.length > 64) {
    toast.error('昵称不能超过 64 个字符')
    return
  }
  saving.value = true
  try {
    await accountApi.updateMe({
      displayName: trimmed,
      avatarUrl: avatarUrl.value.trim() || null,
    })
    updateLocalProfile({
      displayName: trimmed,
      avatarUrl: avatarUrl.value.trim() || null,
    })
    toast.success('个人资料已保存')
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '保存失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <MobileShell title="个人资料" fallback-path="/me" padded>
    <div class="space-y-6">
      <!-- 头像区域 -->
      <div class="flex flex-col items-center pt-2">
        <button
          type="button"
          class="relative h-22 w-22 rounded-full bg-primary/15 text-primary flex items-center justify-center text-2xl font-semibold overflow-hidden border-2 border-border"
          :disabled="uploading"
          @click="avatarSheetOpen = true"
        >
          <img v-if="avatarUrl" :src="avatarUrl" :alt="displayName" class="h-full w-full object-cover" />
          <span v-else>{{ initials }}</span>
          <span class="absolute bottom-0 inset-x-0 bg-black/50 py-1 text-[10px] text-white flex items-center justify-center gap-0.5">
            <Camera :size="11" />
            <span>{{ uploading ? '上传中' : '更换' }}</span>
          </span>
        </button>
        <p class="mt-2 text-xs text-muted-foreground">点击头像可从相册选择或拍照上传</p>
      </div>

      <input ref="albumInput" type="file" accept="image/*" class="hidden" @change="onFileSelected" />
      <input ref="cameraInput" type="file" accept="image/*" capture="user" class="hidden" @change="onFileSelected" />

      <!-- 表单 -->
      <div class="rounded-2xl border border-border bg-card p-4 space-y-4">
        <div class="space-y-1.5">
          <label for="profile-name" class="block text-xs font-medium text-foreground">显示昵称</label>
          <input
            id="profile-name"
            v-model="displayName"
            type="text"
            maxlength="64"
            placeholder="请输入昵称"
            class="w-full h-11 rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary"
          />
        </div>

        <VanButton
          type="primary"
          block
          round
          :loading="saving"
          loading-text="正在保存…"
          class="!h-11 !text-sm !font-medium"
          @click="saveProfile"
        >
          保存修改
        </VanButton>
      </div>
    </div>

    <ActionSheet
      v-model:open="avatarSheetOpen"
      title="更换头像"
      :actions="avatarActions"
      @select="handleAvatarAction"
    />
  </MobileShell>
</template>
