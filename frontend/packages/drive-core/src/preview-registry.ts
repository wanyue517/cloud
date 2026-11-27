export type PreviewKind =
  | 'image'
  | 'pdf'
  | 'word'
  | 'ppt'
  | 'excel'
  | 'markdown'
  | 'code'
  | 'text'
  | 'audio'
  | 'video'
  | 'archive'
  | 'unsupported'

export interface TocHeading {
  id: string
  level: number
  text: string
}

const IMAGE_EXTS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'ico', 'avif', 'svg'])
const PDF_EXTS = new Set(['pdf'])
const WORD_EXTS = new Set(['doc', 'docx', 'odt', 'rtf'])
const PPT_EXTS = new Set(['ppt', 'pptx', 'odp'])
const EXCEL_EXTS = new Set(['xls', 'xlsx', 'ods', 'csv'])
const ARCHIVE_EXTS = new Set(['zip', 'tar', 'tgz', 'gz'])
const MARKDOWN_EXTS = new Set(['md', 'markdown', 'mdx'])
const AUDIO_EXTS = new Set(['mp3', 'wav', 'ogg', 'flac', 'aac', 'm4a', 'opus'])
const VIDEO_EXTS = new Set(['mp4', 'webm', 'mov', 'm4v', 'ogv', 'mkv', 'avi'])
const PLAIN_TEXT_EXTS = new Set(['txt', 'log', 'tsv', 'env', 'gitignore', 'editorconfig'])

const CODE_LANG_BY_EXT: Record<string, string> = {
  js: 'javascript',
  mjs: 'javascript',
  cjs: 'javascript',
  jsx: 'javascript',
  ts: 'typescript',
  mts: 'typescript',
  cts: 'typescript',
  tsx: 'typescript',
  vue: 'xml',
  html: 'xml',
  htm: 'xml',
  xml: 'xml',
  svg: 'xml',
  css: 'css',
  scss: 'scss',
  less: 'less',
  json: 'json',
  java: 'java',
  kt: 'kotlin',
  py: 'python',
  go: 'go',
  rs: 'rust',
  c: 'c',
  h: 'c',
  cpp: 'cpp',
  hpp: 'cpp',
  cs: 'csharp',
  php: 'php',
  rb: 'ruby',
  swift: 'swift',
  sql: 'sql',
  sh: 'bash',
  bash: 'bash',
  zsh: 'bash',
  ps1: 'powershell',
  yaml: 'yaml',
  yml: 'yaml',
  toml: 'ini',
  ini: 'ini',
  conf: 'ini',
  properties: 'properties',
  dockerfile: 'dockerfile',
  makefile: 'makefile',
}

export function getExtension(name: string): string {
  const base = name.trim().toLowerCase()
  const dot = base.lastIndexOf('.')
  if (dot <= 0 || dot === base.length - 1) return base
  return base.slice(dot + 1)
}

export function detectCodeLanguage(name: string): string {
  const lower = name.trim().toLowerCase()
  if (lower === 'dockerfile') return 'dockerfile'
  if (lower === 'makefile') return 'makefile'
  const ext = getExtension(lower)
  return CODE_LANG_BY_EXT[ext] ?? 'plaintext'
}

export function resolvePreviewKind(name: string, mimeType = ''): PreviewKind {
  const ext = getExtension(name)
  const mime = mimeType.toLowerCase().split(';')[0]?.trim() ?? ''

  if (IMAGE_EXTS.has(ext) || (mime.startsWith('image/') && ext !== 'svg')) {
    return 'image'
  }
  if (PDF_EXTS.has(ext) || mime === 'application/pdf') {
    return 'pdf'
  }
  if (WORD_EXTS.has(ext)) {
    return 'word'
  }
  if (PPT_EXTS.has(ext)) {
    return 'ppt'
  }
  if (EXCEL_EXTS.has(ext)) {
    return 'excel'
  }
  if (ARCHIVE_EXTS.has(ext) || name.toLowerCase().endsWith('.tar.gz')) {
    return 'archive'
  }
  if (MARKDOWN_EXTS.has(ext) || mime === 'text/markdown') {
    return 'markdown'
  }
  if (AUDIO_EXTS.has(ext) || mime.startsWith('audio/')) {
    return 'audio'
  }
  if (VIDEO_EXTS.has(ext) || mime.startsWith('video/')) {
    return 'video'
  }
  if (CODE_LANG_BY_EXT[ext] || mime === 'application/json' || mime === 'application/xml' || mime === 'application/javascript') {
    return 'code'
  }
  if (PLAIN_TEXT_EXTS.has(ext) || mime.startsWith('text/')) {
    return 'text'
  }
  return 'unsupported'
}

export function previewKindLabel(kind: PreviewKind): string {
  switch (kind) {
    case 'image': return '图片预览'
    case 'pdf': return 'PDF 文档'
    case 'word': return 'Word 文档'
    case 'ppt': return '演示文稿'
    case 'excel': return '电子表格'
    case 'markdown': return 'Markdown 文档'
    case 'code': return '代码文件'
    case 'text': return '纯文本'
    case 'audio': return '音频'
    case 'video': return '视频'
    case 'archive': return '压缩归档'
    default: return '暂不支持预览'
  }
}

export function slugifyHeading(text: string, index: number): string {
  const clean = text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
  return clean ? `heading-${clean}-${index}` : `heading-${index}`
}

export function extractMarkdownToc(source: string): TocHeading[] {
  const headings: TocHeading[] = []
  let inFence = false
  const lines = source.split(/\r?\n/)
  let idx = 0
  for (const line of lines) {
    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence
      continue
    }
    if (inFence) continue
    const match = /^\s*(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line)
    if (!match) continue
    const level = match[1]!.length
    const text = match[2]!.replace(/[*_`~[\]]/g, '').trim()
    if (!text) continue
    idx += 1
    headings.push({ id: slugifyHeading(text, idx), level, text })
  }
  return headings
}
