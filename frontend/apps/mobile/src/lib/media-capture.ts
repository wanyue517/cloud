export type CaptureKind = 'photo' | 'video' | 'audio'

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

export function formatMediaTimestamp(date = new Date()): string {
  const y = date.getFullYear()
  const m = pad2(date.getMonth() + 1)
  const d = pad2(date.getDate())
  const hh = pad2(date.getHours())
  const mm = pad2(date.getMinutes())
  const ss = pad2(date.getSeconds())
  return `${y}${m}${d}_${hh}${mm}${ss}`
}

function extFromMimeOrName(kind: CaptureKind, mimeType?: string, originalName?: string): string {
  const mime = (mimeType || '').toLowerCase()
  if (kind === 'photo') {
    if (mime.includes('png')) return 'png'
    if (mime.includes('webp')) return 'webp'
    if (mime.includes('heic')) return 'heic'
    if (mime.includes('jpeg') || mime.includes('jpg')) return 'jpg'
  } else if (kind === 'video') {
    if (mime.includes('webm')) return 'webm'
    if (mime.includes('quicktime')) return 'mov'
    if (mime.includes('mp4')) return 'mp4'
  } else {
    if (mime.includes('mp4') || mime.includes('aac') || mime.includes('m4a')) return 'm4a'
    if (mime.includes('ogg')) return 'ogg'
    if (mime.includes('wav')) return 'wav'
    if (mime.includes('webm')) return 'webm'
  }

  if (originalName && originalName.includes('.')) {
    const ext = originalName.split('.').pop()?.trim().toLowerCase()
    if (ext && /^[a-z0-9]{2,5}$/.test(ext)) return ext
  }

  if (kind === 'photo') return 'jpg'
  if (kind === 'video') return 'mp4'
  return 'webm'
}

export function buildCaptureFilename(
  kind: CaptureKind,
  mimeType?: string,
  originalName?: string,
  date = new Date(),
): string {
  const prefix = kind === 'photo' ? 'IMG' : kind === 'video' ? 'VID' : 'REC'
  const stamp = formatMediaTimestamp(date)
  const ext = extFromMimeOrName(kind, mimeType, originalName)
  return `${prefix}_${stamp}.${ext}`
}

export function pickPreferredAudioMimeType(
  isTypeSupported?: (mime: string) => boolean,
): string {
  if (!isTypeSupported) return ''
  const candidates = [
    'audio/mp4',
    'audio/webm;codecs=opus',
    'audio/webm',
    'audio/ogg;codecs=opus',
  ]
  for (const mime of candidates) {
    try {
      if (isTypeSupported(mime)) return mime
    } catch {
      // ignore invalid mime checks
    }
  }
  return ''
}

export function formatRecordingDuration(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds))
  const mm = pad2(Math.floor(safe / 60))
  const ss = pad2(safe % 60)
  return `${mm}:${ss}`
}
