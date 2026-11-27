<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button as VanButton } from 'vant'
import { FileText, Table2 } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { mobileDriveApi, type DriveNode, type ExcelSheetInfo } from '@/api/drive'
import { excelColumnLabel } from '@/lib/pdf-window'
import { EmptyState, ErrorState } from '@/components/common'
import MobileOfficePreviewer from './MobileOfficePreviewer.vue'

const ROW_BATCH = 100

const props = defineProps<{
  node: DriveNode
  versionId?: string
}>()

const emit = defineEmits<{
  'toggle-chrome': []
  download: []
}>()

const mode = ref<'table' | 'pdf'>('table')
const sheets = ref<ExcelSheetInfo[]>([])
const activeSheetIndex = ref(0)
const rows = ref<string[][]>([])
const colCount = ref(0)
const totalRows = ref(0)
const hasMore = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const errorText = ref('')

let loadSeq = 0

const colHeaders = computed(() =>
  Array.from({ length: Math.max(colCount.value, 1) }, (_, idx) => excelColumnLabel(idx)),
)

async function loadRows(sheetIndex: number, reset: boolean) {
  const seq = reset ? ++loadSeq : loadSeq
  if (reset) {
    loading.value = true
    errorText.value = ''
    rows.value = []
  } else {
    loadingMore.value = true
  }

  try {
    const page = await mobileDriveApi.excelRows(props.node.spaceId, props.node.id, {
      versionId: props.versionId ?? props.node.currentVersionId ?? undefined,
      sheetIndex,
      rowOffset: reset ? 0 : rows.value.length,
      rowLimit: ROW_BATCH,
    })
    if (seq !== loadSeq) return
    rows.value = reset ? page.rows : [...rows.value, ...page.rows]
    colCount.value = page.colCount
    totalRows.value = page.totalRows
    hasMore.value = page.hasMore
  } catch (err) {
    if (seq !== loadSeq) return
    errorText.value = normalizeErrorMessage(err, '加载表格数据失败')
  } finally {
    if (seq === loadSeq) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

async function initWorkbook() {
  const seq = ++loadSeq
  loading.value = true
  errorText.value = ''
  sheets.value = []
  rows.value = []
  try {
    const meta = await mobileDriveApi.excelSheets(
      props.node.spaceId,
      props.node.id,
      props.versionId ?? props.node.currentVersionId ?? undefined,
    )
    if (seq !== loadSeq) return
    sheets.value = meta.sheets
    activeSheetIndex.value = meta.sheets[0]?.index ?? 0
    if (meta.sheets.length > 0) {
      await loadRows(activeSheetIndex.value, true)
    } else {
      loading.value = false
    }
  } catch (err) {
    if (seq !== loadSeq) return
    loading.value = false
    errorText.value = normalizeErrorMessage(err, '解析电子表格失败')
  }
}

watch(
  () => [props.node.spaceId, props.node.id, props.versionId ?? props.node.currentVersionId] as const,
  () => {
    mode.value = 'table'
    void initWorkbook()
  },
  { immediate: true },
)

function selectSheet(index: number) {
  if (activeSheetIndex.value === index) return
  activeSheetIndex.value = index
  void loadRows(index, true)
}
</script>

<template>
  <div class="flex-1 min-h-0 flex flex-col bg-background text-foreground">
    <!-- 顶部 Sheet 切换与表格/PDF 模式切换栏 -->
    <div class="flex items-center justify-between gap-2 border-b border-border bg-card/90 px-3 py-1.5 shrink-0">
      <div class="flex items-center gap-1.5 overflow-x-auto no-scrollbar min-w-0">
        <template v-if="mode === 'table' && sheets.length > 0">
          <button
            v-for="sheet in sheets"
            :key="sheet.index"
            type="button"
            class="shrink-0 rounded-full px-2.5 py-1 text-xs transition-colors"
            :class="
              activeSheetIndex === sheet.index
                ? 'bg-primary text-primary-foreground font-medium'
                : 'bg-muted text-muted-foreground active:bg-accent'
            "
            @click="selectSheet(sheet.index)"
          >
            {{ sheet.name }}
          </button>
        </template>
        <span v-else class="text-xs text-muted-foreground truncate">
          {{ mode === 'pdf' ? 'PDF 打印预览模式' : '电子表格' }}
        </span>
      </div>

      <button
        type="button"
        class="shrink-0 inline-flex items-center gap-1 rounded-lg border border-border px-2.5 py-1 text-xs text-foreground active:bg-accent"
        @click="mode = mode === 'table' ? 'pdf' : 'table'"
      >
        <FileText v-if="mode === 'table'" :size="13" />
        <Table2 v-else :size="13" />
        <span>{{ mode === 'table' ? 'PDF 预览' : '表格模式' }}</span>
      </button>
    </div>

    <MobileOfficePreviewer
      v-if="mode === 'pdf'"
      :node="props.node"
      :version-id="props.versionId"
      @toggle-chrome="emit('toggle-chrome')"
      @download="emit('download')"
    />

    <div v-else class="flex-1 min-h-0 overflow-auto pb-24" @click="emit('toggle-chrome')">
      <div v-if="loading" class="py-16 text-center text-sm text-muted-foreground">正在加载表格…</div>
      <ErrorState v-else-if="errorText" :message="errorText" @retry="initWorkbook" />
      <EmptyState v-else-if="rows.length === 0" title="当前工作表为空" />
      <template v-else>
        <table class="border-collapse text-xs min-w-full">
          <thead class="sticky top-0 z-10 bg-muted">
            <tr>
              <th class="border border-border px-2 py-1.5 text-center font-medium text-muted-foreground w-10">
                #
              </th>
              <th
                v-for="col in colHeaders"
                :key="col"
                class="border border-border px-2.5 py-1.5 text-left font-medium text-muted-foreground whitespace-nowrap"
              >
                {{ col }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rIdx) in rows" :key="rIdx" class="even:bg-muted/20">
              <td class="border border-border px-2 py-1 text-center text-muted-foreground tabular-nums bg-muted/30">
                {{ rIdx + 1 }}
              </td>
              <td
                v-for="(_, cIdx) in colHeaders"
                :key="cIdx"
                class="border border-border px-2.5 py-1 text-foreground whitespace-nowrap max-w-[220px] truncate"
              >
                {{ row[cIdx] ?? '' }}
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="hasMore" class="py-4 text-center" @click.stop>
          <VanButton
            size="small"
            round
            plain
            type="primary"
            :loading="loadingMore"
            @click="loadRows(activeSheetIndex, false)"
          >
            加载更多行（已显示 {{ rows.length }} / {{ totalRows }}）
          </VanButton>
        </div>
      </template>
    </div>
  </div>
</template>
