import catalogUrl from './lucide-catalog.json?url'

export type LucideSvgNode = [tag: string, attrs: Record<string, string | number>]

export interface LucideIconOption {
  name: string
  label: string
  keywords: string
  nodes: LucideSvgNode[]
  featured?: boolean
}

/** 默认 folder 图标节点，保证首屏同步渲染零闪烁 */
export const DEFAULT_FOLDER_NODES: LucideSvgNode[] = [
  [
    'path',
    {
      d: 'M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z',
    },
  ],
]

/** 常用高频图标的中文名称与搜索关键词（置顶展示并支持中文检索） */
const FEATURED_META: Record<string, { label: string; keywords: string }> = {
  folder: { label: '文件夹', keywords: 'folder dir 目录 默认' },
  'folder-heart': { label: '珍藏目录', keywords: 'folder heart 喜欢 收藏 家庭' },
  'folder-kanban': { label: '项目看板', keywords: 'folder kanban 项目 协作' },
  'folder-code': { label: '代码目录', keywords: 'folder code 开发 代码' },
  'folder-git-2': { label: 'Git 仓库', keywords: 'folder git 版本 仓库' },
  'folder-archive': { label: '归档目录', keywords: 'folder archive 归档 压缩' },
  'folder-lock': { label: '加密目录', keywords: 'folder lock 私密 安全' },
  'folder-sync': { label: '同步目录', keywords: 'folder sync 同步 云端' },
  'folder-tree': { label: '层级目录', keywords: 'folder tree 结构' },
  'folder-check': { label: '已完成', keywords: 'folder check 归档 完成' },
  'folder-clock': { label: '历史记录', keywords: 'folder clock 时间 历史' },
  'folder-search': { label: '检索目录', keywords: 'folder search 搜索' },
  briefcase: { label: '工作资料', keywords: 'briefcase work 办公 职场' },
  'building-2': { label: '企业团队', keywords: 'building company 公司 部门' },
  users: { label: '团队协作', keywords: 'users team 共享 成员' },
  home: { label: '家庭空间', keywords: 'home family 个人 首页' },
  image: { label: '相册图片', keywords: 'image photo 图片 照片' },
  camera: { label: '摄影素材', keywords: 'camera photo 相机 拍摄' },
  video: { label: '视频影像', keywords: 'video movie 视频 影片' },
  film: { label: '影视剧集', keywords: 'film cinema 电影' },
  music: { label: '音乐音频', keywords: 'music audio 歌曲' },
  headphones: { label: '播客听书', keywords: 'headphones podcast 音频' },
  'file-text': { label: '文档资料', keywords: 'file text doc 文档' },
  files: { label: '综合文件', keywords: 'files docs 资料库' },
  'file-spreadsheet': { label: '数据表格', keywords: 'excel sheet 表格 财务' },
  'file-code-2': { label: '源码脚本', keywords: 'code script 程序' },
  'code-2': { label: '研发工程', keywords: 'code dev 开发' },
  terminal: { label: '运维脚本', keywords: 'terminal shell 命令' },
  database: { label: '数据库备份', keywords: 'database db 备份 数据' },
  server: { label: '服务器资源', keywords: 'server ops 运维' },
  'hard-drive': { label: '硬盘归档', keywords: 'hard drive disk 存储' },
  cloud: { label: '云端同步', keywords: 'cloud 网盘' },
  archive: { label: '冷备归档', keywords: 'archive backup 备份' },
  package: { label: '安装包', keywords: 'package app 软件' },
  box: { label: '资源盒', keywords: 'box 资产 素材' },
  layers: { label: '设计图层', keywords: 'layers design 设计' },
  palette: { label: '创意美术', keywords: 'palette art 设计 配色' },
  'book-open': { label: '电子书库', keywords: 'book read 阅读 学习' },
  'graduation-cap': { label: '学习课程', keywords: 'study course 教育 论文' },
  notebook: { label: '笔记手账', keywords: 'notebook note 笔记' },
  'clipboard-list': { label: '任务清单', keywords: 'clipboard todo 清单' },
  calendar: { label: '日程归档', keywords: 'calendar schedule 日程' },
  bookmark: { label: '书签资料', keywords: 'bookmark 收藏 摘录' },
  star: { label: '精选重要', keywords: 'star 重要 标星' },
  heart: { label: '生活回忆', keywords: 'heart 喜欢 生活' },
  sparkles: { label: '灵感素材', keywords: 'sparkles ai 灵感' },
  lightbulb: { label: '创意想法', keywords: 'lightbulb idea 想法' },
  rocket: { label: '项目发布', keywords: 'rocket launch 发布 上线' },
  globe: { label: '公开资源', keywords: 'globe web 网站 公开' },
  compass: { label: '旅行攻略', keywords: 'compass travel 探索' },
  plane: { label: '出差旅行', keywords: 'plane trip 旅行' },
  wallet: { label: '账单票据', keywords: 'wallet finance 发票 财务' },
  'shopping-bag': { label: '订单采购', keywords: 'shop order 电商' },
  'gamepad-2': { label: '游戏娱乐', keywords: 'game 游戏' },
  cpu: { label: '硬件固件', keywords: 'cpu hardware 芯片 固件' },
  wrench: { label: '工具软件', keywords: 'wrench tool 工具' },
  shield: { label: '合规安全', keywords: 'shield security 安全' },
  lock: { label: '私密保险', keywords: 'lock private 隐私' },
  'key-round': { label: '密钥证书', keywords: 'key cert 证书 密钥' },
  award: { label: '荣誉证书', keywords: 'award 证书 奖项' },
  'layout-grid': { label: '综合空间', keywords: 'grid layout 综合' },
  tag: { label: '分类标签', keywords: 'tag 分类' },
}

export function normalizeIconName(raw?: string | null): string {
  const s = (raw || '').trim()
  if (!s) return 'folder'
  if (s.includes('-')) return s.toLowerCase()
  return s
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1-$2')
    .replace(/([a-zA-Z])([0-9])/g, '$1-$2')
    .toLowerCase()
}

let cachedAllOptions: LucideIconOption[] | null = null
let cachedIconMap: Map<string, LucideIconOption> | null = null
let loadingPromise: Promise<LucideIconOption[]> | null = null

/** 异步加载轻量级全量 Lucide 图标目录（2098 个），按需 fetch 静态 JSON 且单例缓存 */
export async function loadAllLucideIcons(): Promise<LucideIconOption[]> {
  if (cachedAllOptions) return cachedAllOptions
  if (!loadingPromise) {
    loadingPromise = fetch(catalogUrl)
      .then(res => {
        if (!res.ok) throw new Error(`Failed to load Lucide catalog (${res.status})`)
        return res.json() as Promise<Record<string, LucideSvgNode[]>>
      })
      .then(catalog => {
        const featuredList: LucideIconOption[] = []
        const otherList: LucideIconOption[] = []
        const map = new Map<string, LucideIconOption>()

        for (const [name, nodes] of Object.entries(catalog)) {
          if (!Array.isArray(nodes)) continue
          const meta = FEATURED_META[name]
          const option: LucideIconOption = {
            name,
            label: meta ? meta.label : name,
            keywords: meta ? `${meta.keywords} ${name} ${name.replace(/-/g, ' ')}` : `${name} ${name.replace(/-/g, ' ')}`,
            nodes,
            featured: Boolean(meta),
          }
          map.set(name, option)
          if (meta) featuredList.push(option)
          else otherList.push(option)
        }

        const featuredOrder = Object.keys(FEATURED_META)
        featuredList.sort((a, b) => featuredOrder.indexOf(a.name) - featuredOrder.indexOf(b.name))
        otherList.sort((a, b) => a.name.localeCompare(b.name))

        cachedIconMap = map
        cachedAllOptions = [...featuredList, ...otherList]
        return cachedAllOptions
      })
      .catch(err => {
        loadingPromise = null
        throw err
      })
  }
  return loadingPromise
}

export function getCachedLucideIcons(): LucideIconOption[] | null {
  return cachedAllOptions
}

export function resolveLucideNodesSync(name?: string | null): LucideSvgNode[] | null {
  const key = normalizeIconName(name)
  if (key === 'folder') return cachedIconMap?.get('folder')?.nodes ?? DEFAULT_FOLDER_NODES
  return cachedIconMap?.get(key)?.nodes ?? null
}

export function findLucideIconLabelSync(name?: string | null): string {
  const key = normalizeIconName(name)
  return cachedIconMap?.get(key)?.label ?? FEATURED_META[key]?.label ?? key
}
