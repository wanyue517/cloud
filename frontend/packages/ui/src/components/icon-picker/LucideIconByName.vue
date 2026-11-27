<script setup lang="ts">
import { computed } from 'vue'
import { resolveLucideIcon } from './icons'

const props = withDefaults(defineProps<{
  name?: string | null
  size?: number
}>(), {
  name: 'folder',
  size: 16,
})

const iconComponent = computed(() => resolveLucideIcon(props.name))
</script>

<template>
  <component :is="iconComponent" :size="size" v-bind="$attrs" />
</template>
