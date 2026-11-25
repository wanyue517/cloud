// Keep this allowlist aligned with implemented routes, never accept origins or encoded paths.
export function safeReturnPath(value: unknown): string {
  if (typeof value !== 'string' || /[\\\\%\x00-\x20\x7f]/.test(value)) return '/app'
  const path = value.split(/[?#]/, 1)[0]!
  if (/^\/admin(?:\/users)?\/?$/.test(path) || /^\/app(?:\/spaces\/\d+(?:\/folders\/\d+)?)?\/?$/.test(path)) return value
  return '/app'
}
export function loginUrl(returnTo: string): string {
  return '/auth/login?returnTo=' + encodeURIComponent(safeReturnPath(returnTo))
}
export function isAdminPath(path: string): boolean {
  const pathname = path.split(/[?#]/, 1)[0]!
  return pathname === '/admin' || pathname.startsWith('/admin/')
}
export async function adminAccess(
  fullPath: string,
  getToken: () => string | null,
  getIdentity: () => Promise<{ admin: boolean }>,
): Promise<'allow' | 'not-found' | string> {
  if (!getToken()) return loginUrl('/admin' + fullPath)
  return (await getIdentity()).admin ? 'allow' : 'not-found'
}
