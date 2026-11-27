import { computed, reactive } from 'vue'
import { mobileDriveApi, type Space } from '@/api/drive'
import { filterVisibleSpaces, resolveActiveSpace } from '@/lib/spaces'
import { normalizeErrorMessage } from '@repo/drive-core/errors'

export const spacesState = reactive<{
  all: Space[]
  loading: boolean
  loaded: boolean
  error: string
}>({
  all: [],
  loading: false,
  loaded: false,
  error: '',
})

export const visibleSpaces = computed(() => filterVisibleSpaces(spacesState.all))

let inflight: Promise<Space[]> | null = null

export async function fetchSpaces(force = false): Promise<Space[]> {
  if (spacesState.loaded && !force) return spacesState.all
  if (inflight && !force) return inflight

  spacesState.loading = true
  spacesState.error = ''
  inflight = mobileDriveApi
    .spaces()
    .then(list => {
      spacesState.all = list
      spacesState.loaded = true
      return list
    })
    .catch(err => {
      spacesState.error = normalizeErrorMessage(err, '加载空间列表失败')
      return spacesState.all
    })
    .finally(() => {
      spacesState.loading = false
      inflight = null
    })

  return inflight
}

export function getSpaceById(spaceId?: string): Space | null {
  return resolveActiveSpace(visibleSpaces.value, spaceId)
}
