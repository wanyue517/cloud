<script setup lang="ts">
import type { CheckboxRootEmits, CheckboxRootProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { Check, Minus } from '@lucide/vue'
import { CheckboxIndicator, CheckboxRoot, useForwardPropsEmits } from 'reka-ui'
import { cn } from '../../lib/utils'

const props = defineProps<CheckboxRootProps & { class?: HTMLAttributes['class'] }>()
const emits = defineEmits<CheckboxRootEmits>()
const forwarded = useForwardPropsEmits(props, emits)
</script>

<template>
  <CheckboxRoot
    data-slot="checkbox"
    v-bind="forwarded"
    :class="cn(
      'peer size-4 shrink-0 rounded-[4px] border border-input bg-background shadow-2xs transition-colors outline-none',
      'focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50',
      'disabled:cursor-not-allowed disabled:opacity-50',
      'data-[state=checked]:border-primary data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground',
      'data-[state=indeterminate]:border-primary data-[state=indeterminate]:bg-primary data-[state=indeterminate]:text-primary-foreground',
      props.class,
    )"
  >
    <CheckboxIndicator
      data-slot="checkbox-indicator"
      class="grid place-content-center text-current transition-none"
    >
      <Minus v-if="props.modelValue === 'indeterminate'" class="size-3 stroke-[2.5]" />
      <Check v-else class="size-3.5 stroke-[2.5]" />
    </CheckboxIndicator>
  </CheckboxRoot>
</template>
