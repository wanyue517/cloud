<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RefreshCw, Search } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { confirm } from '@repo/ui/confirm-dialog'
import { Input } from '@repo/ui/input'
import { toast } from '@repo/ui/toast'
import { request } from '@repo/session/request'

interface AdminUser {
  id: string
  displayName: string
  email?: string
  isAdmin?: boolean
  status: number
  registeredAt: string
}

const route = useRoute()
const router = useRouter()
const PAGE_SIZE = 50

function parsePage(raw: unknown): number {
  const num = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(num) && num >= 1 ? num : 1
}

const page = ref(parsePage(route.query.page))
const query = ref('')
const statusFilter = ref<'all' | '1' | '0'>('all')
const users = ref<AdminUser[]>([])
const total = ref(0)
const myUserId = ref('')
const loading = ref(false)
const busyId = ref('')
const error = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, unknown> = { page: page.value, pageSize: PAGE_SIZE }
    if (query.value.trim()) params.query = query.value.trim()
    if (statusFilter.value !== 'all') params.status = Number(statusFilter.value)
    const countParams: Record<string, unknown> = {}
    if (query.value.trim()) countParams.query = query.value.trim()
    if (statusFilter.value !== 'all') countParams.status = Number(statusFilter.value)
    const [list, count] = await Promise.all([
      request.get<AdminUser[]>('/v1/admin/users', params),
      request.get<number>('/v1/admin/users/count', countParams),
    ])
    users.value = list
    total.value = Number(count || 0)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载用户列表失败'
  } finally {
    loading.value = false
  }
}

async function updateStatus(user: AdminUser, value: number) {
  const disabling = value === 0
  const ok = await confirm({
    title: disabling ? '禁用账号' : '启用账号',
    description: disabling
      ? `确定禁用用户「${user.displayName}」吗？禁用后服务端将立即撤销其所有访问权限并强制下线。`
      : `确定重新启用用户「${user.displayName}」吗？`,
    confirmText: disabling ? '确认禁用' : '确认启用',
    variant: disabling ? 'destructive' : 'default',
  })
  if (!ok) return
  busyId.value = user.id
  error.value = ''
  try {
    await request.put(`/v1/admin/users/${user.id}/status`, { status: value })
    toast.success(disabling ? `已禁用用户「${user.displayName}」` : `已启用用户「${user.displayName}」`)
    await load()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '保存失败'
    error.value = msg
    toast.error(msg)
  } finally {
    busyId.value = ''
  }
}

function goPage(next: number) {
  const safe = Math.max(1, next)
  page.value = safe
  void router.replace({ query: { ...route.query, page: safe === 1 ? undefined : String(safe) } })
}

watch(
  () => route.query.page,
  raw => {
    const parsed = parsePage(raw)
    if (parsed !== page.value) page.value = parsed
    void load()
  },
)

watch([query, statusFilter], () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    void load()
  }, 250)
})

onMounted(async () => {
  try {
    const me = await request.get<{ id: string }>('/v1/me')
    myUserId.value = me.id
  } catch { /* ignore */ }
  void load()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold">用户管理</h2>
        <p class="text-sm text-muted-foreground mt-1">支持按昵称、邮箱或 ID 检索平台账号，禁用后服务端将立即撤销其访问权限。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <RefreshCw class="size-4" :class="{ 'animate-spin': loading }" />
        刷新列表
      </Button>
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <div class="relative min-w-[220px] flex-1 max-w-sm">
        <Search :size="15" class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <Input v-model="query" class="pl-9 h-9" placeholder="搜索昵称、邮箱或用户 ID…" />
      </div>
      <div class="inline-flex items-center gap-1 rounded-md border border-input p-0.5">
        <Button size="sm" class="h-7 px-2.5 text-xs" :variant="statusFilter === 'all' ? 'default' : 'ghost'" @click="statusFilter = 'all'">全部</Button>
        <Button size="sm" class="h-7 px-2.5 text-xs" :variant="statusFilter === '1' ? 'default' : 'ghost'" @click="statusFilter = '1'">正常</Button>
        <Button size="sm" class="h-7 px-2.5 text-xs" :variant="statusFilter === '0' ? 'default' : 'ghost'" @click="statusFilter = '0'">禁用</Button>
      </div>
      <span class="ml-auto text-xs text-muted-foreground">共 {{ total.toLocaleString() }} 位用户</span>
    </div>

    <p v-if="error" role="alert" class="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
      {{ error }}
    </p>

    <section class="rounded-xl border border-border bg-card overflow-x-auto">
      <table class="w-full min-w-[640px] text-sm text-left">
        <thead class="text-muted-foreground bg-muted/50">
          <tr>
            <th class="px-4 py-3 font-medium whitespace-nowrap">用户</th>
            <th class="px-4 py-3 font-medium whitespace-nowrap">角色</th>
            <th class="px-4 py-3 font-medium whitespace-nowrap">账号状态</th>
            <th class="px-4 py-3 font-medium whitespace-nowrap">注册时间</th>
            <th class="px-4 py-3 font-medium text-right whitespace-nowrap">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading && !users.length" class="border-t border-border">
            <td colspan="5" class="px-4 py-10 text-center text-muted-foreground">正在加载用户列表…</td>
          </tr>
          <tr v-else-if="!loading && users.length === 0" class="border-t border-border">
            <td colspan="5" class="px-4 py-10 text-center text-muted-foreground">未找到符合条件的用户记录</td>
          </tr>
          <tr v-for="user in users" :key="user.id" class="border-t border-border">
            <td class="px-4 py-3">
              <div class="font-medium">{{ user.displayName }}</div>
              <div class="text-xs text-muted-foreground">{{ user.email || `ID: ${user.id}` }}</div>
            </td>
            <td class="px-4 py-3 whitespace-nowrap">
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium whitespace-nowrap"
                :class="user.isAdmin ? 'bg-primary/15 text-primary' : 'bg-muted text-muted-foreground'"
              >
                {{ user.isAdmin ? '超级管理员' : '普通成员' }}
              </span>
            </td>
            <td class="px-4 py-3 whitespace-nowrap">
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium whitespace-nowrap"
                :class="user.status === 1
                  ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'
                  : 'bg-destructive/15 text-destructive'"
              >
                {{ user.status === 1 ? '正常' : user.status === 0 ? '禁用' : '冻结' }}
              </span>
            </td>
            <td class="px-4 py-3 text-muted-foreground whitespace-nowrap">{{ new Date(user.registeredAt).toLocaleString('zh-CN') }}</td>
            <td class="px-4 py-3 text-right whitespace-nowrap">
              <Button
                v-if="!user.isAdmin && user.id !== myUserId"
                :variant="user.status === 1 ? 'outline' : 'default'"
                size="sm"
                :class="user.status === 1 ? 'text-destructive' : ''"
                :disabled="busyId === user.id"
                @click="updateStatus(user, user.status === 1 ? 0 : 1)"
              >
                {{ user.status === 1 ? '禁用' : '启用' }}
              </Button>
              <span v-else class="text-xs text-muted-foreground">不可操作</span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="total > PAGE_SIZE" class="flex items-center justify-end gap-3">
      <Button variant="outline" size="sm" :disabled="page <= 1 || loading" @click="goPage(page - 1)">上一页</Button>
      <span class="px-2 text-sm text-muted-foreground">第 {{ page }} / {{ Math.max(1, Math.ceil(total / PAGE_SIZE)) }} 页</span>
      <Button variant="outline" size="sm" :disabled="page * PAGE_SIZE >= total || loading" @click="goPage(page + 1)">下一页</Button>
    </div>
  </div>
</template>
