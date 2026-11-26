import { defineAsyncComponent, type Component } from 'vue'
import type { PreviewKind } from '@/lib/preview-registry'

const asyncRegistry: Record<PreviewKind, Component> = {
  image: defineAsyncComponent(() => import('./ImagePreviewer.vue')),
  pdf: defineAsyncComponent(() => import('./PdfPreviewer.vue')),
  word: defineAsyncComponent(() => import('./OfficePreviewer.vue')),
  ppt: defineAsyncComponent(() => import('./OfficePreviewer.vue')),
  excel: defineAsyncComponent(() => import('./ExcelPreviewer.vue')),
  markdown: defineAsyncComponent(() => import('./MarkdownPreviewer.vue')),
  code: defineAsyncComponent(() => import('./TextCodePreviewer.vue')),
  text: defineAsyncComponent(() => import('./TextCodePreviewer.vue')),
  audio: defineAsyncComponent(() => import('./AudioPreviewer.vue')),
  video: defineAsyncComponent(() => import('./VideoPreviewer.vue')),
  archive: defineAsyncComponent(() => import('./ArchivePreviewer.vue')),
  unsupported: defineAsyncComponent(() => import('./UnsupportedPreviewer.vue')),
}

export function getPreviewComponent(kind: PreviewKind): Component {
  return asyncRegistry[kind] ?? asyncRegistry.unsupported
}
