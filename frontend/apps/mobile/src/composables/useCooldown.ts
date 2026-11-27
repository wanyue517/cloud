import { computed, onBeforeUnmount, ref } from 'vue'

export function createCooldownController(nowFn: () => number = () => Date.now()) {
  const secondsLeft = ref(0)
  let expiresAt = 0
  let timer: ReturnType<typeof setInterval> | null = null

  function clearTimer() {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  function sync() {
    const remaining = Math.ceil((expiresAt - nowFn()) / 1000)
    if (remaining <= 0) {
      secondsLeft.value = 0
      clearTimer()
    } else {
      secondsLeft.value = remaining
    }
  }

  function start(seconds = 60) {
    expiresAt = nowFn() + Math.max(1, seconds) * 1000
    sync()
    clearTimer()
    timer = setInterval(sync, 250)
  }

  function reset() {
    expiresAt = 0
    secondsLeft.value = 0
    clearTimer()
  }

  return {
    secondsLeft,
    isCoolingDown: computed(() => secondsLeft.value > 0),
    start,
    reset,
    sync,
    dispose: clearTimer,
  }
}

export function useCooldown(defaultSeconds = 60) {
  const ctrl = createCooldownController()
  onBeforeUnmount(() => ctrl.dispose())
  return {
    secondsLeft: ctrl.secondsLeft,
    isCoolingDown: ctrl.isCoolingDown,
    start: (seconds = defaultSeconds) => ctrl.start(seconds),
    reset: ctrl.reset,
  }
}
