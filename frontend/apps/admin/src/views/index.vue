<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { logout } from '@repo/session/session'
import { loginUrl } from '@repo/session/navigation'
import { Button } from '@repo/ui/button'
import { request } from '@repo/session/request'
import ThemeSettings from '@repo/ui/ThemeSettings.vue'
async function signOut() { try { await logout() } catch (e) { error.value = e instanceof Error ? e.message : '退出失败' } finally { window.location.assign(loginUrl('/admin/')) } }
const stats = ref<Record<string, number>>({}), users = ref<{ id: string; displayName: string; status: number; registeredAt: string }[]>([])
const error = ref(''), ready = ref(false), page = ref(1)
async function load() {
  try { users.value = await request.get('/v1/admin/users', { page: page.value }); stats.value = await request.get('/v1/admin/stats') }
  catch (e) { error.value = e instanceof Error ? e.message : '加载失败' }
}
async function status(id: string, value: number) {
  try { await request.put(`/v1/admin/users/${id}/status`, { status: value }); await load() } catch (e) { error.value = e instanceof Error ? e.message : '保存失败' }
}
onMounted(async () => { ready.value = true; await load() })
</script>
<template>
  <div v-if="ready" class="min-h-screen bg-muted/30 text-foreground">
    <header class="border-b bg-background px-8 py-5 flex items-center justify-between"><h1 class="font-semibold text-xl">Cloudlane 管理后台</h1><div class="flex items-center gap-4"><div class="w-36"><ThemeSettings /></div><Button as-child variant="outline"><a href="/app">返回网盘</a></Button><Button variant="outline" @click="signOut">退出登录</Button></div></header>
    <main class="max-w-6xl mx-auto p-8 space-y-8">
      <p v-if="error" role="alert" class="text-destructive">{{ error }}</p>
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4"><section v-for="(label, key) in { users: '用户', spaces: '空间', files: '文件', bytes: '物理存储（字节）' }" :key="key" class="rounded-xl border bg-card p-6"><p class="text-sm text-muted-foreground">{{ label }}</p><p class="text-3xl font-semibold mt-3">{{ (stats[key] || 0).toLocaleString() }}</p></section></div>
      <section class="rounded-xl border bg-card overflow-auto"><h2 class="font-semibold p-5 border-b">用户管理</h2><table class="w-full text-sm text-left"><thead class="text-muted-foreground"><tr><th class="p-4">昵称</th><th>账号状态</th><th>注册时间</th><th>操作</th></tr></thead><tbody><tr v-for="user in users" :key="user.id" class="border-t"><td class="p-4">{{ user.displayName }}</td><td>{{ user.status === 1 ? '正常' : user.status === 0 ? '禁用' : '冻结' }}</td><td>{{ new Date(user.registeredAt).toLocaleString() }}</td><td><Button variant="outline" size="sm" @click="status(user.id, user.status === 1 ? 0 : 1)">{{ user.status === 1 ? '禁用' : '启用' }}</Button></td></tr></tbody></table></section>
      <div class="flex justify-end gap-3"><Button variant="outline" :disabled="page <= 1" @click="page--; load()">上一页</Button><span class="p-2">第 {{ page }} 页</span><Button variant="outline" :disabled="users.length < 100" @click="page++; load()">下一页</Button></div>
    </main>
  </div>
  <p v-else-if="error" role="alert" class="p-10 text-destructive">{{ error }}</p>
</template>
