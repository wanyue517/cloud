<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button as VanButton, showConfirmDialog, showToast } from 'vant'
import { Trash2, UserPlus } from '@lucide/vue'
import { canManageMembersInRole, roleLabel } from '@repo/drive-core/categories'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import {
  mobileShareApi,
  type SpaceMemberItem,
  type SpaceMembersOverview,
} from '@/api/share'
import { BottomSheet, EmptyState, SkeletonList } from '@/components/common'

const props = defineProps<{
  open: boolean
  spaceId: string
  spaceName?: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const overview = ref<SpaceMembersOverview | null>(null)
const loading = ref(false)
const inviteTarget = ref('')
const inviteRole = ref<'MANAGER' | 'EDITOR' | 'VIEWER'>('EDITOR')
const inviting = ref(false)

const canManage = computed(() => canManageMembersInRole(overview.value?.myRole))

async function loadMembers() {
  if (!props.spaceId) return
  loading.value = true
  try {
    overview.value = await mobileShareApi.spaceMembers(props.spaceId)
  } catch (err) {
    showToast(normalizeErrorMessage(err, '加载空间成员失败'))
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, props.spaceId] as const,
  ([opened, spaceId]) => {
    if (opened && spaceId) {
      inviteTarget.value = ''
      void loadMembers()
    }
  },
)

async function handleInvite() {
  const target = inviteTarget.value.trim()
  if (!target) {
    showToast('请输入成员邮箱或用户 ID')
    return
  }
  inviting.value = true
  try {
    await mobileShareApi.inviteToSpace(props.spaceId, { target, role: inviteRole.value })
    showToast('邀请已发送')
    inviteTarget.value = ''
    await loadMembers()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '发送邀请失败'))
  } finally {
    inviting.value = false
  }
}

async function changeRole(member: SpaceMemberItem, role: 'MANAGER' | 'EDITOR' | 'VIEWER') {
  try {
    await mobileShareApi.updateMemberRole(props.spaceId, member.userId, role)
    showToast('已更新成员角色')
    await loadMembers()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '更新角色失败'))
  }
}

async function removeMember(member: SpaceMemberItem) {
  try {
    await showConfirmDialog({
      title: '移除成员',
      message: `确定将「${member.displayName || member.email}」移出空间吗？`,
      confirmButtonText: '移除',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }
  try {
    await mobileShareApi.removeMember(props.spaceId, member.userId)
    showToast('已移除成员')
    await loadMembers()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '移除成员失败'))
  }
}

async function cancelPendingInvite(id: string) {
  try {
    await mobileShareApi.cancelInvitation(id)
    showToast('已撤回邀请')
    await loadMembers()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '撤回邀请失败'))
  }
}
</script>

<template>
  <BottomSheet
    :open="props.open"
    title="空间成员管理"
    :subtitle="props.spaceName"
    max-height="86vh"
    @update:open="emit('update:open', $event)"
  >
    <div class="space-y-4 pb-2">
      <!-- 邀请成员表单（仅 OWNER/MANAGER 可见） -->
      <form
        v-if="canManage"
        class="rounded-2xl border border-border bg-muted/30 p-3 space-y-2.5"
        @submit.prevent="handleInvite"
      >
        <div class="flex items-center gap-1.5 text-xs font-medium text-foreground">
          <UserPlus :size="14" class="text-primary" />
          <span>邀请新成员</span>
        </div>
        <div class="flex gap-2">
          <input
            v-model="inviteTarget"
            type="text"
            placeholder="输入邮箱或用户 ID"
            class="flex-1 min-w-0 rounded-xl border border-input bg-background px-3 py-2 text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
          />
          <select
            v-model="inviteRole"
            class="rounded-xl border border-input bg-background px-2 py-2 text-xs text-foreground"
          >
            <option value="VIEWER">仅查看</option>
            <option value="EDITOR">可编辑</option>
            <option value="MANAGER">管理员</option>
          </select>
        </div>
        <VanButton block size="small" round type="primary" native-type="submit" :loading="inviting">
          发送邀请
        </VanButton>
      </form>

      <SkeletonList v-if="loading" :count="4" />

      <template v-else-if="overview">
        <div class="space-y-1.5">
          <p class="text-xs font-medium text-muted-foreground px-1">
            当前成员（{{ overview.members.length }}）
          </p>
          <EmptyState v-if="overview.members.length === 0" title="暂无其他成员" />
          <div v-else class="divide-y divide-border rounded-2xl border border-border bg-background px-3">
            <div
              v-for="m in overview.members"
              :key="m.id"
              class="flex items-center justify-between gap-2 py-2.5"
            >
              <div class="min-w-0 flex-1">
                <p class="text-sm font-medium truncate text-foreground">
                  {{ m.displayName || m.email }}
                </p>
                <p class="text-[11px] text-muted-foreground truncate">{{ m.email }}</p>
              </div>

              <div class="flex items-center gap-1.5 shrink-0">
                <select
                  v-if="canManage && m.role !== 'OWNER'"
                  :value="m.role"
                  class="rounded-lg border border-border bg-card px-2 py-1 text-xs text-foreground"
                  @change="e => changeRole(m, (e.target as HTMLSelectElement).value as 'MANAGER' | 'EDITOR' | 'VIEWER')"
                >
                  <option value="VIEWER">仅查看</option>
                  <option value="EDITOR">可编辑</option>
                  <option value="MANAGER">管理员</option>
                </select>
                <span v-else class="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-medium text-primary">
                  {{ roleLabel(m.role) }}
                </span>

                <button
                  v-if="canManage && m.role !== 'OWNER'"
                  type="button"
                  class="inline-flex h-7 w-7 items-center justify-center rounded-full text-destructive active:bg-destructive/10"
                  aria-label="移除成员"
                  @click="removeMember(m)"
                >
                  <Trash2 :size="14" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="overview.pendingInvitations.length > 0" class="space-y-1.5">
          <p class="text-xs font-medium text-muted-foreground px-1">
            待接受邀请（{{ overview.pendingInvitations.length }}）
          </p>
          <div class="divide-y divide-border rounded-2xl border border-border bg-background px-3">
            <div
              v-for="inv in overview.pendingInvitations"
              :key="inv.id"
              class="flex items-center justify-between gap-2 py-2.5 text-xs"
            >
              <div class="min-w-0 flex-1">
                <p class="font-medium text-foreground truncate">{{ inv.inviteeName || inv.inviteeUserId }}</p>
                <p class="text-muted-foreground">{{ roleLabel(inv.role) }}</p>
              </div>
              <button
                v-if="canManage"
                type="button"
                class="text-destructive px-2 py-1 rounded-lg active:bg-destructive/10"
                @click="cancelPendingInvite(inv.id)"
              >
                撤回
              </button>
            </div>
          </div>
        </div>
      </template>
    </div>
  </BottomSheet>
</template>
