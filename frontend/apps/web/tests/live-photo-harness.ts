import { createApp, h, ref } from 'vue'
import LivePhotoOverlay from '@repo/ui/LivePhotoOverlay.vue'

// Deterministic browser fixture: exercise the real shared component without a login or database.
const identity = ref('photo-1')
let mode = 'ready'
let uploaded = false
let addressCalls = 0
let saveCalls = 0
let exportCalls = 0
let saveFailed = false
let pendingAddress: ((value: string) => void) | undefined
Object.assign(window, {
  liveQA: {
    setMode(value: string) { mode = value; identity.value = `photo-${Date.now()}` },
    changePhoto() { identity.value = 'photo-next' },
    resolveAddress() { pendingAddress?.('data:video/mp4;base64,AAAA') },
    get addressCalls() { return addressCalls },
    get uploaded() { return uploaded },
    get saveCalls() { return saveCalls },
    get exportCalls() { return exportCalls },
    failSaving() { saveFailed = true },
  },
})

createApp({
  setup() {
    return () => h('div', { style: 'position:relative;width:600px;height:500px;background:#222' }, [
      h(LivePhotoOverlay, {
        identity: identity.value,
        inspect: async () => ({ livePhoto: mode !== 'none', status: mode === 'pending' ? 'PROCESSING' : 'READY' }),
        address: async () => {
          addressCalls++
          if (mode === 'deferred') return new Promise<string>(resolve => { pendingAddress = resolve })
          throw new Error('Fixture network failure')
        },
        uploadVideo: async () => { uploaded = true; mode = 'pending'; return { livePhoto: true, status: 'PROCESSING' } },
        saveNative: async () => { saveCalls++; if (saveFailed) throw new Error('相册权限被拒绝') },
        exportMotion: async () => { exportCalls++ },
      }),
    ])
  },
}).mount('#app')
