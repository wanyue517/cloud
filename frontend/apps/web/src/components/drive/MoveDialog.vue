<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowLeft, ChevronRight, Folder, FolderPlus } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import { Input } from '@repo/ui/input'
import { toast } from '@repo/ui/toast'
import { drive, type DriveNode } from '@/api/drive'

const props = defineProps<{ nodes: DriveNode[] }>()
const emit = defineEmits<{ close: []; moved: [] }>()

const primarySpaceId = computed(() => props.nodes[0]?.spaceId || '')
const excludedIds = computed(() => new Set(props.nodes.map(n => n.id)))
const sameParent = computed(() => {
  if (!props.nodes.length) return false
  return props.nodes.every(n => n.parentId === parent.value)
})

const parent = ref('0')
const folders = ref<DriveNode[]>([])
const path = ref<DriveNode[]>([])
const page = ref(1)
const total = ref(0)
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const creating = ref(false)
const newFolderName = ref('')
let generation = 0

async function load() {
  if (!primarySpaceId.value) return
  const current = ++generation
  loading.value = true
  error.value = ''
  try {
    const [result, ancestors] = await Promise.all([
      drive.nodes(primarySpaceId.value, { parentId: parent.value, folders: true, page: page.value, pageSize: 50 }),
      parent.value === '0' ? Promise.resolve([]) : drive.ancestors(primarySpaceId.value, parent.value),
    ])
    if (current !== generation) return
    folders.value = result.items.filter(n => !excludedIds.value.has(n.id))
    total.value = result.total
    path.value = ancestors
  } catch (e) {
    error.value = e instanceof Error ? e.message : '读取目录失败'
  } finally {
    if (current === generation) loading.value = false
  }
}

function enter(id: string) {
  page.value = 1
  parent.value = id
  creating.value = false
}

async function createSubfolder() {
  const name = newFolderName.value.trim()
  if (!name || busy.value || !primarySpaceId.value) return
  busy.value = true
  error.value = ''
  try {
    const created = await drive.folder(primarySpaceId.value, parent.value, name)
    newFolderName.value = ''
    creating.value = false
    toast.success(`已新建文件夹「${created.name}」`)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '新建文件夹失败'
  } finally {
    busy.value = false
  }
}

async function move() {
  if (busy.value || !props.nodes.length) return
  busy.value = true
  error.value = ''
  let ok = 0
  const failures: string[] = []
  for (const node of props.nodes) {
    if (node.parentId === parent.value) continue
    try {
      await drive.change(node, node.name, parent.value)
      ok++
    } catch (e) {
      failures.push(`${node.name}：${e instanceof Error ? e.message : '失败'}`)
    }
  }
  busy.value = false
  if (failures.length) {
    error.value = failures.join('；')
    if (ok > 0) {
      toast.warning(`已移动 ${ok} 项，${failures.length} 项失败`)
      emit('moved')
    }
    return
  }
  toast.success(props.nodes.length === 1 ? `已移动「${props.nodes[0]!.name}」` : `已批量移动 ${ok} 项`)
  emit('moved')
  emit('close')
}

watch([parent, page], load, { immediate: true })
</script>

<template>
  <Dialog :open="true" @update:open="value => { if (!value && !busy) emit('close') }">
    <DialogContent class="!max-w-lg">
      <DialogHeader>
        <DialogTitle>{{ nodes.length === 1 ? `移动「${nodes[0]?.name}」` : `批量移动 ${nodes.length} 项` }}</DialogTitle>
        <DialogDescription>选择目标文件夹，原文件内容和历史版本保持关联。</DialogDescription>
      </DialogHeader>

      <div class="flex flex-wrap items-center justify-between gap-2">
        <nav aria-label="移动目标路径" class="flex flex-wrap items-center gap-1 text-sm">
          <Button variant="ghost" size="sm" class="h-7 px-2" @click="enter('0')">空间根目录</Button>
          <template v-for="item in path" :key="item.id">
            <ChevronRight :size="12" class="text-muted-foreground" />
            <Button variant="ghost" size="sm" class="h-7 px-2" @click="enter(item.id)">{{ item.name }}</Button>
          </template>
        </nav>
        <Button variant="outline" size="sm" class="h-7 px-2 text-xs" :disabled="busy" @click="creating = !creating; newFolderName = ''">
          <FolderPlus :size="13" /> 新建文件夹
        </Button>
      </div>

      <form v-if="creating" class="flex items-center gap-2" @submit.prevent="createSubfolder">
        <Input v-model="newFolderName" class="h-8 text-sm" placeholder="输入新文件夹名称" :disabled="busy" autofocus />
        <Button type="submit" size="sm" class="h-8" :disabled="busy || !newFolderName.trim()">创建</Button>
        <Button type="button" variant="ghost" size="sm" class="h-8" :disabled="busy" @click="creating = false">取消</Button>
      </form>

      <div class="min-h-48 max-h-64 overflow-auto rounded-lg border p-2">
        <p v-if="loading" class="p-5 text-center text-sm text-muted-foreground">正在加载…</p>
        <template v-else>
          <Button v-if="parent !== '0'" variant="ghost" class="w-full justify-start" @click="enter(path.length > 1 ? path[path.length - 2]!.id : '0')">
            <ArrowLeft :size="16" /> 上一级
          </Button>
          <Button v-for="folder in folders" :key="folder.id" variant="ghost" class="w-full justify-start" @click="enter(folder.id)">
            <Folder :size="16" class="text-amber-500" /> <span class="truncate">{{ folder.name }}</span>
          </Button>
          <p v-if="!folders.length" class="p-6 text-center text-sm text-muted-foreground">此目录没有可选的子文件夹</p>
        </template>
      </div>

      <div v-if="total > 50" class="flex items-center justify-between text-xs text-muted-foreground">
        <Button variant="outline" size="sm" :disabled="page === 1" @click="page--">上一页</Button>
        <span>第 {{ page }} / {{ Math.ceil(total / 50) }} 页</span>
        <Button variant="outline" size="sm" :disabled="page * 50 >= total" @click="page++">下一页</Button>
      </div>

      <p v-if="error" role="alert" class="text-sm text-destructive">{{ error }}</p>
      <DialogFooter>
        <Button variant="outline" :disabled="busy" @click="emit('close')">取消</Button>
        <Button :disabled="busy || sameParent" @click="move">{{ busy ? '正在移动…' : '移动到此处' }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
