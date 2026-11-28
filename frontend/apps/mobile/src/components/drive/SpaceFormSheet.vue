<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Button as VanButton, showConfirmDialog } from 'vant'
import { Trash2, Users } from '@lucide/vue'
import { toast } from '@repo/ui/toast'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { mobileDriveApi, type Space } from '@/api/drive'
import { canDeleteSpace } from '@/lib/spaces'
import { BottomSheet } from '@/components/common'

const COLOR_PRESETS = ['#2563eb', '#7c3aed', '#15803d', '#be185d', '#c2410c', '#0f766e', '#334155']

const props = defineProps<{
  open: boolean
  space: Space | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: [spaceId?: string]
  deleted: [spaceId: string]
}>()

const isEdit = computed(() => Boolean(props.space))
const saving = ref(false)
const converting = ref(false)
const deleting = ref(false)

const form = reactive({
  name: '',
  kind: 'PERSONAL' as 'PERSONAL' | 'SHARED',
  color: '#2563eb',
  description: '',
  sortOrder: 0,
})

watch(
  () => [props.open, props.space] as const,
  ([open, sp]) => {
    if (!open) return
    if (sp) {
      form.name = sp.name
      form.kind = sp.kind === 'SHARED' ? 'SHARED' : 'PERSONAL'
      form.color = sp.color || '#2563eb'
      form.description = sp.description || ''
      form.sortOrder = sp.sortOrder ?? 0
    } else {
      form.name = ''
      form.kind = 'PERSONAL'
      form.color = '#2563eb'
      form.description = ''
      form.sortOrder = 0
    }
  },
)

async function handleSave() {
  if (saving.value || converting.value || deleting.value) return
  const trimmed = form.name.trim()
  if (!trimmed) {
    toast.error('空间名称不能为空')
    return
  }
  saving.value = true
  try {
    if (props.space) {
      await mobileDriveApi.updateSpace(props.space.id, {
        name: trimmed,
        color: form.color,
        description: form.description.trim(),
        sortOrder: Number(form.sortOrder) || 0,
        icon: props.space.icon || 'Folder',
      })
      toast.success('空间设置已保存')
      emit('update:open', false)
      emit('saved', props.space.id)
    } else {
      const id = await mobileDriveApi.createSpace({
        name: trimmed,
        kind: form.kind,
        color: form.color,
        description: form.description.trim(),
        sortOrder: Number(form.sortOrder) || 0,
      })
      toast.success('空间已创建')
      emit('update:open', false)
      emit('saved', id)
    }
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '保存空间失败'))
  } finally {
    saving.value = false
  }
}

async function handleToggleShared() {
  if (!props.space || converting.value || saving.value || deleting.value) return
  const toShared = props.space.kind !== 'SHARED'
  try {
    await showConfirmDialog({
      title: toShared ? '转为共享空间' : '转为个人空间',
      message: toShared
        ? '转为共享空间后可邀请成员协作，原有文件将完整保留。'
        : '转为个人空间后将不再作为共享空间展示。',
    })
  } catch {
    return
  }

  converting.value = true
  try {
    if (toShared) {
      await mobileDriveApi.convertToShared(props.space.id)
      toast.success('已转为共享空间')
    } else {
      await mobileDriveApi.convertToPersonal(props.space.id, false)
      toast.success('已转为个人空间')
    }
    emit('update:open', false)
    emit('saved', props.space.id)
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '切换空间类型失败'))
  } finally {
    converting.value = false
  }
}

async function handleDelete() {
  if (!props.space || !canDeleteSpace(props.space) || deleting.value || saving.value || converting.value) return
  const targetSpace = props.space
  deleting.value = true
  try {
    try {
      await showConfirmDialog({
        title: '删除空间',
        message: `确定删除空间「${targetSpace.name}」吗？删除后该空间将无法访问。`,
        confirmButtonText: '确认删除',
        confirmButtonColor: 'var(--destructive)',
      })
    } catch {
      return
    }
    if (!props.open || props.space?.id !== targetSpace.id) return
    await mobileDriveApi.deleteSpace(targetSpace.id, targetSpace.name)
    toast.success('空间已删除')
    emit('update:open', false)
    emit('deleted', targetSpace.id)
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '删除空间失败'))
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <BottomSheet
    :open="open"
    :title="isEdit ? '空间设置' : '新建空间'"
    @update:open="emit('update:open', $event)"
  >
    <div class="space-y-4 pb-2">
      <div class="space-y-1.5">
        <label class="block text-xs font-medium text-foreground">空间名称</label>
        <input
          v-model="form.name"
          type="text"
          maxlength="64"
          placeholder="例如：工作资料、项目协作"
          class="w-full h-11 rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary"
        />
      </div>

      <div v-if="!isEdit" class="space-y-1.5">
        <label class="block text-xs font-medium text-foreground">空间类型</label>
        <div class="grid grid-cols-2 gap-2">
          <button
            v-for="opt in [
              { value: 'PERSONAL', label: '个人空间' },
              { value: 'SHARED', label: '共享空间' },
            ]"
            :key="opt.value"
            type="button"
            class="h-10 rounded-xl border text-xs font-medium transition-colors"
            :class="
              form.kind === opt.value
                ? 'border-primary bg-primary/10 text-primary'
                : 'border-border bg-background text-muted-foreground'
            "
            @click="form.kind = opt.value as 'PERSONAL' | 'SHARED'"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>

      <div class="space-y-1.5">
        <label class="block text-xs font-medium text-foreground">标识颜色</label>
        <div class="flex items-center gap-2.5 flex-wrap">
          <button
            v-for="color in COLOR_PRESETS"
            :key="color"
            type="button"
            class="h-8 w-8 rounded-full transition-transform"
            :class="form.color === color ? 'scale-110 ring-2 ring-offset-2 ring-primary ring-offset-card' : ''"
            :style="{ backgroundColor: color }"
            :aria-label="`选择颜色 ${color}`"
            @click="form.color = color"
          />
        </div>
      </div>

      <div class="space-y-1.5">
        <label class="block text-xs font-medium text-foreground">空间描述（可选）</label>
        <input
          v-model="form.description"
          type="text"
          maxlength="200"
          placeholder="简短说明用途"
          class="w-full h-11 rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary"
        />
      </div>

      <div class="space-y-1.5">
        <label class="block text-xs font-medium text-foreground">排序权重（越小越靠前）</label>
        <input
          v-model.number="form.sortOrder"
          type="number"
          class="w-full h-11 rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary"
        />
      </div>

      <VanButton
        type="primary"
        block
        round
        :loading="saving"
        :disabled="converting || deleting"
        class="!h-11 !text-sm !font-medium"
        @click="handleSave"
      >
        {{ isEdit ? '保存设置' : '创建空间' }}
      </VanButton>

      <!-- 编辑态高级操作（仅 OWNER） -->
      <template v-if="isEdit && canDeleteSpace(space)">
        <div class="flex items-center justify-between gap-3 pt-2 border-t border-border">
          <VanButton
            plain
            type="primary"
            size="small"
            :loading="converting"
            :disabled="saving || deleting"
            class="!h-9 !border-0 !bg-transparent !px-2 !text-xs"
            @click="handleToggleShared"
          >
            <span class="inline-flex items-center gap-1.5">
              <Users :size="14" />
              <span>{{ space?.kind === 'SHARED' ? '转为个人空间' : '转为共享空间' }}</span>
            </span>
          </VanButton>

          <VanButton
            plain
            size="small"
            :loading="deleting"
            :disabled="saving || converting"
            class="!h-9 !border-0 !bg-transparent !px-2 !text-xs !text-muted-foreground active:!text-destructive"
            @click="handleDelete"
          >
            <span class="inline-flex items-center gap-1.5">
              <Trash2 :size="13" />
              <span>删除空间</span>
            </span>
          </VanButton>
        </div>
      </template>
    </div>
  </BottomSheet>
</template>
