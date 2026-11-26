<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Button } from '@repo/ui/button'
import { ADMIN_NAV_ITEMS } from '@/layout/navigation'

const route = useRoute()
const item = computed(() => ADMIN_NAV_ITEMS.find(entry => entry.name === route.name))
</script>

<template>
  <section class="rounded-xl border border-dashed border-border bg-card p-8 space-y-4">
    <div class="inline-flex items-center gap-2 rounded-full bg-muted px-3 py-1 text-xs text-muted-foreground">
      <span>{{ item?.phase || 'P1' }}</span>
      <span>·</span>
      <span>计划任务 {{ item?.taskId || '待排期' }}</span>
    </div>
    <h2 class="text-xl font-semibold">{{ item?.label || '后台功能模块' }}</h2>
    <p class="text-sm text-muted-foreground max-w-2xl leading-relaxed">
      {{ item?.description || '该管理后台模块已按 T005 路由清单预留独立入口，待对应后端接口与业务任务实现后接入。' }}
    </p>
    <div class="pt-2 flex items-center gap-3">
      <Button as-child variant="outline" size="sm">
        <RouterLink to="/">返回仪表盘</RouterLink>
      </Button>
      <Button as-child variant="outline" size="sm">
        <RouterLink to="/users">前往用户管理</RouterLink>
      </Button>
    </div>
  </section>
</template>
