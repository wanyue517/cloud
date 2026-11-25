<script setup lang="ts">
import type { ContextMenuContentProps, ContextMenuContentEmits } from 'reka-ui'
import { ContextMenuPortal, ContextMenuContent, useForwardPropsEmits } from 'reka-ui'
const props = defineProps<ContextMenuContentProps>()
const emits = defineEmits<ContextMenuContentEmits>()
const forwarded = useForwardPropsEmits(props, emits)
</script>
<template>
  <ContextMenuPortal><ContextMenuContent v-bind="forwarded" class="z-50 min-w-40 rounded-md border bg-popover p-1 text-popover-foreground shadow-md [&_[role=menuitem]]:flex [&_[role=menuitem]]:cursor-default [&_[role=menuitem]]:rounded-sm [&_[role=menuitem]]:px-3 [&_[role=menuitem]]:py-2 [&_[role=menuitem]]:text-sm [&_[role=menuitem]]:outline-none [&_[role=menuitem][data-highlighted]]:bg-accent [&_[role=menuitem][data-disabled]]:opacity-40"><slot /></ContextMenuContent></ContextMenuPortal>
</template>
