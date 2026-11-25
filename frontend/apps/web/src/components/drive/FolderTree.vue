<script setup lang="ts">
import { Button } from '@repo/ui/button'
import { ref, watch } from 'vue'
import { ChevronRight, Folder } from '@lucide/vue'
import { drive, type DriveNode } from '@/api/drive'
const props = defineProps<{ space: string; parent: string; active: string; revision: number }>()
const emit = defineEmits<{ navigate: [id: string]; move: [source: string, target: string] }>()
const nodes = ref<DriveNode[]>([]), expanded = ref(new Set<string>()), error = ref(''), loading = ref(false)
let generation = 0
async function load() {
  const current = ++generation; loading.value = true; error.value = ''
  try {
    const result = await drive.nodes(props.space, { parentId: props.parent, folders: true, pageSize: 200 })
    if (current === generation) nodes.value = result.items
  } catch (e) { if (current === generation) error.value = e instanceof Error ? e.message : '加载失败' }
  finally { if (current === generation) loading.value = false }
}
function toggle(id: string) { const next = new Set(expanded.value); next.has(id) ? next.delete(id) : next.add(id); expanded.value = next }
function drop(event: DragEvent, target: string) { const source = event.dataTransfer?.getData('application/cloudlane-node'); if (source) emit('move', source, target) }
watch(() => [props.space, props.parent, props.revision], load, { immediate: true })
</script>
<template>
  <div class="folder-tree" role="group">
    <span v-if="loading" class="tree-message">正在加载…</span>
    <Button variant="outline" v-if="error" @click="load">{{ error }}，点击重试</Button>
    <div v-for="node in nodes" :key="node.id">
      <div class="tree-row" :class="{ active: node.id === active }" @dragover.prevent @drop.prevent.stop="drop($event, node.id)">
        <Button variant="outline" class="tree-toggle" :aria-label="`${expanded.has(node.id) ? '收起' : '展开'}${node.name}`" :aria-expanded="expanded.has(node.id)" @click="toggle(node.id)"><ChevronRight :size="13" :class="{ rotated: expanded.has(node.id) }" /></Button>
        <Button variant="outline" class="tree-label" @click="emit('navigate', node.id)"><Folder :size="15" /><span>{{ node.name }}</span></Button>
      </div>
      <FolderTree v-if="expanded.has(node.id)" :space="space" :parent="node.id" :active="active" :revision="revision" @navigate="emit('navigate', $event)" @move="(s, t) => emit('move', s, t)" />
    </div>
  </div>
</template>
