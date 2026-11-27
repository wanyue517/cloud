export const CATEGORY_LABELS: Readonly<Record<string, string>> = Object.freeze({
  IMAGE: '图片',
  PDF: 'PDF',
  VIDEO: '视频',
  AUDIO: '音频',
  WORD: 'Word',
  EXCEL: '表格',
  PPT: '演示',
  CODE: '代码/文本',
  ARCHIVE: '压缩包',
  OTHER: '其他',
})

export function categoryLabel(category: string): string {
  return CATEGORY_LABELS[category] ?? category
}

export type SpaceRole = 'OWNER' | 'MANAGER' | 'EDITOR' | 'VIEWER'

export const SPACE_ROLE_LABELS: Readonly<Record<SpaceRole, string>> = Object.freeze({
  OWNER: '所有者',
  MANAGER: '管理员',
  EDITOR: '可编辑',
  VIEWER: '仅查看',
})

export function roleLabel(role: string): string {
  return SPACE_ROLE_LABELS[role as SpaceRole] ?? role
}

export function canWriteInRole(role: string | null | undefined): boolean {
  return role === 'OWNER' || role === 'MANAGER' || role === 'EDITOR'
}

export function canManageMembersInRole(role: string | null | undefined): boolean {
  return role === 'OWNER' || role === 'MANAGER'
}
