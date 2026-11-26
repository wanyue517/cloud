<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Check, FolderSymlink, Inbox, Link2, RefreshCw, Share2, Trash2, X } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { confirm } from '@repo/ui/confirm-dialog'
import { toast } from '@repo/ui/toast'
import { roleLabel, shareApi, type InvitationItem, type ResourceShareItem, type ShareLinkItem } from '@/api/share'

const emit = defineEmits<{
  'open-shared-folder': [item: ResourceShareItem]
  'spaces-changed': []
}>()

const activeTab = ref<'with-me' | 'by-me' | 'links' | 'invitations'>('with-me')
const loading = ref(true)
const busyId = ref('')
const error = ref('')
const notice = ref('')
const withMe = ref<ResourceShareItem[]>([])
const byMe = ref<ResourceShareItem[]>([])
const myLinks = ref<ShareLinkItem[]>([])
const invitations = ref<InvitationItem[]>([])

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [a, b, c, d] = await Promise.all([
      shareApi.sharedWithMe(),
      shareApi.sharedByMe(),
      shareApi.receivedInvitations(),
      shareApi.listMyShareLinks(),
    ])
    withMe.value = a
    byMe.value = b
    invitations.value = c
    myLinks.value = d
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载共享中心失败'
  } finally {
    loading.value = false
  }
}

async function disableLink(id: string) {
  const ok = await confirm({
    title: '停用公开分享链接',
    description: '停用后该链接将永久失效，持有链接的访问者无法再打开。确定停用吗？',
    confirmText: '确认停用',
    variant: 'destructive',
  })
  if (!ok) return
  busyId.value = id
  error.value = ''
  try {
    await shareApi.disableShareLink(id)
    toast.success('分享链接已停用')
    await loadAll()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '停用失败'
    error.value = msg
    toast.error(msg)
  } finally {
    busyId.value = ''
  }
}

async function revoke(shareId: string) {
  const ok = await confirm({
    title: '撤销文件夹共享',
    description: '撤销后对方将立即失去该文件夹的访问权限。确定撤销吗？',
    confirmText: '确认撤销',
    variant: 'destructive',
  })
  if (!ok) return
  busyId.value = shareId
  error.value = ''
  try {
    await shareApi.revokeResourceShare(shareId)
    toast.success('已撤销共享授权')
    await loadAll()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '撤销失败'
    error.value = msg
    toast.error(msg)
  } finally {
    busyId.value = ''
  }
}

async function respondInvitation(id: string, accept: boolean) {
  busyId.value = id
  error.value = ''
  try {
    if (accept) {
      await shareApi.acceptInvitation(id)
      notice.value = '已接受邀请并加入共享空间'
      emit('spaces-changed')
    } else {
      await shareApi.rejectInvitation(id)
      notice.value = '已拒绝该邀请'
    }
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '处理邀请失败'
  } finally {
    busyId.value = ''
  }
}

onMounted(() => { void loadAll() })
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 flex-col gap-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-xl font-semibold">共享中心</h1>
        <p class="text-xs text-muted-foreground">集中查看与我共享的文件夹、管理我发起的授权及处理空间邀请。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="loadAll">
        <RefreshCw :size="14" /> 刷新
      </Button>
    </header>

    <div class="flex flex-wrap items-center gap-2 border-b border-border pb-2">
      <Button variant="outline" size="sm" :class="activeTab === 'with-me' ? 'border-primary text-primary' : ''" @click="activeTab = 'with-me'">
        <FolderSymlink :size="14" /> 与我共享 ({{ withMe.length }})
      </Button>
      <Button variant="outline" size="sm" :class="activeTab === 'by-me' ? 'border-primary text-primary' : ''" @click="activeTab = 'by-me'">
        <Share2 :size="14" /> 我发起的共享 ({{ byMe.length }})
      </Button>
      <Button variant="outline" size="sm" :class="activeTab === 'links' ? 'border-primary text-primary' : ''" @click="activeTab = 'links'">
        <Link2 :size="14" /> 分享链接 ({{ myLinks.length }})
      </Button>
      <Button variant="outline" size="sm" :class="activeTab === 'invitations' ? 'border-primary text-primary' : ''" @click="activeTab = 'invitations'">
        <Inbox :size="14" /> 收到的邀请 ({{ invitations.length }})
      </Button>
    </div>

    <p v-if="error" role="alert" class="rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ error }}</p>
    <p v-if="notice" role="status" class="rounded-md bg-primary/10 px-3 py-2 text-xs text-primary">{{ notice }}</p>

    <div v-if="loading" class="py-12 text-center text-sm text-muted-foreground">正在加载共享数据…</div>

    <div v-else-if="activeTab === 'with-me'" class="min-h-0 flex-1 overflow-y-auto">
      <div v-if="!withMe.length" class="rounded-xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
        还没有他人共享给你的文件夹
      </div>
      <div v-else class="divide-y divide-border rounded-xl border border-border bg-card">
        <div v-for="item in withMe" :key="item.id" class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div class="min-w-0">
            <div class="truncate font-medium">{{ item.nodeName }}</div>
            <div class="text-xs text-muted-foreground">
              来自 {{ item.ownerName || item.ownerUserId }} · 权限：{{ roleLabel(item.permission) }} · {{ new Date(item.createdAt).toLocaleString('zh-CN') }}
            </div>
          </div>
          <Button size="sm" variant="outline" @click="emit('open-shared-folder', item)">打开目录</Button>
        </div>
      </div>
    </div>

    <div v-else-if="activeTab === 'by-me'" class="min-h-0 flex-1 overflow-y-auto">
      <div v-if="!byMe.length" class="rounded-xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
        还没有我发起的文件夹共享
      </div>
      <div v-else class="divide-y divide-border rounded-xl border border-border bg-card">
        <div v-for="item in byMe" :key="item.id" class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div class="min-w-0">
            <div class="truncate font-medium">{{ item.nodeName }}</div>
            <div class="text-xs text-muted-foreground">
              共享给 {{ item.targetName || item.targetUserId }} · 权限：{{ roleLabel(item.permission) }} · 空间：{{ item.spaceName }}
            </div>
          </div>
          <Button size="sm" variant="outline" class="text-destructive" :disabled="busyId === item.id" @click="revoke(item.id)">
            <Trash2 :size="14" /> 撤销共享
          </Button>
        </div>
      </div>
    </div>

    <div v-else-if="activeTab === 'links'" class="min-h-0 flex-1 overflow-y-auto">
      <div v-if="!myLinks.length" class="rounded-xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
        还没有创建公开分享链接
      </div>
      <div v-else class="divide-y divide-border rounded-xl border border-border bg-card">
        <div v-for="l in myLinks" :key="l.id" class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div class="min-w-0">
            <div class="truncate font-medium">
              {{ l.nodeName }}
              <span class="ml-2 rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground">/s/{{ l.tokenPrefix }}…</span>
            </div>
            <div class="text-xs text-muted-foreground">
              浏览 {{ l.viewCount }} 次 · 下载 {{ l.downloadCount }}{{ l.maxDownloads ? `/${l.maxDownloads}` : '' }} 次
              · {{ l.hasPassword ? '需提取码' : '免密' }}
              · {{ l.disabledAt ? '已停用' : l.expiresAt ? `至 ${new Date(l.expiresAt).toLocaleDateString('zh-CN')}` : '永久有效' }}
            </div>
          </div>
          <Button v-if="!l.disabledAt" size="sm" variant="outline" class="text-destructive" :disabled="busyId === l.id" @click="disableLink(l.id)">
            停用链接
          </Button>
          <span v-else class="text-xs text-muted-foreground">已停用</span>
        </div>
      </div>
    </div>

    <div v-else class="min-h-0 flex-1 overflow-y-auto">
      <div v-if="!invitations.length" class="rounded-xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
        当前没有待处理的空间邀请
      </div>
      <div v-else class="divide-y divide-border rounded-xl border border-border bg-card">
        <div v-for="inv in invitations" :key="inv.id" class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div class="min-w-0">
            <div class="truncate font-medium">{{ inv.inviterName || inv.inviterUserId }} 邀请你加入「{{ inv.spaceName }}」</div>
            <div class="text-xs text-muted-foreground">
              角色：{{ roleLabel(inv.role) }}<template v-if="inv.message"> · 附言：{{ inv.message }}</template>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <Button size="sm" :disabled="busyId === inv.id" @click="respondInvitation(inv.id, true)">
              <Check :size="14" /> 接受
            </Button>
            <Button size="sm" variant="outline" :disabled="busyId === inv.id" @click="respondInvitation(inv.id, false)">
              <X :size="14" /> 拒绝
            </Button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
