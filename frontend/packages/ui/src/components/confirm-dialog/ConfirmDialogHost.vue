<script setup lang="ts">
import { Button } from '../button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../dialog'
import { settleConfirm, useConfirmState } from './confirm'

const state = useConfirmState()
</script>

<template>
  <Dialog :open="state.open" @update:open="v => { if (!v) settleConfirm(false) }">
    <DialogContent class="!max-w-md !z-[100]" overlay-class="!z-[99]">
      <DialogHeader>
        <DialogTitle>{{ state.title }}</DialogTitle>
        <DialogDescription v-if="state.description">{{ state.description }}</DialogDescription>
      </DialogHeader>
      <DialogFooter class="mt-2">
        <Button type="button" variant="outline" @click="settleConfirm(false)">{{ state.cancelText }}</Button>
        <Button
          type="button"
          :variant="state.variant === 'destructive' ? 'destructive' : 'default'"
          @click="settleConfirm(true)"
        >
          {{ state.confirmText }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
