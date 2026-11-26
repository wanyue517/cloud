<script setup lang="ts">
import { ConfirmDialogHost } from '@repo/ui/confirm-dialog'
import { Toaster } from '@repo/ui/toast'
</script>

<template>
  <RouterView />
  <Toaster />
  <ConfirmDialogHost />
</template>
