import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import json from 'highlight.js/lib/languages/json'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import yaml from 'highlight.js/lib/languages/yaml'
import katex from 'katex'
import { extractMarkdownToc, slugifyHeading, type TocHeading } from '@repo/drive-core/preview-registry'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('vue', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('json', json)
hljs.registerLanguage('java', java)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('sh', bash)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)

export function highlightCodeSnippet(code: string, lang: string): string {
  const safeLang = (lang || '').trim().toLowerCase()
  if (safeLang && hljs.getLanguage(safeLang)) {
    try {
      return hljs.highlight(code, { language: safeLang, ignoreIllegals: true }).value
    } catch {
      // fall through to escaped plaintext
    }
  }
  return escapeHtml(code)
}

export function escapeHtml(raw: string): string {
  return raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderMathExpressions(markdown: string): string {
  // Replace display math $$...$$ and inline $...$ outside fenced code blocks
  const parts = markdown.split(/(```[\s\S]*?```|~~~[\s\S]*?~~~)/g)
  return parts
    .map((part, idx) => {
      if (idx % 2 === 1) return part
      const withDisplay = part.replace(/\$\$([\s\S]+?)\$\$/g, (_, expr: string) => {
        try {
          return `<div class="katex-display-block overflow-x-auto my-3">${katex.renderToString(expr.trim(), {
            displayMode: true,
            throwOnError: false,
          })}</div>`
        } catch {
          return `<pre>${escapeHtml(expr)}</pre>`
        }
      })
      return withDisplay.replace(/(^|[^$\\])\$([^$\n]+?)\$/g, (full, prefix: string, expr: string) => {
        try {
          return `${prefix}${katex.renderToString(expr.trim(), { displayMode: false, throwOnError: false })}`
        } catch {
          return full
        }
      })
    })
    .join('')
}

export function renderSafeMarkdown(source: string): { html: string; toc: TocHeading[] } {
  const toc = extractMarkdownToc(source)
  let headingCounter = 0

  const md = new MarkdownIt({
    html: false,
    linkify: true,
    typographer: true,
    highlight(str: string, lang: string) {
      if (lang.trim().toLowerCase() === 'mermaid') {
        return `<div class="mermaid-block" data-mermaid="${encodeURIComponent(str)}"><pre class="text-xs overflow-x-auto">${escapeHtml(str)}</pre></div>`
      }
      const highlighted = highlightCodeSnippet(str, lang)
      return `<pre class="hljs rounded-xl p-3 overflow-x-auto text-xs leading-relaxed"><code>${highlighted}</code></pre>`
    },
  })

  md.renderer.rules.heading_open = (tokens, idx, options, _env, self) => {
    headingCounter += 1
    const inline = tokens[idx + 1]
    const text = inline ? inline.content : ''
    const id = slugifyHeading(text, headingCounter)
    tokens[idx]!.attrSet('id', id)
    return self.renderToken(tokens, idx, options)
  }

  const withMath = renderMathExpressions(source)
  const rawHtml = md.render(withMath)
  const html = DOMPurify.sanitize(rawHtml, {
    ADD_ATTR: ['data-mermaid', 'target', 'rel', 'aria-hidden'],
    FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'form'],
  })

  return { html, toc }
}
