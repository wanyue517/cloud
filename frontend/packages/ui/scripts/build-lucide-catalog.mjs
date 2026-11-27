import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const uiRoot = path.resolve(__dirname, '..')
const lucideEntry = path.join(uiRoot, 'node_modules', '@lucide', 'vue', 'dist', 'esm', 'lucide-vue.mjs')
const dest = path.join(uiRoot, 'src', 'components', 'icon-picker', 'lucide-catalog.json')

const mod = await import(pathToFileURL(lucideEntry).href)
const out = {}
let count = 0

function toKebab(pascal) {
  return pascal
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1-$2')
    .replace(/([a-zA-Z])([0-9])/g, '$1-$2')
    .toLowerCase()
}

for (const [pascal, fn] of Object.entries(mod.icons || {})) {
  if (typeof fn !== 'function') continue
  try {
    const vnode = fn({}, { slots: {} })
    const icon = vnode?.props?.icon
    const nodes = Array.isArray(icon) ? icon : icon?.node
    if (!Array.isArray(nodes)) continue
    const kebab = (typeof icon?.name === 'string' && icon.name.trim()) ? icon.name.trim().toLowerCase() : toKebab(pascal)
    if (out[kebab]) continue
    out[kebab] = nodes.map(([tag, attrs]) => {
      const clean = {}
      for (const [k, val] of Object.entries(attrs || {})) {
        if (k !== 'key') clean[k] = val
      }
      return [tag, clean]
    })
    count++
  } catch {
    // skip non-standard export
  }
}

if (count === 0) {
  console.error('Failed to extract Lucide icons!')
  process.exit(1)
}

fs.writeFileSync(dest, JSON.stringify(out))
console.log(`Generated ${count} Lucide icons -> ${dest} (${Math.round(fs.statSync(dest).size / 1024)} KB)`)
