<script setup lang="ts">
import { shallowRef, watch } from 'vue'
import {
  DEFAULT_FOLDER_NODES,
  loadAllLucideIcons,
  resolveLucideNodesSync,
  type LucideSvgNode,
} from './icons'

const props = withDefaults(defineProps<{
  name?: string | null
  size?: number
  strokeWidth?: number
}>(), {
  name: 'folder',
  size: 16,
  strokeWidth: 2,
})

const nodes = shallowRef<LucideSvgNode[]>(resolveLucideNodesSync(props.name) ?? DEFAULT_FOLDER_NODES)

async function syncNodes(iconName?: string | null) {
  const immediate = resolveLucideNodesSync(iconName)
  if (immediate) {
    nodes.value = immediate
    return
  }
  await loadAllLucideIcons()
  nodes.value = resolveLucideNodesSync(iconName) ?? DEFAULT_FOLDER_NODES
}

watch(() => props.name, next => { void syncNodes(next) }, { immediate: true })
</script>

<template>
  <svg
    xmlns="http://www.w3.org/2000/svg"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-bind="$attrs"
  >
    <component :is="tag" v-for="([tag, attrs], idx) in nodes" :key="idx" v-bind="attrs" />
  </svg>
</template>
