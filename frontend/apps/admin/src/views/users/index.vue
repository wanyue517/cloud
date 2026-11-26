<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RefreshCw } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { request } from '@repo/session/request'

interface AdminUser {
  id: string
  displayName: string
  status: number
  registeredAt: string
}

const route = useRoute()
const router = useRouter()

function parsePage(raw: unknown): number {
  const num = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(num) && num >= 1 ? num : 1
}

const page = ref(parsePage(route.query.page))
const users = ref<AdminUser[]>([])
const loading = ref(false)
const busyId = ref('')
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    users.value = await request.get<AdminUser[]>('/v1/admin/users', { page: page.value })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载用户列表失败'
  } finally {
    loading.value = false
  }
}

async function updateStatus(id: string, value: number) {
  busyId.value = id
  error.value = ''
  try {
    await request.put(`/v1/admin/users/${id}/status`, { status: value })
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败'
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

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold">用户管理</h2>
        <p class="text-sm text-muted-foreground mt-1">管理平台账号状态，禁用账号后服务端将立即撤销其访问权限。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <RefreshCw class="size-4" :class="{ 'animate-spin': loading }" />
        刷新列表
      </Button>
    </div>

    <p v-if="error" role="alert" class="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
      {{ error }}
    </p>

    <section class="rounded-xl border border-border bg-card overflow-x-auto">
      <table class="w-full text-sm text-left">
        <thead class="text-muted-foreground bg-muted/50">
          <tr>
            <th class="px-4 py-3 font-medium">昵称</th>
            <th class="px-4 py-3 font-medium">账号状态</th>
            <th class="px-4 py-3 font-medium">注册时间</th>
            <th class="px-4 py-3 font-medium text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && users.length === 0" class="border-t border-border">
            <td colspan="4" class="px-4 py-10 text-center text-muted-foreground">当前页暂无用户记录</td>
          </tr>
          <tr v-for="user in users" :key="user.id" class="border-t border-border">
            <td class="px-4 py-3 font-medium">{{ user.displayName }}</td>
            <td class="px-4 py-3">
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium"
                :class="user.status === 1
                  ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'
                  : 'bg-destructive/15 text-destructive'"
              >
                {{ user.status === 1 ? '正常' : user.status === 0 ? '禁用' : '冻结' }}
              </span>
            </td>
            <td class="px-4 py-3 text-muted-foreground">{{ new Date(user.registeredAt).toLocaleString('zh-CN') }}</td>
            <td class="px-4 py-3 text-right">
              <Button
                variant="outline"
                size="sm"
                :disabled="busyId === user.id"
                @click="updateStatus(user.id, user.status === 1 ? 0 : 1)"
              >
                {{ user.status === 1 ? '禁用' : '启用' }}
              </Button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div class="flex items-center justify-end gap-3">
      <Button variant="outline" :disabled="page <= 1 || loading" @click="goPage(page - 1)">上一页</Button>
      <span class="px-2 text-sm text-muted-foreground">第 {{ page }} 页</span>
      <Button variant="outline" :disabled="users.length < 100 || loading" @click="goPage(page + 1)">下一页</Button>
    </div>
  </div>
</template>
