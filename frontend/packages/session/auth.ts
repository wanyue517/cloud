export interface TokenStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

const ACCESS_TOKEN_KEY = 'cloudlane.accessToken'
const REFRESH_TOKEN_KEY = 'cloudlane.refreshToken'

let customStorage: TokenStorage | null = null

function resolveStorage(): TokenStorage | null {
  if (customStorage) return customStorage
  try {
    return typeof globalThis.sessionStorage !== 'undefined' ? globalThis.sessionStorage : null
  } catch {
    return null
  }
}

export function configureTokenStorage(storage: TokenStorage | null | undefined) {
  customStorage = storage ?? null
}

export function resetTokenStorage() {
  customStorage = null
}

export function getAccessToken(): string | null {
  try {
    return resolveStorage()?.getItem(ACCESS_TOKEN_KEY) ?? null
  } catch {
    return null
  }
}

export function saveAccessToken(token: string) {
  try {
    resolveStorage()?.setItem(ACCESS_TOKEN_KEY, token)
  } catch {
    // Ignore storage quota/security errors
  }
}

export function clearAccessToken() {
  try {
    resolveStorage()?.removeItem(ACCESS_TOKEN_KEY)
  } catch {
    // Ignore storage errors
  }
}

export function getRefreshToken(): string | null {
  try {
    return resolveStorage()?.getItem(REFRESH_TOKEN_KEY) ?? null
  } catch {
    return null
  }
}

export function saveRefreshToken(token: string) {
  try {
    resolveStorage()?.setItem(REFRESH_TOKEN_KEY, token)
  } catch {
    // Ignore storage quota/security errors
  }
}

export function clearRefreshToken() {
  try {
    resolveStorage()?.removeItem(REFRESH_TOKEN_KEY)
  } catch {
    // Ignore storage errors
  }
}
