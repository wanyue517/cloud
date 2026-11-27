<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bell,
  Clock,
  Pin,
  Search,
  Star,
  UploadCloud,
  X,
} from '@lucide/vue'
import { toast } from '@repo/ui/toast'
import NodeThumb from '@/components/drive/NodeThumb.vue'
import { formatBytes, formatRelativeTime } from '@repo/drive-core/format'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import {
  mobileDriveApi,
  type DriveNode,
  type QuickAccessItem,
  type RecentAccessItem,
} from '@/api/drive'
import { EmptyState, PullRefresh, SkeletonList } from '@/components/common'
import { mobileUi } from '@/stores/mobile-ui'
import { fetchSpaces, visibleSpaces } from '@/stores/spaces'

const router = useRouter()

const loading = ref(true)
const refreshing = ref(false)
const quickItems = ref<QuickAccessItem[]>([])
const favoriteItems = ref<DriveNode[]>([])
const recentItems = ref<RecentAccessItem[]>([])

let loadSeq = 0

async function loadHomeData(forceSpaces = false) {
  const seq = ++loadSeq
  try {
    const [, quick, recent] = await Promise.all([
      fetchSpaces(forceSpaces),
      mobileDriveApi.listQuickAccess().catch(() => [] as QuickAccessItem[]),
      mobileDriveApi.listRecent().catch(() => [] as RecentAccessItem[]),
    ])
    if (seq !== loadSeq) return
    quickItems.value = quick
    recentItems.value = recent

    // Aggregate favorites across visible (non-private) spaces
    const favPages = await Promise.all(
      visibleSpaces.value.slice(0, 6).map(sp =>
        mobileDriveApi
          .nodes(sp.id, { favorite: true, page: 1, pageSize: 10 })
          .then(res => res.items)
          .catch(() => [] as DriveNode[]),
      ),
    )
    if (seq !== loadSeq) return
    favoriteItems.value = favPages
      .flat()
      .sort((a, b) => (b.updatedAt || '').localeCompare(a.updatedAt || ''))
      .slice(0, 12)
  } finally {
    if (seq === loadSeq) {
      loading.value = false
      refreshing.value = false
    }
  }
}

onMounted(() => {
  void loadHomeData()
})

async function onRefresh() {
  refreshing.value = true
  await loadHomeData(true)
}

function openNode(spaceId: string, nodeId: string, nodeType: string, parentId = '0') {
  if (nodeType === 'FOLDER') {
    void router.push(`/files/${spaceId}/folders/${nodeId}`)
  } else {
    const base = parentId && parentId !== '0' ? `/files/${spaceId}/folders/${parentId}` : `/files/${spaceId}`
    void router.push({ path: base, query: { preview: nodeId } })
  }
}

async function unpinItem(item: QuickAccessItem) {
  try {
    await mobileDriveApi.setPin(item.spaceId, item.nodeId, false)
    quickItems.value = quickItems.value.filter(i => i.id !== item.id)
    toast.success('已取消快捷访问')
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '操作失败'))
  }
}

async function unfavoriteItem(node: DriveNode) {
  try {
    await mobileDriveApi.setFavorite(node.spaceId, node.id, false)
    favoriteItems.value = favoriteItems.value.filter(i => i.id !== node.id)
    toast.success('已取消收藏')
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '操作失败'))
  }
}
</script>

<template>
  <div class="flex-1 min-h-0 flex flex-col bg-background text-foreground">
    <!-- 顶部吸顶搜索栏 -->
    <header class="sticky top-0 z-20 border-b border-border bg-background/95 backdrop-blur px-3.5 py-2.5 safe-top">
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="flex-1 flex items-center gap-2 h-10 rounded-full border border-border bg-muted/60 px-3.5 text-sm text-muted-foreground active:bg-muted"
          @click="router.push('/search')"
        >
          <Search :size="16" />
          <span class="truncate">搜索文件名或文档正文…</span>
        </button>

        <button
          type="button"
          class="relative inline-flex h-10 w-10 items-center justify-center rounded-full text-foreground active:bg-accent shrink-0"
          aria-label="传输任务"
          @click="router.push('/tasks')"
        >
          <UploadCloud :size="20" />
          <span
            v-if="mobileUi.activeUploadCount > 0"
            class="absolute top-1 right-1 min-w-4 h-4 px-1 rounded-full bg-primary text-[10px] font-semibold text-primary-foreground flex items-center justify-center"
          >
            {{ mobileUi.activeUploadCount }}
          </span>
        </button>

        <button
          type="button"
          class="relative inline-flex h-10 w-10 items-center justify-center rounded-full text-foreground active:bg-accent shrink-0"
          aria-label="消息中心"
          @click="router.push('/messages')"
        >
          <Bell :size="20" />
          <span
            v-if="mobileUi.unreadMessageCount > 0"
            class="absolute top-1 right-1 min-w-4 h-4 px-1 rounded-full bg-destructive text-[10px] font-semibold text-destructive-foreground flex items-center justify-center"
          >
            {{ mobileUi.unreadMessageCount > 99 ? '99+' : mobileUi.unreadMessageCount }}
          </span>
        </button>
      </div>
    </header>

    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <SkeletonList v-if="loading" :count="6" />

      <div v-else class="space-y-5 px-4 py-4 pb-8">
        <!-- 快捷访问 -->
        <section class="space-y-2.5">
          <div class="flex items-center justify-between">
            <h2 class="text-sm font-semibold flex items-center gap-1.5 text-foreground">
              <Pin :size="15" class="text-primary" />
              <span>快捷访问</span>
            </h2>
            <span class="text-xs text-muted-foreground">{{ quickItems.length }} 项</span>
          </div>

          <div v-if="quickItems.length > 0" class="flex gap-2.5 overflow-x-auto pb-1 -mx-4 px-4">
            <div
              v-for="item in quickItems"
              :key="item.id"
              class="relative w-36 shrink-0 rounded-xl border border-border bg-card p-3 flex flex-col gap-1.5"
            >
              <button
                type="button"
                class="absolute top-2 right-2 p-1 rounded-full text-muted-foreground active:bg-accent"
                aria-label="取消固定"
                @click.stop="unpinItem(item)"
              >
                <X :size="13" />
              </button>
              <button
                type="button"
                class="text-left space-y-1.5"
                @click="openNode(item.spaceId, item.nodeId, item.nodeType)"
              >
                <NodeThumb
                  :node="{ id: item.nodeId, spaceId: item.spaceId, name: item.name, nodeType: item.nodeType }"
                  variant="list"
                />
                <p class="text-xs font-medium text-foreground truncate pr-4">{{ item.name }}</p>
                <p class="text-[11px] text-muted-foreground truncate">{{ item.spaceName }}</p>
              </button>
            </div>
          </div>

          <div
            v-else
            class="rounded-xl border border-dashed border-border bg-card/50 px-4 py-4 text-xs text-muted-foreground text-center"
          >
            在文件菜单中选择「固定到快捷访问」，常用目录和文档会出现在这里
          </div>
        </section>

        <!-- 我的收藏 -->
        <section class="space-y-2.5">
          <div class="flex items-center justify-between">
            <h2 class="text-sm font-semibold flex items-center gap-1.5 text-foreground">
              <Star :size="15" class="text-amber-500" />
              <span>我的收藏</span>
            </h2>
            <span class="text-xs text-muted-foreground">{{ favoriteItems.length }} 项</span>
          </div>

          <div v-if="favoriteItems.length > 0" class="rounded-2xl border border-border bg-card divide-y divide-border">
            <div
              v-for="node in favoriteItems"
              :key="node.id"
              class="flex items-center gap-3 px-3.5 py-2.5"
            >
              <button
                type="button"
                class="flex flex-1 items-center gap-3 min-w-0 text-left"
                @click="openNode(node.spaceId, node.id, node.nodeType, node.parentId)"
              >
                <NodeThumb :node="node" variant="list" />
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium truncate text-foreground">{{ node.name }}</p>
                  <p class="text-[11px] text-muted-foreground mt-0.5">
                    {{ node.nodeType === 'FOLDER' ? '文件夹' : formatBytes(node.size) }} · {{ formatRelativeTime(node.updatedAt) }}
                  </p>
                </div>
              </button>
              <button
                type="button"
                class="p-1.5 text-amber-500 shrink-0"
                aria-label="取消收藏"
                @click="unfavoriteItem(node)"
              >
                <Star :size="16" class="fill-current" />
              </button>
            </div>
          </div>

          <div
            v-else
            class="rounded-xl border border-dashed border-border bg-card/50 px-4 py-4 text-xs text-muted-foreground text-center"
          >
            暂无收藏内容，可在文件列表中收藏重要文件
          </div>
        </section>

        <!-- 最近使用 -->
        <section class="space-y-2.5">
          <div class="flex items-center justify-between">
            <h2 class="text-sm font-semibold flex items-center gap-1.5 text-foreground">
              <Clock :size="15" class="text-primary" />
              <span>最近使用</span>
            </h2>
            <span class="text-xs text-muted-foreground">{{ recentItems.length }} 项</span>
          </div>

          <div v-if="recentItems.length > 0" class="rounded-2xl border border-border bg-card divide-y divide-border">
            <button
              v-for="item in recentItems"
              :key="item.id"
              type="button"
              class="w-full flex items-center gap-3 px-3.5 py-2.5 text-left active:bg-accent/50"
              @click="openNode(item.spaceId, item.id, item.nodeType, item.parentId)"
            >
              <NodeThumb :node="item" variant="list" />
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium truncate text-foreground">{{ item.name }}</p>
                <p class="text-[11px] text-muted-foreground mt-0.5 truncate">
                  {{ item.spaceName }} · {{ formatBytes(item.size) }} · {{ formatRelativeTime(item.accessedAt) }}
                </p>
              </div>
            </button>
          </div>

          <EmptyState
            v-else
            title="暂无最近访问记录"
            description="浏览或预览过的文件会自动出现在这里"
            action-text="前往文件"
            @action="router.push('/files')"
          />
        </section>
      </div>
    </PullRefresh>
  </div>
</template>
