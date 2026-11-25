<script setup lang="ts">
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@repo/ui/dialog'
import ThemeSettings from '@/components/drive/ThemeSettings.vue'
import MoveDialog from '@/components/drive/MoveDialog.vue'
import { ContextMenu, ContextMenuTrigger, ContextMenuContent, ContextMenuItem } from '@repo/ui/context-menu'
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Cloud, Folder, File, Upload, FolderPlus, Search, Grid2X2, List, Trash2, Star, LogOut, Plus, ChevronRight, RefreshCw, X } from '@lucide/vue'
import { drive, uploadFile, type DriveNode, type Space, type Version } from '@/api/drive'
import FolderTree from '@/components/drive/FolderTree.vue'
import PreviewDialog from '@/components/drive/PreviewDialog.vue'
import { logout as endSession } from '@repo/session/session'
import { request, saveBlob } from '@/utils/request'
import '@/styles/drive.css'

const route = useRoute(), router = useRouter()
const spaces = ref<Space[]>([]), nodes = ref<DriveNode[]>([]), ancestors = ref<DriveNode[]>([])
const selected = ref(new Set<string>()), page = ref(1), total = ref(0), sort = ref('name'), query = ref(''), hidden = ref(false)
const view = ref(localStorage.getItem('cloudlane.view') || 'list'), loading = ref(false), error = ref(''), notice = ref(''), treeRevision = ref(0)
const mode = ref<'files' | 'trash' | 'favorites'>('files'), preview = ref<DriveNode>(), previewVersion = ref<Version>(), versions = ref<Version[]>([]), versionNode = ref<DriveNode>()
const spaceId = computed(() => String(route.params.spaceId || spaces.value[0]?.id || ''))
const parentId = computed(() => String(route.params.folderId || '0'))
const currentSpace = computed(() => spaces.value.find(s => s.id === spaceId.value))
const currentSelection = computed(() => nodes.value.filter(n => selected.value.has(n.id)))
const fileInput = ref<HTMLInputElement>(), folderInput = ref<HTMLInputElement>(), replacement = ref<HTMLInputElement>()
const replacing = ref<DriveNode>(), uploadTasks = ref<{ name: string; percent: number; status: string }[]>([])
const moving = ref<DriveNode>(), admin = ref(false)
const uploading = ref(false), controller = ref<AbortController>()
let generation = 0, searchTimer: ReturnType<typeof setTimeout> | undefined
const dialog = ref<{ title: string; label: string; value: string; submit: (value: string) => Promise<unknown> }>()
const dialogBusy = ref(false)
function ask(title: string, label: string, value: string, submit: (value: string) => Promise<unknown>) { dialog.value = { title, label, value, submit }; error.value = '' }
async function submitDialog() {
  if (!dialog.value || dialogBusy.value) return
  dialogBusy.value = true; error.value = ''
  try { await dialog.value.submit(dialog.value.value); dialog.value = undefined; await refresh(); treeRevision.value++ }
  catch (e) { fail(e) } finally { dialogBusy.value = false }
}
function fail(e: unknown) { error.value = e instanceof Error ? e.message : '操作失败，请重试' }
async function loadSpaces() { spaces.value = await drive.spaces() }
async function refresh() {
  if (!spaceId.value) return
  const current = ++generation; loading.value = true; error.value = ''; selected.value = new Set()
  try {
    const [result, path] = await Promise.all([
      drive.nodes(spaceId.value, { parentId: parentId.value, page: page.value, pageSize: 100, sort: sort.value, query: query.value, hidden: hidden.value, trash: mode.value === 'trash', favorites: mode.value === 'favorites' }),
      parentId.value === '0' || mode.value !== 'files' ? Promise.resolve([]) : drive.ancestors(spaceId.value, parentId.value),
    ])
    if (current !== generation) return
    nodes.value = result.items; total.value = result.total; ancestors.value = path
  } catch (e) { if (current === generation) fail(e) } finally { if (current === generation) loading.value = false }
}
function navigate(id: string, space = spaceId.value) {
  mode.value = 'files'; query.value = ''; page.value = 1
  const target = id === '0' ? `/app/spaces/${space}` : `/app/spaces/${space}/folders/${id}`
  if (route.path === target) void refresh(); else void router.push(target)
}
async function switchMode(value: typeof mode.value) { mode.value = value; page.value = 1; query.value = ''; await refresh() }
function choose(node: DriveNode, event: Pick<MouseEvent, 'ctrlKey' | 'metaKey' | 'shiftKey'>) {
  const next = new Set(event.ctrlKey || event.metaKey ? selected.value : [])
  if (event.shiftKey && selected.value.size) {
    const anchor = nodes.value.findIndex(n => n.id === [...selected.value][0]), end = nodes.value.indexOf(node)
    nodes.value.slice(Math.min(anchor, end), Math.max(anchor, end) + 1).forEach(n => next.add(n.id))
  } else { next.has(node.id) ? next.delete(node.id) : next.add(node.id) }
  selected.value = next
}
function open(node: DriveNode) { if (mode.value === 'trash') return; if (node.nodeType === 'FOLDER') navigate(node.id); else { previewVersion.value = undefined; preview.value = node } }
function newFolder() { ask('新建文件夹', '文件夹名称', '新建文件夹', name => drive.folder(spaceId.value, parentId.value, name)) }
function newSpace() { ask('新建空间', '空间名称', '', async name => { const id = await drive.createSpace(name); await loadSpaces(); navigate('0', id) }) }
function rename(node: DriveNode) { ask('重命名', '名称', node.name, name => drive.change(node, name)) }
async function move(source: string, target: string) {
  const node = nodes.value.find(n => n.id === source)
  if (!node) return
  try { await drive.change(node, node.name, target); await refresh(); treeRevision.value++ } catch (e) { fail(e) }
}
function moveSelection() { moving.value = currentSelection.value[0] }
async function batch(action: (node: DriveNode) => Promise<unknown>) {
  let success = 0; const failures: string[] = []
  for (const node of currentSelection.value) { try { await action(node); success++ } catch (e) { failures.push(`${node.name}：${e instanceof Error ? e.message : '失败'}`) } }
  await refresh(); treeRevision.value++; notice.value = `成功 ${success} 项${failures.length ? `；${failures.join('；')}` : ''}`
}
function deleteSelection() { const count = selected.value.size; ask('移到回收站', `输入“删除”确认将 ${count} 项移入回收站`, '', async value => { if (value !== '删除') throw new Error('请输入“删除”确认'); await batch(drive.trash) }) }
async function download(node: DriveNode) { try { saveBlob(await drive.content(node), node.name) } catch (e) { fail(e) } }
async function showVersions(node: DriveNode) { try { versions.value = await drive.versions(node); versionNode.value = node } catch (e) { fail(e) } }
async function restoreVersion(version: Version) {
  if (!versionNode.value) return
  try { await drive.restoreVersion(versionNode.value, version.id); versionNode.value = undefined; await refresh(); notice.value = '已创建新的当前版本，历史版本保留' } catch (e) { fail(e) }
}
async function replace(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !replacing.value) return
  try { await drive.replace(replacing.value, file); notice.value = '已保存新版本'; await refresh() } catch (e) { fail(e) }
  ;(event.target as HTMLInputElement).value = ''
}
async function upload(files: File[]) {
  if (uploading.value || !spaceId.value) return
  const targetSpace = spaceId.value, targetParent = parentId.value
  uploading.value = true; controller.value = new AbortController(); const signal = controller.value.signal
  const folders = new Map<string, string>(); folders.set('', targetParent)
  uploadTasks.value = files.map(file => ({ name: file.webkitRelativePath || file.name, percent: 0, status: '等待上传' }))
  for (let index = 0; index < files.length; index++) {
    const file = files[index]!, task = uploadTasks.value[index]!
    if (signal.aborted) { task.status = '已暂停，重新选择文件可续传'; continue }
    try {
      let parent = targetParent, path = ''
      for (const name of (file.webkitRelativePath || file.name).split('/').slice(0, -1)) {
        const next = path ? `${path}/${name}` : name
        if (!folders.has(next)) {
          try { folders.set(next, (await drive.folder(targetSpace, parent, name)).id) }
          catch (e) {
            const existing = await drive.nodes(targetSpace, { parentId: parent, folders: true, pageSize: 200 })
            const folder = existing.items.find(n => n.name === name)
            if (!folder) throw e
            folders.set(next, folder.id)
          }
        }
        parent = folders.get(next)!; path = next
      }
      task.status = '上传中'
      await uploadFile(targetSpace, parent, file, percent => { task.percent = percent }, signal)
      task.status = '完成'
    } catch (e) { task.status = signal.aborted ? '已暂停，重新选择文件可续传' : e instanceof Error ? e.message : '上传失败' }
  }
  uploading.value = false; treeRevision.value++; await refresh()
}
function selectFiles(event: Event) { const target = event.target as HTMLInputElement; void upload(Array.from(target.files || [])); target.value = '' }
function dropFiles(event: DragEvent) { if (event.dataTransfer?.files.length) void upload(Array.from(event.dataTransfer.files)) }
function size(bytes: number) { if (!bytes) return '0 B'; const units = ['B', 'KB', 'MB', 'GB']; const index = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024))); return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}` }
async function logout() { try { await endSession() } catch (e) { fail(e) } finally { void router.replace('/auth/login') } }
function key(event: KeyboardEvent) {
  if ((event.target as HTMLElement)?.closest('input,textarea,select,[contenteditable]') || dialog.value || preview.value || versionNode.value) return
  if ((event.ctrlKey || event.metaKey) && event.key === 'a') { event.preventDefault(); selected.value = new Set(nodes.value.map(n => n.id)) }
  if (event.key === 'Escape') selected.value = new Set()
  if (event.key === 'F2' && currentSelection.value[0]) { event.preventDefault(); rename(currentSelection.value[0]) }
  if (event.key === 'Delete' && selected.value.size && mode.value !== 'trash') deleteSelection()
  if (event.key === 'Enter' && currentSelection.value[0]) open(currentSelection.value[0])
}
watch(() => route.fullPath, () => { page.value = 1; void refresh() })
watch([sort, hidden, page], () => void refresh())
watch(query, () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => { page.value = 1; void refresh() }, 300) })
watch(view, value => localStorage.setItem('cloudlane.view', value))
onMounted(async () => {
  document.addEventListener('keydown', key)
  try {
    await loadSpaces()
    admin.value = (await request.get<{ admin: boolean }>('/v1/me')).admin
    if (!route.params.spaceId && spaces.value[0]) navigate('0', spaces.value[0].id); else await refresh()
  } catch (e) { fail(e) }
})
onBeforeUnmount(() => { generation++; clearTimeout(searchTimer); controller.value?.abort(); document.removeEventListener('keydown', key) })
</script>

<template>
  <div class="drive-app">
    <aside class="drive-sidebar">
      <a class="drive-brand" href="/app"><span><Cloud :size="25" /></span>Cloudlane<small>你的云端文件工作台</small></a>
      <Button variant="default" class="primary upload-main" :disabled="!spaceId || uploading" @click="fileInput?.click()"><Upload :size="17" /> 上传文件</Button>
      <div class="sidebar-caption">我的空间<Button variant="outline" aria-label="新建空间" @click="newSpace"><Plus :size="17" /></Button></div>
      <div v-for="space in spaces" :key="space.id" class="space-section">
        <Button variant="outline" class="space-button" :class="{ active: spaceId === space.id && mode === 'files' }" @click="navigate('0', space.id)"><Folder :size="17" :style="{ color: space.color }" /><span>{{ space.name }}</span></Button>
        <FolderTree v-if="spaceId === space.id && mode === 'files'" :space="space.id" parent="0" :active="parentId" :revision="treeRevision" @navigate="navigate" @move="move" />
      </div>
      <div class="sidebar-caption">文件管理</div>
      <Button variant="outline" class="nav-button" :class="{ active: mode === 'favorites' }" @click="switchMode('favorites')"><Star :size="17" /> 我的收藏</Button>
      <Button variant="outline" class="nav-button" :class="{ active: mode === 'trash' }" @click="switchMode('trash')"><Trash2 :size="17" /> 回收站</Button>
      <div class="sidebar-bottom"><ThemeSettings /><Button v-if="admin" as-child variant="outline"><a href="/admin/">管理后台</a></Button><Button variant="outline" @click="ask('个人资料', '显示昵称', '', value => request.put('/v1/me', { displayName: value }))">个人资料</Button><span>文件属于你，灵感随时可达</span><Button variant="outline" @click="logout"><LogOut :size="16" /> 退出登录</Button></div>
    </aside>
    <main class="drive-main" @dragover.prevent @drop.prevent="dropFiles">
      <header class="drive-topbar"><span>个人网盘 <ChevronRight :size="14" /> {{ mode === 'trash' ? '回收站' : mode === 'favorites' ? '我的收藏' : currentSpace?.name || '欢迎使用' }}</span><label class="drive-search"><Search :size="17" /><input v-model="query" aria-label="搜索当前空间" placeholder="搜索当前空间的文件…" /></label></header>
      <section class="drive-content">
        <div class="drive-heading"><div><p>所有内容，井然有序</p><h1>{{ mode === 'trash' ? '回收站' : mode === 'favorites' ? '我的收藏' : currentSpace?.name || '我的文件' }}</h1></div><div class="heading-actions"><Button variant="outline" v-if="currentSpace && mode === 'files'" @click="ask('重命名空间', '空间名称', currentSpace.name, async name => { await drive.updateSpace(spaceId, name); await loadSpaces() })">空间设置</Button><Button variant="outline" v-if="mode === 'files'" @click="newFolder"><FolderPlus :size="16" /> 新建文件夹</Button><Button variant="default" class="primary" :disabled="uploading || !spaceId" @click="fileInput?.click()"><Upload :size="16" /> 上传文件</Button></div></div>
        <nav class="breadcrumbs" aria-label="目录路径"><Button variant="outline" @click="navigate('0')">{{ currentSpace?.name || '根目录' }}</Button><template v-for="node in ancestors" :key="node.id"><ChevronRight :size="13" /><Button variant="outline" @click="navigate(node.id)">{{ node.name }}</Button></template></nav>
        <div class="drive-toolbar"><span>{{ total }} 项<small v-if="selected.size"> · 已选 {{ selected.size }} 项</small></span><div><Button variant="outline" v-if="mode === 'files'" :disabled="uploading" @click="folderInput?.click()">上传文件夹</Button><label><input v-model="hidden" type="checkbox" /> 显示隐藏项</label><select v-model="sort" aria-label="文件排序"><option value="name">名称排序</option><option value="updated">最近修改</option><option value="size">文件大小</option></select><Button variant="outline" aria-label="列表视图" :aria-pressed="view === 'list'" @click="view = 'list'"><List :size="17" /></Button><Button variant="outline" aria-label="网格视图" :aria-pressed="view === 'grid'" @click="view = 'grid'"><Grid2X2 :size="17" /></Button><Button variant="outline" aria-label="刷新文件" @click="refresh"><RefreshCw :size="16" /></Button></div></div>
        <p v-if="error" class="drive-error" role="alert">{{ error }}<Button variant="outline" @click="error = ''" aria-label="关闭错误"><X :size="15" /></Button></p>
        <p v-if="notice" class="drive-notice" role="status">{{ notice }}<Button variant="outline" @click="notice = ''" aria-label="关闭提示"><X :size="15" /></Button></p>
        <div v-if="selected.size" class="selection-toolbar"><template v-if="mode === 'trash'"><Button variant="outline" @click="batch(drive.restore)">恢复选中项</Button></template><template v-else><Button variant="outline" v-if="selected.size === 1" @click="rename(currentSelection[0]!)">重命名</Button><Button variant="outline" v-if="selected.size === 1" @click="moveSelection">移动</Button><Button variant="outline" @click="batch(node => drive.favorite(node, mode !== 'favorites'))">{{ mode === 'favorites' ? '取消收藏' : '收藏' }}</Button><Button variant="outline" class="danger" @click="deleteSelection">移到回收站</Button><template v-if="selected.size === 1 && currentSelection[0]?.nodeType === 'FILE'"><Button variant="outline" @click="download(currentSelection[0]!)">下载</Button><Button variant="outline" @click="showVersions(currentSelection[0]!)">历史版本</Button><Button variant="outline" @click="replacing = currentSelection[0]; replacement?.click()">替换文件</Button></template></template></div>
        <div v-if="loading" class="drive-empty" role="status">正在加载文件…</div>
        <div v-else-if="!nodes.length" class="drive-empty"><Folder :size="48" /><h2>{{ query ? '没有找到相关文件' : mode === 'trash' ? '回收站是空的' : mode === 'favorites' ? '还没有收藏' : '为想法留一个位置' }}</h2><p>{{ mode === 'files' ? '上传文件或新建文件夹，开始整理你的空间' : '文件会在这里集中显示' }}</p><Button variant="outline" v-if="mode === 'files'" @click="fileInput?.click()">选择文件上传</Button></div>
        <div v-else class="file-collection" :class="view" role="list" aria-label="文件列表">
          <div v-if="view === 'list'" class="file-columns"><span>名称</span><span>大小</span><span>修改时间</span><span>类型</span></div>
          <ContextMenu v-for="node in nodes" :key="node.id"><ContextMenuTrigger as-child><div class="file-item" :class="{ selected: selected.has(node.id) }" role="listitem" tabindex="0" draggable="true" @dragstart="$event.dataTransfer?.setData('application/cloudlane-node', node.id)" @dragover.prevent @drop.prevent.stop="node.nodeType === 'FOLDER' && move($event.dataTransfer?.getData('application/cloudlane-node') || '', node.id)" @click="choose(node, $event)" @dblclick="open(node)" @contextmenu.prevent="selected = new Set([node.id])" @keydown.enter.stop="open(node)">
            <span class="file-name"><input type="checkbox" :checked="selected.has(node.id)" :aria-label="`选择 ${node.name}`" @click.stop @change="choose(node, { ctrlKey: true, metaKey: false, shiftKey: false })" /><span class="file-icon" :class="{ folder: node.nodeType === 'FOLDER' }"><Folder v-if="node.nodeType === 'FOLDER'" :size="view === 'grid' ? 42 : 24" /><File v-else :size="view === 'grid' ? 38 : 22" /></span><span class="filename">{{ node.name }}</span></span><span>{{ node.nodeType === 'FOLDER' ? '—' : size(node.size) }}</span><time :datetime="node.updatedAt">{{ new Date(node.updatedAt).toLocaleString('zh-CN') }}</time><small>{{ node.nodeType === 'FOLDER' ? '文件夹' : node.name.split('.').pop()?.toUpperCase() || '文件' }}</small>
          </div></ContextMenuTrigger><ContextMenuContent><template v-if="mode === 'trash'"><ContextMenuItem @select="selected = new Set([node.id]); batch(drive.restore)">恢复</ContextMenuItem></template><template v-else><ContextMenuItem @select="open(node)">{{ node.nodeType === 'FOLDER' ? '打开文件夹' : '预览' }}</ContextMenuItem><ContextMenuItem v-if="node.nodeType === 'FILE'" @select="download(node)">下载</ContextMenuItem><ContextMenuItem @select="rename(node)">重命名</ContextMenuItem><ContextMenuItem @select="moving = node">移动到…</ContextMenuItem><ContextMenuItem @select="selected = new Set([node.id]); batch(n => drive.favorite(n, true))">收藏</ContextMenuItem><ContextMenuItem class="text-destructive" @select="selected = new Set([node.id]); deleteSelection()">移到回收站</ContextMenuItem></template></ContextMenuContent></ContextMenu>
        </div>
        <footer class="drive-pagination"><Button variant="outline" :disabled="page <= 1" @click="page--">上一页</Button><span>第 {{ page }} 页 · 共 {{ Math.max(1, Math.ceil(total / 100)) }} 页</span><Button variant="outline" :disabled="page * 100 >= total" @click="page++">下一页</Button></footer>
      </section>
    </main>
    <input ref="fileInput" type="file" multiple hidden @change="selectFiles" /><input ref="folderInput" type="file" multiple webkitdirectory hidden @change="selectFiles" /><input ref="replacement" type="file" hidden @change="replace" />
    <aside v-if="currentSelection.length === 1" class="drive-details"><header><h2>文件详情</h2><Button variant="ghost" size="icon" aria-label="关闭文件详情" @click="selected = new Set()"><X :size="16" /></Button></header><Folder v-if="currentSelection[0]!.nodeType === 'FOLDER'" :size="48" class="text-yellow-600 my-6" /><File v-else :size="48" class="text-primary my-6" /><h3>{{ currentSelection[0]!.name }}</h3><dl><dt>大小</dt><dd>{{ size(currentSelection[0]!.size) }}</dd><dt>修改时间</dt><dd>{{ new Date(currentSelection[0]!.updatedAt).toLocaleString() }}</dd><dt>类型</dt><dd>{{ currentSelection[0]!.nodeType === 'FOLDER' ? '文件夹' : currentSelection[0]!.mimeType }}</dd></dl></aside>
    <MoveDialog v-if="moving" :node="moving" @close="moving = undefined" @moved="refresh(); treeRevision++" />
    <aside v-if="uploadTasks.length" class="upload-panel" aria-label="上传任务"><header><strong>上传任务</strong><Button variant="outline" v-if="uploading" @click="controller?.abort()">暂停</Button><Button variant="outline" v-else @click="uploadTasks = []" aria-label="关闭上传面板"><X :size="17" /></Button></header><div v-for="(task, i) in uploadTasks" :key="i"><span>{{ task.name }}</span><small>{{ task.status }} · {{ task.percent }}%</small><progress :value="task.percent" max="100" /></div></aside>
    <PreviewDialog v-if="preview" :node="preview" :version="previewVersion" @close="preview = undefined" />
    <Dialog :open="!!dialog" @update:open="value => { if (!value && !dialogBusy) dialog = undefined }">
      <DialogContent><template v-if="dialog"><DialogHeader><DialogTitle>{{ dialog.title }}</DialogTitle><DialogDescription>{{ dialog.label }}</DialogDescription></DialogHeader><form @submit.prevent="submitDialog"><Input v-model="dialog.value" :aria-label="dialog.label" :disabled="dialogBusy" /><p v-if="error" role="alert" class="text-destructive text-sm mt-3">{{ error }}</p><DialogFooter class="mt-5"><Button variant="outline" type="button" :disabled="dialogBusy" @click="dialog = undefined">取消</Button><Button type="submit" :disabled="dialogBusy">{{ dialogBusy ? '正在保存…' : '确认' }}</Button></DialogFooter></form></template></DialogContent>
    </Dialog>
    <Dialog :open="!!versionNode" @update:open="value => { if (!value) versionNode = undefined }"><DialogContent><template v-if="versionNode"><DialogHeader><DialogTitle>{{ versionNode.name }} · 历史版本</DialogTitle><DialogDescription>恢复历史内容会创建新版本，原有记录保留。</DialogDescription></DialogHeader><div v-for="version in versions" :key="version.id" class="flex items-center gap-3 py-2"><span class="flex-1 text-sm">版本 {{ version.number }} · {{ size(version.size) }}<small class="block text-muted-foreground">{{ new Date(version.createdAt).toLocaleString() }}</small></span><Button variant="outline" @click="previewVersion = version; preview = versionNode">预览</Button><Button variant="outline" :disabled="version.id === versionNode.currentVersionId" @click="restoreVersion(version)">恢复</Button></div></template></DialogContent></Dialog>
  </div>
</template>
