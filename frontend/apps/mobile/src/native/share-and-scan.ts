export interface IncomingSharedFile {
  name: string
  mimeType: string
  uri?: string
  blob?: Blob
}

export interface ScannedPageImage {
  jpegBytes: Uint8Array
  width: number
  height: number
}

/**
 * 订阅系统级「分享到 Cloudlane」事件（Android ACTION_SEND / ACTION_SEND_MULTIPLE & iOS Share Extension）。
 */
export function subscribeIncomingNativeShare(
  callback: (files: IncomingSharedFile[]) => void,
): () => void {
  const win = globalThis as unknown as {
    Capacitor?: {
      Plugins?: {
        SendIntent?: {
          checkSendIntentReceived?: () => Promise<{ title?: string; url?: string; type?: string; additionalItems?: Array<{ title?: string; url?: string; type?: string }> }>
        }
      }
    }
  }

  let disposed = false
  const check = async () => {
    const plugin = win.Capacitor?.Plugins?.SendIntent
    if (!plugin?.checkSendIntentReceived || disposed) return
    try {
      const res = await plugin.checkSendIntentReceived()
      if (!res?.url && !res?.additionalItems?.length) return
      const items: IncomingSharedFile[] = []
      if (res.url) {
        items.push({
          name: res.title || `shared-${Date.now()}`,
          mimeType: res.type || 'application/octet-stream',
          uri: res.url,
        })
      }
      for (const extra of res.additionalItems ?? []) {
        if (extra.url) {
          items.push({
            name: extra.title || `shared-${Date.now()}`,
            mimeType: extra.type || 'application/octet-stream',
            uri: extra.url,
          })
        }
      }
      if (items.length > 0 && !disposed) {
        callback(items)
      }
    } catch {
      // no pending share intent
    }
  }

  void check()
  return () => {
    disposed = true
  }
}

/**
 * 调用系统其他应用打开文件（写入临时沙箱后唤起原生 FileOpener 插件）。
 */
export async function openBlobWithExternalApp(
  blob: Blob,
  filename: string,
  mimeType: string,
): Promise<{ ok: boolean; message?: string }> {
  const win = globalThis as unknown as {
    Capacitor?: {
      Plugins?: {
        FileOpener?: {
          open: (opts: { filePath: string; contentType?: string }) => Promise<void>
        }
      }
    }
  }
  const opener = win.Capacitor?.Plugins?.FileOpener
  if (!opener?.open) {
    return { ok: false, message: '当前环境未安装原生外部应用打开插件，请直接下载后查看' }
  }
  try {
    const { Filesystem, Directory } = await import('@capacitor/filesystem')
    const buf = await blob.arrayBuffer()
    const bytes = new Uint8Array(buf)
    let bin = ''
    for (let i = 0; i < bytes.length; i += 0x8000) {
      bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
    }
    const safeName = filename.replace(/[^a-zA-Z0-9._\-\u4e00-\u9fa5]/g, '_')
    const written = await Filesystem.writeFile({
      path: `cloudlane-open/${Date.now()}-${safeName}`,
      data: btoa(bin),
      directory: Directory.Cache,
      recursive: true,
    })
    await opener.open({ filePath: written.uri, contentType: mimeType || 'application/octet-stream' })
    return { ok: true }
  } catch (err) {
    return {
      ok: false,
      message: err instanceof Error ? err.message : '唤起外部应用失败',
    }
  }
}

/**
 * 纯前端多页 JPEG -> 标准 PDF-1.4 合成器（零外部重型依赖）：
 * 将扫描拍摄的多张纸质文档图片合成为可被 PDF.js 及系统阅读器直接打开的多页 PDF。
 */
export function buildPdfFromJpegPages(pages: ScannedPageImage[]): Uint8Array {
  if (pages.length === 0) {
    throw new Error('请至少添加一页扫描文档')
  }

  const encoder = new TextEncoder()
  const chunks: Uint8Array[] = []
  const offsets: number[] = []
  let byteOffset = 0

  const pushBytes = (arr: Uint8Array) => {
    chunks.push(arr)
    byteOffset += arr.length
  }
  const pushStr = (s: string) => pushBytes(encoder.encode(s))

  pushStr('%PDF-1.4\n%\xFF\xFF\xFF\xFF\n')

  // Object 1: Catalog, Object 2: Pages
  // Each page i uses 3 objects: Page (3 + 3*i), Image XObject (4 + 3*i), Content stream (5 + 3*i)
  const totalObjects = 2 + pages.length * 3
  const pageObjIds: number[] = []
  for (let i = 0; i < pages.length; i++) {
    pageObjIds.push(3 + i * 3)
  }

  // Obj 1: Catalog
  offsets.push(byteOffset)
  pushStr('1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n')

  // Obj 2: Pages
  offsets.push(byteOffset)
  pushStr(`2 0 obj\n<< /Type /Pages /Kids [${pageObjIds.map(id => `${id} 0 R`).join(' ')}] /Count ${pages.length} >>\nendobj\n`)

  for (let i = 0; i < pages.length; i++) {
    const p = pages[i]!
    const pageId = 3 + i * 3
    const imgId = 4 + i * 3
    const contentId = 5 + i * 3

    // Fit onto A4 width (595pt) preserving aspect ratio
    const pageW = 595
    const pageH = Math.max(200, Math.round((p.height / Math.max(1, p.width)) * pageW))

    // Page obj
    offsets.push(byteOffset)
    pushStr(
      `${pageId} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageW} ${pageH}] ` +
        `/Resources << /XObject << /Im0 ${imgId} 0 R >> >> /Contents ${contentId} 0 R >>\nendobj\n`,
    )

    // Image XObject (DCTDecode = JPEG)
    offsets.push(byteOffset)
    pushStr(
      `${imgId} 0 obj\n<< /Type /XObject /Subtype /Image /Width ${p.width} /Height ${p.height} ` +
        `/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpegBytes.length} >>\nstream\n`,
    )
    pushBytes(p.jpegBytes)
    pushStr('\nendstream\nendobj\n')

    // Content stream drawing image full page
    const drawCmd = `q\n${pageW} 0 0 ${pageH} 0 0 cm\n/Im0 Do\nQ\n`
    const drawBytes = encoder.encode(drawCmd)
    offsets.push(byteOffset)
    pushStr(`${contentId} 0 obj\n<< /Length ${drawBytes.length} >>\nstream\n`)
    pushBytes(drawBytes)
    pushStr('endstream\nendobj\n')
  }

  const xrefOffset = byteOffset
  pushStr(`xref\n0 ${totalObjects + 1}\n0000000000 65535 f \n`)
  for (const off of offsets) {
    pushStr(`${String(off).padStart(10, '0')} 00000 n \n`)
  }
  pushStr(`trailer\n<< /Size ${totalObjects + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`)

  const out = new Uint8Array(byteOffset)
  let cursor = 0
  for (const c of chunks) {
    out.set(c, cursor)
    cursor += c.length
  }
  return out
}
