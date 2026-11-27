import { safeMobileReturnPath } from '@repo/session/navigation'

export interface GuardTarget {
  fullPath: string
  meta: {
    requiresAuth?: boolean
    guestOnly?: boolean
  }
  query?: Record<string, unknown>
}

export type GuardDecision =
  | true
  | { name: 'mobile-login'; query: { redirect: string } }
  | string

/**
 * Strip the `/m` base prefix so vue-router (already mounted at base `/m/`) navigates cleanly.
 */
export function stripMobileBase(fullPath: string): string {
  if (fullPath === '/m' || fullPath === '/m/') return '/home'
  if (fullPath.startsWith('/m/')) return fullPath.slice(2)
  return fullPath.startsWith('/') ? fullPath : '/home'
}

export function resolveMobileRouteGuard(to: GuardTarget, hasToken: boolean): GuardDecision {
  if (to.meta.requiresAuth && !hasToken) {
    const prefixed = to.fullPath.startsWith('/m') ? to.fullPath : `/m${to.fullPath.startsWith('/') ? to.fullPath : `/${to.fullPath}`}`
    return {
      name: 'mobile-login',
      query: { redirect: safeMobileReturnPath(prefixed) },
    }
  }
  if (to.meta.guestOnly && hasToken) {
    const rawRedirect = typeof to.query?.redirect === 'string' ? to.query.redirect : '/m/home'
    return stripMobileBase(safeMobileReturnPath(rawRedirect))
  }
  return true
}
