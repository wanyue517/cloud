<script setup lang="ts">
import { ConfirmDialogHost } from '@repo/ui/confirm-dialog'
import { Toaster } from '@repo/ui/toast'
import MobileSwitchBanner from '@/components/MobileSwitchBanner.vue'
</script>

<template>
  <RouterView />
  <MobileSwitchBanner />
  <Toaster />
  <ConfirmDialogHost />
</template>
