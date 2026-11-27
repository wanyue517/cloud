export interface TreeNodeSummary {
  id: string
  parentId: string
  name: string
  hasChildren: boolean
}

export type FlatTreeItem =
  | { kind: 'node'; key: string; depth: number; node: TreeNodeSummary; expanded: boolean; loading: boolean }
  | { kind: 'loading'; key: string; depth: number; parentId: string }
  | { kind: 'error'; key: string; depth: number; parentId: string; message: string }
  | { kind: 'more'; key: string; depth: number; parentId: string }

export interface FlattenTreeInput {
  childrenByParent: ReadonlyMap<string, readonly TreeNodeSummary[]>
  expandedIds: ReadonlySet<string>
  loadingParents: ReadonlySet<string>
  errorByParent: ReadonlyMap<string, string>
  hasMoreByParent: ReadonlyMap<string, boolean>
  rootParentId?: string
}

/**
 * 将懒加载目录树扁平化为当前可见节点线性列表，供虚拟列表窗口化渲染（T043）。
 */
export function flattenVisibleTree(input: FlattenTreeInput): FlatTreeItem[] {
  const rootId = input.rootParentId ?? '0'
  const out: FlatTreeItem[] = []
  const visited = new Set<string>()

  function walk(parentId: string, depth: number) {
    const children = input.childrenByParent.get(parentId) ?? []
    for (const node of children) {
      if (visited.has(node.id)) continue
      visited.add(node.id)
      const expanded = input.expandedIds.has(node.id)
      const loading = input.loadingParents.has(node.id)
      out.push({ kind: 'node', key: `node:${node.id}`, depth, node, expanded, loading })
      if (expanded) {
        walk(node.id, depth + 1)
      }
    }
    const err = input.errorByParent.get(parentId)
    if (err) {
      out.push({ kind: 'error', key: `err:${parentId}`, depth, parentId, message: err })
    } else if (input.loadingParents.has(parentId) && children.length === 0) {
      out.push({ kind: 'loading', key: `load:${parentId}`, depth, parentId })
    }
    if (input.hasMoreByParent.get(parentId)) {
      out.push({ kind: 'more', key: `more:${parentId}`, depth, parentId })
    }
  }

  walk(rootId, 0)
  return out
}

export interface VirtualWindow {
  startIndex: number
  endIndex: number
  offsetY: number
  totalHeight: number
}

/**
 * 计算虚拟列表当前可视窗口切片（O(1)），支持十万级可见节点流畅滚动。
 */
export function computeVirtualWindow(
  totalCount: number,
  scrollTop: number,
  viewportHeight: number,
  rowHeight = 32,
  overscan = 6,
): VirtualWindow {
  const safeTotal = Math.max(0, totalCount)
  const safeRow = Math.max(1, rowHeight)
  const totalHeight = safeTotal * safeRow
  if (safeTotal === 0) {
    return { startIndex: 0, endIndex: 0, offsetY: 0, totalHeight: 0 }
  }
  const safeScroll = Math.max(0, scrollTop)
  const safeViewport = Math.max(safeRow, viewportHeight)
  const visibleStart = Math.floor(safeScroll / safeRow)
  const visibleCount = Math.ceil(safeViewport / safeRow)
  const startIndex = Math.max(0, visibleStart - overscan)
  const endIndex = Math.min(safeTotal, visibleStart + visibleCount + overscan)
  return {
    startIndex,
    endIndex,
    offsetY: startIndex * safeRow,
    totalHeight,
  }
}

export {
  type BreadcrumbPartition,
  partitionBreadcrumbs,
  mainFilenameRange,
} from '@repo/drive-core/path'
