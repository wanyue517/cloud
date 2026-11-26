<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronRight, Folder, Loader2 } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { drive } from '@/api/drive'
import {
  computeVirtualWindow,
  flattenVisibleTree,
  type TreeNodeSummary,
} from '@/lib/tree'

const props = defineProps<{ space: string; parent: string; active: string; revision: number }>()
const emit = defineEmits<{ navigate: [id: string]; move: [source: string, target: string] }>()

const PAGE_SIZE = 200
const ROW_HEIGHT = 32

const childrenByParent = ref(new Map<string, TreeNodeSummary[]>())
const pageByParent = ref(new Map<string, number>())
const hasMoreByParent = ref(new Map<string, boolean>())
const expandedIds = ref(new Set<string>())
const loadingParents = ref(new Set<string>())
const errorByParent = ref(new Map<string, string>())
const dropTargetId = ref('')

const viewportRef = ref<HTMLElement>()
const scrollTop = ref(0)
const viewportHeight = ref(420)
let spaceGeneration = 0
let resizeObserver: ResizeObserver | undefined

const flatItems = computed(() =>
  flattenVisibleTree({
    childrenByParent: childrenByParent.value,
    expandedIds: expandedIds.value,
    loadingParents: loadingParents.value,
    errorByParent: errorByParent.value,
    hasMoreByParent: hasMoreByParent.value,
    rootParentId: props.parent,
  }),
)

const virtualWindow = computed(() =>
  computeVirtualWindow(flatItems.value.length, scrollTop.value, viewportHeight.value, ROW_HEIGHT, 8),
)

const visibleItems = computed(() =>
  flatItems.value.slice(virtualWindow.value.startIndex, virtualWindow.value.endIndex),
)

async function loadParent(parentId: string, page = 1) {
  const currentGen = spaceGeneration
  const nextLoading = new Set(loadingParents.value)
  nextLoading.add(parentId)
  loadingParents.value = nextLoading

  const nextErr = new Map(errorByParent.value)
  nextErr.delete(parentId)
  errorByParent.value = nextErr

  try {
    const result = await drive.nodes(props.space, { parentId, folders: true, page, pageSize: PAGE_SIZE })
    if (currentGen !== spaceGeneration) return
    const incoming: TreeNodeSummary[] = result.items.map(n => ({
      id: n.id,
      parentId: n.parentId,
      name: n.name,
      hasChildren: n.hasChildren,
    }))
    const nextMap = new Map(childrenByParent.value)
    const merged = page > 1 ? [...(nextMap.get(parentId) ?? []), ...incoming] : incoming
    nextMap.set(parentId, merged)
    childrenByParent.value = nextMap

    const nextPages = new Map(pageByParent.value)
    nextPages.set(parentId, page)
    pageByParent.value = nextPages

    const nextMore = new Map(hasMoreByParent.value)
    nextMore.set(parentId, merged.length < result.total)
    hasMoreByParent.value = nextMore
  } catch (e) {
    if (currentGen !== spaceGeneration) return
    const map = new Map(errorByParent.value)
    map.set(parentId, e instanceof Error ? e.message : '加载失败')
    errorByParent.value = map
  } finally {
    if (currentGen === spaceGeneration) {
      const done = new Set(loadingParents.value)
      done.delete(parentId)
      loadingParents.value = done
    }
  }
}

function toggle(node: TreeNodeSummary) {
  const next = new Set(expandedIds.value)
  if (next.has(node.id)) {
    next.delete(node.id)
    expandedIds.value = next
    return
  }
  next.add(node.id)
  expandedIds.value = next
  if (!childrenByParent.value.has(node.id)) {
    void loadParent(node.id, 1)
  }
}

function loadMore(parentId: string) {
  const nextPage = (pageByParent.value.get(parentId) ?? 1) + 1
  void loadParent(parentId, nextPage)
}

function onScroll(event: Event) {
  scrollTop.value = (event.target as HTMLElement).scrollTop
}

function onDragOver(event: DragEvent, targetId: string) {
  event.preventDefault()
  dropTargetId.value = targetId
}

function onDragLeave(targetId: string) {
  if (dropTargetId.value === targetId) dropTargetId.value = ''
}

function onDrop(event: DragEvent, targetId: string) {
  dropTargetId.value = ''
  const source = event.dataTransfer?.getData('application/cloudlane-node')
  if (source && source !== targetId) {
    emit('move', source, targetId)
  }
}

async function reloadTree() {
  spaceGeneration++
  childrenByParent.value = new Map()
  pageByParent.value = new Map()
  hasMoreByParent.value = new Map()
  errorByParent.value = new Map()
  loadingParents.value = new Set()
  await loadParent(props.parent, 1)
  for (const id of expandedIds.value) {
    void loadParent(id, 1)
  }
}

watch(
  () => [props.space, props.parent],
  () => {
    expandedIds.value = new Set()
    scrollTop.value = 0
    void reloadTree()
  },
  { immediate: true },
)

watch(
  () => props.revision,
  () => {
    void reloadTree()
  },
)

onMounted(() => {
  if (viewportRef.value) {
    viewportHeight.value = viewportRef.value.clientHeight || 420
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(entries => {
        const h = entries[0]?.contentRect.height
        if (h) viewportHeight.value = h
      })
      resizeObserver.observe(viewportRef.value)
    }
  }
})

onBeforeUnmount(() => {
  spaceGeneration++
  resizeObserver?.disconnect()
})
</script>

<template>
  <div ref="viewportRef" class="folder-tree-viewport" role="tree" aria-label="空间目录树" @scroll="onScroll">
    <div class="folder-tree-spacer" :style="{ height: `${virtualWindow.totalHeight}px` }">
      <div class="folder-tree-window" :style="{ transform: `translateY(${virtualWindow.offsetY}px)` }">
        <template v-for="item in visibleItems" :key="item.key">
          <div
            v-if="item.kind === 'node'"
            class="tree-row"
            role="treeitem"
            :aria-expanded="item.node.hasChildren ? item.expanded : undefined"
            :aria-selected="item.node.id === active"
            :class="{ active: item.node.id === active, 'is-drop-target': dropTargetId === item.node.id }"
            :style="{ paddingLeft: `${item.depth * 14}px`, height: `${ROW_HEIGHT}px` }"
            @dragover="onDragOver($event, item.node.id)"
            @dragleave="onDragLeave(item.node.id)"
            @drop.prevent.stop="onDrop($event, item.node.id)"
          >
            <Button
              v-if="item.node.hasChildren"
              variant="ghost"
              size="icon-xs"
              class="tree-toggle"
              :aria-label="`${item.expanded ? '收起' : '展开'}${item.node.name}`"
              :aria-expanded="item.expanded"
              @click.stop="toggle(item.node)"
            >
              <Loader2 v-if="item.loading" :size="13" class="animate-spin" />
              <ChevronRight v-else :size="13" :class="{ rotated: item.expanded }" />
            </Button>
            <span v-else class="tree-toggle-placeholder" aria-hidden="true" />
            <Button variant="ghost" size="sm" class="tree-label" @click="emit('navigate', item.node.id)">
              <Folder :size="15" />
              <span>{{ item.node.name }}</span>
            </Button>
          </div>

          <div
            v-else-if="item.kind === 'loading'"
            class="tree-message"
            :style="{ paddingLeft: `${item.depth * 14 + 24}px`, height: `${ROW_HEIGHT}px` }"
          >
            正在加载…
          </div>

          <div
            v-else-if="item.kind === 'error'"
            class="tree-row"
            :style="{ paddingLeft: `${item.depth * 14 + 8}px`, height: `${ROW_HEIGHT}px` }"
          >
            <Button variant="ghost" size="sm" class="text-destructive text-xs" @click="loadParent(item.parentId, 1)">
              {{ item.message }}，点击重试
            </Button>
          </div>

          <div
            v-else-if="item.kind === 'more'"
            class="tree-row"
            :style="{ paddingLeft: `${item.depth * 14 + 20}px`, height: `${ROW_HEIGHT}px` }"
          >
            <Button variant="ghost" size="sm" class="text-xs text-muted-foreground" @click="loadMore(item.parentId)">
              加载更多目录…
            </Button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
