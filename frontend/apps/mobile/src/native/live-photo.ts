import { Capacitor, registerPlugin } from '@capacitor/core'
import { Directory, Filesystem } from '@capacitor/filesystem'
import { mobileDriveApi, uploadFileDirect, type DriveNode } from '@/api/drive'

interface Resource { path: string; name: string; mimeType: string }
interface Selection { sessionId: string; photo: Resource; video: Resource; poster: Resource }
interface LivePhotoBridge {
  pick(): Promise<Selection>
  save(options: { sessionId: string; photoName: string; videoName: string }): Promise<void>
  cleanup(options: { sessionId: string }): Promise<void>
}
const bridge = registerPlugin<LivePhotoBridge>('LivePhoto')
export function supportsNativeLivePhoto(): boolean {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'ios' && Capacitor.isPluginAvailable('LivePhoto')
}

async function resourceFile(resource: Resource, name: string): Promise<File> {
  const response = await fetch(Capacitor.convertFileSrc(resource.path))
  if (!response.ok) throw new Error('读取实况临时文件失败')
  const blob = await response.blob()
  if (blob.size <= 0 || blob.size > 64 * 1024 * 1024) throw new Error('实况原件须在 64 MB 以内')
  return new File([blob], name, { type: resource.mimeType })
}

/** Upload all original tracks before publishing the immutable pairing. Partial files are retained for recovery. */
let uploadInProgress = false
export async function uploadNativeLivePhoto(
  spaceId: string, parentId: string, progress: (message: string) => void,
  confirm: (bytes: number) => Promise<boolean> = async () => true,
): Promise<boolean> {
  if (uploadInProgress) throw new Error('已有实况照片正在上传，请稍候')
  uploadInProgress = true
  try { return await performUpload(spaceId, parentId, progress, confirm) }
  finally { uploadInProgress = false }
}

async function performUpload(
  spaceId: string, parentId: string,
  progress: (message: string) => void,
  confirm: (bytes: number) => Promise<boolean> = async () => true,
): Promise<boolean> {
  if (!supportsNativeLivePhoto()) throw new Error('请在支持实况的 Cloudlane iOS App 中使用')
  progress('正在从相册导出实况原件…')
  const selection = await bridge.pick()
  const uploaded: string[] = []
  try {
    const stem = `Live-${new Date().toISOString().replace(/[:.]/g, '-')}-${selection.sessionId.slice(0, 8)}`
    const photo = await resourceFile(selection.photo, `${stem}.${selection.photo.name.split('.').pop()}`)
    const video = await resourceFile(selection.video, `${stem}.MOV`)
    const poster = await resourceFile(selection.poster, `${stem}.preview.jpg`)
    if (!await confirm(photo.size + video.size + poster.size)) return false
    progress('正在上传照片原件…')
    const nodeId = await uploadFileDirect(spaceId, parentId, photo, value => progress(`正在上传照片原件 ${value}%`))
    uploaded.push(nodeId)
    const versions = await mobileDriveApi.versions(spaceId, nodeId)
    const version = versions[0]?.id
    if (!version) throw new Error('照片版本不存在')
    const videoId = await uploadFileDirect(spaceId, parentId, video, value => progress(`正在上传实况视频 ${value}%`))
    uploaded.push(videoId)
    const posterId = await uploadFileDirect(spaceId, parentId, poster, value => progress(`正在上传兼容预览图 ${value}%`))
    uploaded.push(posterId)
    progress('正在绑定实况照片…')
    await mobileDriveApi.pairLivePhoto(spaceId, nodeId, version, videoId, posterId)
    return true
  } catch (error) {
    if (uploaded.length) throw new Error(`${error instanceof Error ? error.message : '上传失败'}；已上传的 ${uploaded.length} 个原件保留在当前目录，可重新上传或手动处理`)
    throw error
  } finally {
    await bridge.cleanup({ sessionId: selection.sessionId }).catch(() => {})
  }
}

async function stageBlob(sessionId: string, name: string, blob: Blob) {
  if (blob.size <= 0 || blob.size > 64 * 1024 * 1024) throw new Error('实况原件须在 64 MB 以内')
  // Decode each base64 chunk on the native side rather than retaining multiple full-size copies in JS.
  const path = `cloudlane-live/${sessionId}/${name}`
  const chunkSize = 768 * 1024
  for (let offset = 0; offset < blob.size; offset += chunkSize) {
    const bytes = new Uint8Array(await blob.slice(offset, offset + chunkSize).arrayBuffer())
    let binary = ''
    for (let index = 0; index < bytes.length; index += 0x8000) binary += String.fromCharCode(...bytes.subarray(index, index + 0x8000))
    if (offset === 0) await Filesystem.writeFile({ directory: Directory.Cache, path, data: btoa(binary), recursive: true })
    else await Filesystem.appendFile({ directory: Directory.Cache, path, data: btoa(binary) })
  }
}

export async function saveNativeLivePhoto(node: DriveNode, versionId?: string): Promise<void> {
  if (!supportsNativeLivePhoto()) throw new Error('请在 Cloudlane iOS App 中保存实况照片')
  // Pin the photo version before any asynchronous download or navigation.
  const version = versionId ?? node.currentVersionId ?? undefined
  const sessionId = crypto.randomUUID()
  try {
    const photo = await mobileDriveApi.livePhotoPart(node, 'photo', version)
    const video = await mobileDriveApi.livePhotoPart(node, 'video', version)
    const photoName = /hei[cf]/i.test(photo.type) ? 'photo.heic' : 'photo.jpg'
    await stageBlob(sessionId, photoName, photo)
    await stageBlob(sessionId, 'paired.mov', video)
    await bridge.save({ sessionId, photoName, videoName: 'paired.mov' })
  } finally {
    await bridge.cleanup({ sessionId }).catch(() => {})
  }
}
