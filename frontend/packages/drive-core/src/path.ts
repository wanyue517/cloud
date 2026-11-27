export interface BreadcrumbPartition<T> {
  collapsed: T[]
  tail: T[]
}

/**
 * 面包屑长路径折叠：超过 maxTail 层时，将中间祖先收入折叠下拉菜单，仅直出末尾若干级。
 */
export function partitionBreadcrumbs<T>(ancestors: readonly T[], maxTail = 2): BreadcrumbPartition<T> {
  if (ancestors.length <= maxTail + 1) {
    return { collapsed: [], tail: [...ancestors] }
  }
  return {
    collapsed: ancestors.slice(0, ancestors.length - maxTail),
    tail: ancestors.slice(ancestors.length - maxTail),
  }
}

/**
 * 重命名时选中主文件名范围（不含扩展名），文件夹或隐藏点文件全选。
 */
export function mainFilenameRange(name: string, isFolder: boolean): [number, number] {
  if (!name) return [0, 0]
  if (isFolder) return [0, name.length]
  const dot = name.lastIndexOf('.')
  if (dot <= 0) return [0, name.length]
  return [0, dot]
}
