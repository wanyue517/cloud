<script setup lang="ts">
import { ref, watch } from 'vue'
import { Folder, ChevronRight, ArrowLeft } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@repo/ui/dialog'
import { drive, type DriveNode } from '@/api/drive'
const props = defineProps<{ node: DriveNode }>()
const emit = defineEmits<{ close: []; moved: [] }>()
const parent = ref('0'), folders = ref<DriveNode[]>([]), path = ref<DriveNode[]>([]), page = ref(1), total = ref(0)
const busy = ref(false), loading = ref(false), error = ref('')
let generation = 0
async function load() {
  const current = ++generation; loading.value = true; error.value = ''
  try {
    const [result, ancestors] = await Promise.all([drive.nodes(props.node.spaceId, { parentId: parent.value, folders: true, page: page.value, pageSize: 50 }), parent.value === '0' ? Promise.resolve([]) : drive.ancestors(props.node.spaceId, parent.value)])
    if (current !== generation) return
    folders.value = result.items.filter(n => n.id !== props.node.id); total.value = result.total; path.value = ancestors
  } catch (e) { error.value = e instanceof Error ? e.message : '读取目录失败' }
  finally { if (current === generation) loading.value = false }
}
function enter(id: string) { page.value = 1; parent.value = id }
async function move() {
  busy.value = true
  try { await drive.change(props.node, props.node.name, parent.value); emit('moved'); emit('close') }
  catch (e) { error.value = e instanceof Error ? e.message : '移动失败' }
  finally { busy.value = false }
}
watch([parent, page], load, { immediate: true })
</script>
<template>
  <Dialog :open="true" @update:open="value => { if (!value && !busy) emit('close') }"><DialogContent>
    <DialogHeader><DialogTitle>移动「{{ node.name }}」</DialogTitle><DialogDescription>选择目标文件夹，原文件内容和历史版本保持关联。</DialogDescription></DialogHeader>
    <nav aria-label="移动目标路径" class="flex flex-wrap items-center gap-1 text-sm"><Button variant="ghost" size="sm" @click="enter('0')">空间根目录</Button><template v-for="item in path" :key="item.id"><ChevronRight :size="12" /><Button variant="ghost" size="sm" @click="enter(item.id)">{{ item.name }}</Button></template></nav>
    <div class="border rounded-lg min-h-48 max-h-64 overflow-auto p-2"><p v-if="loading" class="p-5 text-muted-foreground">正在加载…</p><template v-else><Button v-if="parent !== '0'" variant="ghost" class="w-full justify-start" @click="enter(path.length > 1 ? path[path.length - 2]!.id : '0')"><ArrowLeft :size="16" /> 上一级</Button><Button v-for="folder in folders" :key="folder.id" variant="ghost" class="w-full justify-start" @click="enter(folder.id)"><Folder :size="16" /> {{ folder.name }}</Button><p v-if="!folders.length" class="text-center p-6 text-muted-foreground text-sm">此目录没有可选的子文件夹</p></template></div>
    <div v-if="total > 50" class="flex justify-between"><Button variant="outline" :disabled="page === 1" @click="page--">上一页</Button><Button variant="outline" :disabled="page * 50 >= total" @click="page++">下一页</Button></div>
    <p v-if="error" role="alert" class="text-sm text-destructive">{{ error }}</p>
    <DialogFooter><Button variant="outline" :disabled="busy" @click="emit('close')">取消</Button><Button :disabled="busy || parent === node.parentId" @click="move">{{ busy ? '正在移动…' : '移动到此处' }}</Button></DialogFooter>
  </DialogContent></Dialog>
</template>
