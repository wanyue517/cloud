<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Shield, Trash2, UserMinus, UserPlus, Users } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Checkbox } from '@repo/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import { Input } from '@repo/ui/input'
import type { Space } from '@/api/drive'
import { roleLabel, shareApi, type SpaceMembersOverview } from '@/api/share'

const props = defineProps<{ space: Space }>()
const emit = defineEmits<{ close: []; updated: [] }>()

const loading = ref(true)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const overview = ref<SpaceMembersOverview | null>(null)
const inviteTarget = ref('')
const inviteRole = ref<'MANAGER' | 'EDITOR' | 'VIEWER'>('VIEWER')
const inviteMessage = ref('')
const keepMembersOnConvert = ref(true)

const canManage = computed(() => overview.value?.myRole === 'OWNER' || overview.value?.myRole === 'MANAGER')
const retainedMembersCount = computed(() => (overview.value?.members || []).filter(m => m.role !== 'OWNER').length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    overview.value = await shareApi.spaceMembers(props.space.id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载成员列表失败'
  } finally {
    loading.value = false
  }
}

async function convertToShared() {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const retained = retainedMembersCount.value
    await shareApi.convertToShared(props.space.id)
    notice.value = retained > 0
      ? `已转为共享空间，已自动恢复 ${retained} 位保留成员的访问权限`
      : '已转为共享空间，原有文件完整保留'
    emit('updated')
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '转换共享空间失败'
  } finally {
    busy.value = false
  }
}

async function convertToPersonal() {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await shareApi.convertToPersonal(props.space.id, keepMembersOnConvert.value)
    notice.value = keepMembersOnConvert.value
      ? '已转为个人空间（成员名单已保留，下次再转为共享空间时可自动恢复）'
      : '已转为个人空间（已清空所有共享成员，下次共享不会恢复）'
    emit('updated')
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '转为个人空间失败'
  } finally {
    busy.value = false
  }
}

async function submitInvite() {
  if (!inviteTarget.value.trim()) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await shareApi.inviteToSpace(props.space.id, {
      target: inviteTarget.value.trim(),
      role: inviteRole.value,
      message: inviteMessage.value.trim(),
    })
    inviteTarget.value = ''
    inviteMessage.value = ''
    notice.value = '邀请已发送，对方接受后即刻生效'
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '发送邀请失败'
  } finally {
    busy.value = false
  }
}

async function changeRole(memberUserId: string, role: string) {
  busy.value = true
  error.value = ''
  try {
    await shareApi.updateMemberRole(props.space.id, memberUserId, role as 'MANAGER' | 'EDITOR' | 'VIEWER')
    notice.value = '成员角色已更新'
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '修改角色失败'
  } finally {
    busy.value = false
  }
}

async function removeMember(memberUserId: string) {
  busy.value = true
  error.value = ''
  try {
    await shareApi.removeMember(props.space.id, memberUserId)
    notice.value = '已移除该成员，其访问权限即时失效'
    emit('updated')
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '移除成员失败'
  } finally {
    busy.value = false
  }
}

async function cancelInvite(invId: string) {
  busy.value = true
  error.value = ''
  try {
    await shareApi.cancelInvitation(invId)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取消邀请失败'
  } finally {
    busy.value = false
  }
}

onMounted(() => { void load() })
</script>

<template>
  <Dialog :open="true" @update:open="v => { if (!v) emit('close') }">
    <DialogContent class="!max-w-2xl">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2">
          <Users :size="18" /> {{ space.name }} · 共享与成员管理
        </DialogTitle>
        <DialogDescription>管理空间协作角色与成员邀请，所有文件归属与容量计费保持不变。</DialogDescription>
      </DialogHeader>

      <p v-if="error" role="alert" class="rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ error }}</p>
      <p v-if="notice" role="status" class="rounded-md bg-primary/10 px-3 py-2 text-xs text-primary">{{ notice }}</p>

      <div v-if="loading" class="py-8 text-center text-sm text-muted-foreground">正在加载成员信息…</div>
      <div v-else-if="overview && overview.kind !== 'SHARED'" class="space-y-4 py-4">
        <div class="rounded-lg border border-border bg-muted/30 p-4 text-sm space-y-1">
          <p class="font-medium">当前为个人空间</p>
          <p class="text-xs text-muted-foreground">转为共享空间后，可邀请团队成员协作查看或编辑，原有目录与文件完整保留。</p>
          <p v-if="retainedMembersCount > 0" class="text-xs text-primary">
            已保留 {{ retainedMembersCount }} 位原共享成员，转为共享空间后将自动恢复其协作权限。
          </p>
        </div>
        <Button :disabled="busy" @click="convertToShared">
          <Shield :size="15" /> {{ busy ? '正在转换…' : '转为共享空间' }}
        </Button>
      </div>
      <div v-else-if="overview" class="space-y-5">
        <form v-if="canManage" class="space-y-2 rounded-lg border border-border bg-muted/20 p-3" @submit.prevent="submitInvite">
          <div class="text-xs font-semibold text-muted-foreground">邀请新成员</div>
          <div class="flex flex-wrap items-center gap-2">
            <Input v-model="inviteTarget" class="min-w-[180px] flex-1" placeholder="输入成员邮箱或用户 ID" :disabled="busy" />
            <select v-model="inviteRole" class="h-9 rounded-md border border-input bg-background px-2 text-xs" :disabled="busy">
              <option value="VIEWER">仅查看 (VIEWER)</option>
              <option value="EDITOR">可编辑 (EDITOR)</option>
              <option v-if="overview.myRole === 'OWNER'" value="MANAGER">管理员 (MANAGER)</option>
            </select>
            <Button type="submit" size="sm" :disabled="busy || !inviteTarget.trim()">
              <UserPlus :size="14" /> 发送邀请
            </Button>
          </div>
          <Input v-model="inviteMessage" placeholder="附言（可选）" :disabled="busy" />
        </form>

        <div class="space-y-2">
          <div class="text-xs font-semibold text-muted-foreground">当前成员 ({{ overview.members.length }})</div>
          <div class="max-h-56 divide-y divide-border overflow-y-auto rounded-lg border border-border">
            <div v-for="m in overview.members" :key="m.id" class="flex items-center justify-between gap-3 px-3 py-2.5 text-sm">
              <div class="min-w-0">
                <div class="truncate font-medium">{{ m.displayName || m.userId }}</div>
                <div class="truncate text-xs text-muted-foreground">{{ m.email || `ID: ${m.userId}` }}</div>
              </div>
              <div class="flex shrink-0 items-center gap-2">
                <select
                  v-if="canManage && m.role !== 'OWNER'"
                  :value="m.role"
                  class="h-8 rounded border border-input bg-background px-2 text-xs"
                  :disabled="busy"
                  @change="changeRole(m.userId, ($event.target as HTMLSelectElement).value)"
                >
                  <option value="VIEWER">仅查看</option>
                  <option value="EDITOR">可编辑</option>
                  <option v-if="overview.myRole === 'OWNER'" value="MANAGER">管理员</option>
                </select>
                <span v-else class="rounded-full bg-muted px-2.5 py-0.5 text-xs text-muted-foreground">{{ roleLabel(m.role) }}</span>
                <Button
                  v-if="canManage && m.role !== 'OWNER'"
                  variant="ghost"
                  size="icon-sm"
                  class="text-destructive"
                  title="移除成员"
                  :disabled="busy"
                  @click="removeMember(m.userId)"
                >
                  <Trash2 :size="14" />
                </Button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="overview.pendingInvitations.length" class="space-y-2">
          <div class="text-xs font-semibold text-muted-foreground">待处理邀请 ({{ overview.pendingInvitations.length }})</div>
          <div class="divide-y divide-border rounded-lg border border-border">
            <div v-for="inv in overview.pendingInvitations" :key="inv.id" class="flex items-center justify-between gap-2 px-3 py-2 text-xs">
              <span>{{ inv.inviteeName || inv.inviteeUserId }} · {{ roleLabel(inv.role) }}</span>
              <Button variant="ghost" size="sm" class="h-7 text-destructive" :disabled="busy" @click="cancelInvite(inv.id)">取消邀请</Button>
            </div>
          </div>
        </div>

        <div v-if="overview.myRole === 'OWNER'" class="space-y-3 rounded-lg border border-border bg-muted/20 p-3.5">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <div class="text-xs font-semibold">转为个人空间</div>
              <p class="text-[11px] text-muted-foreground">转换后其他成员将立即失去该空间访问权限，待处理邀请自动取消。</p>
            </div>
            <Button variant="outline" size="sm" :disabled="busy" @click="convertToPersonal">
              <UserMinus :size="14" /> {{ busy ? '正在转换…' : '转为个人空间' }}
            </Button>
          </div>
          <label class="flex cursor-pointer items-center gap-2 text-xs text-muted-foreground">
            <Checkbox
              :model-value="keepMembersOnConvert"
              @update:model-value="v => { keepMembersOnConvert = v === true }"
            />
            <span>保留当前成员（下次再次转为共享空间时自动恢复；取消勾选则清空成员，下次不恢复）</span>
          </label>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
