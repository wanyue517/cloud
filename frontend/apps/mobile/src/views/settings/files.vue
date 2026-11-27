<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Button as VanButton, Stepper as VanStepper } from 'vant'
import { toast } from '@repo/ui/toast'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { accountApi, type SpaceSummary } from '@/api/account'
import { MobileShell } from '@/components/common'

const loading = ref(true)
const savingPrefs = ref(false)
const savingPolicy = ref(false)
const spaces = ref<SpaceSummary[]>([])

const prefs = reactive({
  fileView: 'list',
  fileSort: 'name',
  defaultSpaceId: '',
})

const maxVersions = ref(10)
const systemMaxVersions = ref(20)

const selectableSpaces = computed(() => spaces.value.filter(s => s.kind !== 'PRIVATE'))

onMounted(async () => {
  loading.value = true
  try {
    const [p, policy, sp] = await Promise.all([
      accountApi.getPreferences().catch(() => null),
      accountApi.getVersionPolicy().catch(() => null),
      accountApi.listSpaces().catch(() => [] as SpaceSummary[]),
    ])
    spaces.value = sp
    if (p) {
      prefs.fileView = p.fileView || 'list'
      prefs.fileSort = p.fileSort || 'name'
      prefs.defaultSpaceId = p.defaultSpaceId || ''
    }
    if (policy) {
      maxVersions.value = policy.maxVersions
      systemMaxVersions.value = Math.max(1, policy.systemMaxVersions)
    }
  } finally {
    loading.value = false
  }
})

async function savePreferences() {
  savingPrefs.value = true
  try {
    const updated = await accountApi.updatePreferences({
      fileView: prefs.fileView,
      fileSort: prefs.fileSort,
      defaultSpaceId: prefs.defaultSpaceId,
    })
    prefs.fileView = updated.fileView || prefs.fileView
    prefs.fileSort = updated.fileSort || prefs.fileSort
    prefs.defaultSpaceId = updated.defaultSpaceId || prefs.defaultSpaceId
    try {
      localStorage.setItem('cloudlane.view', prefs.fileView)
      localStorage.setItem('cloudlane.sort', prefs.fileSort)
    } catch { /* ignore */ }
    toast.success('文件偏好已保存')
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '保存文件偏好失败'))
  } finally {
    savingPrefs.value = false
  }
}

async function saveVersionPolicy() {
  savingPolicy.value = true
  try {
    const clamped = Math.max(1, Math.min(systemMaxVersions.value, Math.round(maxVersions.value)))
    const res = await accountApi.updateVersionPolicy(clamped)
    maxVersions.value = res.maxVersions
    systemMaxVersions.value = res.systemMaxVersions
    toast.success('历史版本上限已保存')
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '保存版本策略失败'))
  } finally {
    savingPolicy.value = false
  }
}
</script>

<template>
  <MobileShell title="文件与版本偏好" fallback-path="/me" padded>
    <div class="space-y-5 pb-6">
      <!-- 浏览偏好 -->
      <section class="rounded-2xl border border-border bg-card p-4 space-y-4">
        <h2 class="text-sm font-semibold text-foreground">默认浏览方式</h2>

        <div class="space-y-1.5">
          <p class="text-xs text-muted-foreground">默认视图</p>
          <div class="grid grid-cols-2 gap-2">
            <button
              v-for="opt in [
                { value: 'list', label: '列表视图' },
                { value: 'grid', label: '网格视图' },
              ]"
              :key="opt.value"
              type="button"
              class="h-10 rounded-xl border text-xs font-medium transition-colors"
              :class="
                prefs.fileView === opt.value
                  ? 'border-primary bg-primary/10 text-primary'
                  : 'border-border bg-background text-muted-foreground'
              "
              @click="prefs.fileView = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <div class="space-y-1.5">
          <p class="text-xs text-muted-foreground">默认排序</p>
          <div class="grid grid-cols-3 gap-2">
            <button
              v-for="opt in [
                { value: 'name', label: '名称' },
                { value: 'updatedAt', label: '修改时间' },
                { value: 'size', label: '文件大小' },
              ]"
              :key="opt.value"
              type="button"
              class="h-10 rounded-xl border text-xs font-medium transition-colors"
              :class="
                prefs.fileSort === opt.value
                  ? 'border-primary bg-primary/10 text-primary'
                  : 'border-border bg-background text-muted-foreground'
              "
              @click="prefs.fileSort = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <div class="space-y-1.5">
          <label for="default-space" class="block text-xs text-muted-foreground">默认进入空间</label>
          <select
            id="default-space"
            v-model="prefs.defaultSpaceId"
            class="w-full h-11 rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary"
          >
            <option value="">自动选择首个空间</option>
            <option v-for="sp in selectableSpaces" :key="sp.id" :value="sp.id">
              {{ sp.name }}{{ sp.kind === 'SHARED' ? '（共享）' : '' }}
            </option>
          </select>
        </div>

        <VanButton
          type="primary"
          block
          round
          :loading="savingPrefs || loading"
          class="!h-10 !text-sm"
          @click="savePreferences"
        >
          保存浏览偏好
        </VanButton>
      </section>

      <!-- 历史版本策略 -->
      <section class="rounded-2xl border border-border bg-card p-4 space-y-4">
        <div>
          <h2 class="text-sm font-semibold text-foreground">历史版本保留上限</h2>
          <p class="mt-1 text-xs text-muted-foreground">
            替换文件时自动保留旧版本，超出上限后自动裁剪最旧版本（系统允许上限：{{ systemMaxVersions }} 个）。
          </p>
        </div>

        <div class="flex items-center justify-between">
          <span class="text-sm text-foreground">单文件保留版本数</span>
          <VanStepper v-model="maxVersions" :min="1" :max="systemMaxVersions" integer />
        </div>

        <VanButton
          type="primary"
          plain
          block
          round
          :loading="savingPolicy || loading"
          class="!h-10 !text-sm"
          @click="saveVersionPolicy"
        >
          保存版本策略
        </VanButton>
      </section>
    </div>
  </MobileShell>
</template>
