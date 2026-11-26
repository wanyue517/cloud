<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { FileSpreadsheet, Printer } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { drive, type DriveNode, type ExcelSheetInfo, type Version } from '@/api/drive'
import { computeVirtualWindow } from '@/lib/tree'
import OfficePreviewer from './OfficePreviewer.vue'

const props = defineProps<{ node: DriveNode; version?: Version }>()
const emit = defineEmits<{ download: [] }>()

const viewMode = ref<'sheet' | 'pdf'>('sheet')
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const sheets = ref<ExcelSheetInfo[]>([])
const activeSheet = ref(0)
const rows = ref<string[][]>([])
const totalRows = ref(0)
const colCount = ref(0)
const hasMore = ref(false)
const scrollTop = ref(0)
const viewportHeight = ref(480)
const ROW_HEIGHT = 32
let seq = 0

const columnHeaders = computed(() => {
  const count = Math.max(colCount.value, 1)
  return Array.from({ length: count }, (_, idx) => {
    let n = idx + 1
    let label = ''
    while (n > 0) {
      const rem = (n - 1) % 26
      label = String.fromCharCode(65 + rem) + label
      n = Math.floor((n - 1) / 26)
    }
    return label
  })
})

const virtualWindow = computed(() =>
  computeVirtualWindow(rows.value.length, scrollTop.value, viewportHeight.value, ROW_HEIGHT, 10),
)

const bottomSpacer = computed(() => {
  const renderedHeight = (virtualWindow.value.endIndex - virtualWindow.value.startIndex) * ROW_HEIGHT
  return Math.max(0, virtualWindow.value.totalHeight - virtualWindow.value.offsetY - renderedHeight)
})

const visibleRows = computed(() =>
  rows.value.slice(virtualWindow.value.startIndex, virtualWindow.value.endIndex).map((cells, idx) => ({
    rowIndex: virtualWindow.value.startIndex + idx,
    cells,
  })),
)

function onScroll(event: Event) {
  const el = event.target as HTMLElement
  scrollTop.value = el.scrollTop
  viewportHeight.value = el.clientHeight || 480
}

async function loadSheet(sheetIndex: number) {
  const currentSeq = ++seq
  activeSheet.value = sheetIndex
  loading.value = true
  error.value = ''
  rows.value = []
  scrollTop.value = 0
  try {
    const page = await drive.excelRows(props.node, {
      versionId: props.version?.id,
      sheetIndex,
      rowOffset: 0,
      rowLimit: 100,
    })
    if (currentSeq !== seq) return
    rows.value = page.rows
    totalRows.value = page.totalRows
    colCount.value = page.colCount
    hasMore.value = page.hasMore
  } catch (e) {
    if (currentSeq !== seq) return
    error.value = e instanceof Error ? e.message : '读取工作表失败'
  } finally {
    if (currentSeq === seq) loading.value = false
  }
}

async function loadMoreRows() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const page = await drive.excelRows(props.node, {
      versionId: props.version?.id,
      sheetIndex: activeSheet.value,
      rowOffset: rows.value.length,
      rowLimit: 100,
    })
    rows.value = [...rows.value, ...page.rows]
    totalRows.value = page.totalRows
    colCount.value = Math.max(colCount.value, page.colCount)
    hasMore.value = page.hasMore
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载更多行失败'
  } finally {
    loadingMore.value = false
  }
}

async function initWorkbook() {
  const currentSeq = ++seq
  loading.value = true
  error.value = ''
  sheets.value = []
  rows.value = []
  try {
    const meta = await drive.excelSheets(props.node, props.version?.id)
    if (currentSeq !== seq) return
    sheets.value = meta.sheets
    if (meta.sheets.length > 0) {
      await loadSheet(0)
    } else {
      loading.value = false
    }
  } catch (e) {
    if (currentSeq !== seq) return
    error.value = e instanceof Error ? e.message : '解析 Excel 工作簿失败'
    loading.value = false
  }
}

watch(() => [props.node.id, props.version?.id], () => { void initWorkbook() })
onMounted(() => { void initWorkbook() })
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 flex-col bg-background">
    <div class="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-2">
      <div class="flex items-center gap-1.5">
        <Button variant="outline" size="sm" :class="viewMode === 'sheet' ? 'border-primary text-primary' : ''" @click="viewMode = 'sheet'">
          <FileSpreadsheet :size="14" /> 表格模式
        </Button>
        <Button variant="outline" size="sm" :class="viewMode === 'pdf' ? 'border-primary text-primary' : ''" @click="viewMode = 'pdf'">
          <Printer :size="14" /> 打印预览 (PDF)
        </Button>
      </div>
      <span v-if="viewMode === 'sheet'" class="text-xs text-muted-foreground">
        已加载 {{ rows.length }} / {{ totalRows }} 行 · 虚拟滚动渲染
      </span>
    </div>

    <OfficePreviewer v-if="viewMode === 'pdf'" :node="node" :version="version" @download="emit('download')" />

    <template v-else>
      <div v-if="loading" role="status" class="flex flex-1 items-center justify-center text-sm text-muted-foreground">正在分段读取电子表格…</div>
      <div v-else-if="error" role="alert" class="flex flex-1 items-center justify-center text-sm text-destructive">{{ error }}</div>
      <div v-else class="min-h-0 flex-1 overflow-auto" @scroll="onScroll">
        <table class="w-full border-collapse text-xs">
          <thead class="sticky top-0 z-10 bg-muted">
            <tr>
              <th class="w-12 border border-border px-2 py-1.5 text-center font-medium text-muted-foreground">#</th>
              <th v-for="col in columnHeaders" :key="col" class="min-w-[96px] border border-border px-2 py-1.5 text-left font-medium text-muted-foreground">
                {{ col }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="virtualWindow.offsetY > 0" :style="{ height: `${virtualWindow.offsetY}px` }">
              <td :colspan="columnHeaders.length + 1" />
            </tr>
            <tr v-for="item in visibleRows" :key="item.rowIndex" class="h-8 hover:bg-muted/30">
              <td class="border border-border bg-muted/20 px-2 text-center text-muted-foreground tabular-nums">{{ item.rowIndex + 1 }}</td>
              <td v-for="(_, cIdx) in columnHeaders" :key="cIdx" class="max-w-[260px] truncate border border-border px-2 py-1">
                {{ item.cells[cIdx] ?? '' }}
              </td>
            </tr>
            <tr v-if="bottomSpacer > 0" :style="{ height: `${bottomSpacer}px` }">
              <td :colspan="columnHeaders.length + 1" />
            </tr>
          </tbody>
        </table>

        <div v-if="hasMore" class="flex items-center justify-center gap-3 border-t border-border bg-muted/20 py-3">
          <Button variant="outline" size="sm" :disabled="loadingMore" @click="loadMoreRows">
            {{ loadingMore ? '正在加载下一批…' : '加载更多行 (+100 行)' }}
          </Button>
        </div>
      </div>

      <footer v-if="sheets.length" class="flex items-center gap-1 overflow-x-auto border-t border-border bg-muted/30 px-3 py-1.5">
        <button
          v-for="s in sheets"
          :key="s.index"
          type="button"
          class="shrink-0 rounded px-3 py-1 text-xs transition"
          :class="activeSheet === s.index ? 'bg-background font-semibold text-primary shadow-xs' : 'text-muted-foreground hover:bg-background/60'"
          @click="loadSheet(s.index)"
        >
          {{ s.name }} ({{ s.rowCount }})
        </button>
      </footer>
    </template>
  </div>
</template>
