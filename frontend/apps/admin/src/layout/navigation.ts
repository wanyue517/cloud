export interface AdminNavItem {
  name: string
  label: string
  to: string
  group: '概览' | '运营与用户' | '存储与策略' | '运维监控'
  phase: 'P0' | 'P1'
  taskId: string
  description: string
}

export const ADMIN_NAV_ITEMS: readonly AdminNavItem[] = [
  {
    name: 'admin-dashboard',
    label: '仪表盘',
    to: '/',
    group: '概览',
    phase: 'P0',
    taskId: 'T153',
    description: '汇总系统用户、空间、文件数量与物理存储占用概览。',
  },
  {
    name: 'admin-users',
    label: '用户管理',
    to: '/users',
    group: '运营与用户',
    phase: 'P1',
    taskId: 'T154',
    description: '查看用户列表、账号状态，并支持启用或禁用账号。',
  },
  {
    name: 'admin-messages',
    label: '系统消息',
    to: '/messages',
    group: '运营与用户',
    phase: 'P1',
    taskId: 'T158',
    description: '发布全员或定向系统通知，管理消息下发记录。',
  },
  {
    name: 'admin-storage',
    label: '存储配置',
    to: '/storage',
    group: '存储与策略',
    phase: 'P1',
    taskId: 'T159',
    description: '管理本地存储、S3 兼容存储实例、连通性测试及默认写入实例切换。',
  },
  {
    name: 'admin-settings-site',
    label: '网站基础设置',
    to: '/settings/site',
    group: '存储与策略',
    phase: 'P1',
    taskId: 'T164',
    description: '配置站点名称、品牌标识及用户端基础展示信息。',
  },
  {
    name: 'admin-settings-auth',
    label: '登录与注册',
    to: '/settings/auth',
    group: '存储与策略',
    phase: 'P1',
    taskId: 'T155',
    description: '配置开放注册策略、登录方式、SMTP 邮件与第三方身份提供商。',
  },
  {
    name: 'admin-settings-files',
    label: '文件与上传策略',
    to: '/settings/files',
    group: '存储与策略',
    phase: 'P1',
    taskId: 'T163',
    description: '配置单文件上限、历史版本保留上限与上传清理策略。',
  },
  {
    name: 'admin-tasks',
    label: '后台任务',
    to: '/tasks',
    group: '运维监控',
    phase: 'P1',
    taskId: 'T166',
    description: '查看文档转换、索引构建、打包下载等异步任务进度与失败重试。',
  },
  {
    name: 'admin-logs',
    label: '管理审计',
    to: '/logs',
    group: '运维监控',
    phase: 'P1',
    taskId: 'T173',
    description: '检索管理员关键操作记录、权限变更与异常请求链路。',
  },
] as const

export const ADMIN_NAV_GROUPS = ['概览', '运营与用户', '存储与策略', '运维监控'] as const
