<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { ChevronRight } from '@lucide/vue'
import { roleLabel } from '@repo/drive-core/categories'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes, formatRelativeTime } from '@repo/drive-core/format'
import { mobileDriveApi, type DriveNode } from '@/api/drive'
import { mobileShareApi, subscribeSseWithBackoff, type ResourceShareItem } from '@/api/share'
import { truncateSharedBreadcrumbs } from '@/lib/share-helpers'
import { EmptyState, ErrorState, MobileShell, PullRefresh, SkeletonList } from '@/components/common'
import NodeThumb from '@/components/drive/NodeThumb.vue'
import MobilePreviewShell from '@/components/preview/MobilePreviewShell.vue'

const route = useRoute()
const router = useRouter()

const shareId = computed(() => String(route.params.shareId || ''))
const shareItem = ref<ResourceShareItem | null>(null)
const currentFolderId = ref('')
const ancestors = ref<DriveNode[]>([])
const nodes = ref<DriveNode[]>([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const previewNode = ref<DriveNode | null>(null)

let stopSse: (() => void) | null = null

const safeBreadcrumbs = computed(() => {
  if (!shareItem.value) return []
  return truncateSharedBreadcrumbs(ancestors.value, shareItem.value.nodeId)
})

async function verifyAccessAndLoad(folderId?: string) {
  if (!refreshing.value) loading.value = true
  error.value = ''
  try {
    const list = await mobileShareApi.sharedWithMe()
    const matched = list.find(i => i.id === shareId.value)
    if (!matched) {
      nodes.value = []
      ancestors.value = []
      shareItem.value = null
      showToast('该共享已撤销或无权访问')
      await router.replace('/shared')
      return
    }
    shareItem.value = matched
    const targetFolder = folderId || currentFolderId.value || matched.nodeId
    currentFolderId.value = targetFolder

    const [pageRes, ancRes] = await Promise.all([
      mobileDriveApi.nodes(matched.spaceId, {
        parentId: targetFolder,
        page: 1,
        pageSize: 100,
        sort: 'name',
        order: 'asc',
      }),
      mobileDriveApi.ancestors(matched.spaceId, targetFolder).catch(() => []),
    ])
    nodes.value = pageRes.items
    ancestors.value = ancRes
  } catch (err) {
    const status = (err as { httpStatus?: number; code?: number } | null)?.httpStatus
    if (status === 403 || status === 404) {
      nodes.value = []
      ancestors.value = []
      showToast('共享权限已变更或失效')
      await router.replace('/shared')
      return
    }
    error.value = normalizeErrorMessage(err, '加载共享目录失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(() => {
  void verifyAccessAndLoad()
  stopSse = subscribeSseWithBackoff('/api/v1/shares/events', () => {
    void verifyAccessAndLoad(currentFolderId.value)
  })
})

onBeforeUnmount(() => {
  stopSse?.()
  stopSse = null
})

function openNode(node: DriveNode) {
  if (node.nodeType === 'FOLDER') {
    void verifyAccessAndLoad(node.id)
  } else {
    previewNode.value = node
  }
}

function jumpBreadcrumb(folderId: string) {
  if (!shareItem.value) return
  void verifyAccessAndLoad(folderId)
}

async function onRefresh() {
  refreshing.value = true
  await verifyAccessAndLoad(currentFolderId.value)
}
</script>

<template>
  <MobileShell
    :title="shareItem?.nodeName || '与我共享'"
    :subtitle="shareItem ? `来自 ${shareItem.ownerName} · ${roleLabel(shareItem.permission)}` : undefined"
    back-to="/shared"
  >
    <div class="flex-1 min-h-0 flex flex-col">
      <!-- 严格截断在授权根的面包屑 -->
      <nav
        v-if="shareItem"
        aria-label="共享目录路径"
        class="flex items-center gap-1 overflow-x-auto whitespace-nowrap border-b border-border bg-card/70 px-3.5 py-2 text-xs text-muted-foreground no-scrollbar"
      >
        <button
          type="button"
          class="shrink-0 rounded px-1.5 py-0.5 active:bg-accent"
          :class="safeBreadcrumbs.length <= 1 ? 'font-semibold text-foreground' : ''"
          @click="jumpBreadcrumb(shareItem.nodeId)"
        >
          {{ shareItem.nodeName }}
        </button>
        <template v-for="(anc, idx) in safeBreadcrumbs.slice(1)" :key="anc.id">
          <ChevronRight :size="12" class="shrink-0 opacity-60" />
          <button
            type="button"
            class="shrink-0 max-w-[130px] truncate rounded px-1.5 py-0.5 active:bg-accent"
            :class="idx === safeBreadcrumbs.length - 2 ? 'font-semibold text-foreground' : ''"
            @click="jumpBreadcrumb(anc.id)"
          >
            {{ anc.name }}
          </button>
        </template>
      </nav>

      <PullRefresh v-model="refreshing" @refresh="onRefresh">
        <SkeletonList v-if="loading" :count="6" />
        <ErrorState v-else-if="error" :message="error" @retry="verifyAccessAndLoad(currentFolderId)" />
        <EmptyState v-else-if="nodes.length === 0" title="当前共享目录为空" />
        <div v-else class="divide-y divide-border px-3.5 pb-16">
          <button
            v-for="node in nodes"
            :key="node.id"
            type="button"
            class="w-full flex items-center gap-3 py-3 text-left active:bg-accent/50"
            @click="openNode(node)"
          >
            <NodeThumb :node="node" variant="list" />
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium truncate text-foreground">{{ node.name }}</p>
              <p class="text-xs text-muted-foreground mt-0.5">
                {{ node.nodeType === 'FOLDER' ? '文件夹' : formatBytes(node.size) }} · {{ formatRelativeTime(node.updatedAt) }}
              </p>
            </div>
          </button>
        </div>
      </PullRefresh>
    </div>

    <MobilePreviewShell
      :open="Boolean(previewNode)"
      :node="previewNode"
      :siblings="nodes"
      @close="previewNode = null"
      @select="n => (previewNode = n)"
    />
  </MobileShell>
</template>
