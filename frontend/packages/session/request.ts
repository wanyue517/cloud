import axios, {
    AxiosHeaders,
    type AxiosProgressEvent,
    type AxiosRequestConfig,
    type AxiosResponse,
    type InternalAxiosRequestConfig,
    type RawAxiosHeaders,
} from 'axios'

/** Cloudlane 后端统一返回体：R<T> */
export interface R<T> {
    code: number
    msg: string
    data: T
    traceId?: string | null
}

export interface RequestOptions extends AxiosRequestConfig {
    /** 不自动注入 Access Token，例如登录、注册、第三方请求 */
    skipAuth?: boolean
    /** 禁止 401 自动刷新，例如刷新接口自身 */
    skipRefresh?: boolean
    /** 不触发全局错误提示；异常仍向调用方抛出 */
    silent?: boolean
    /** data: 解包 R.data（默认）；raw: 返回原始 body；response: 返回 AxiosResponse */
    responseMode?: 'data' | 'raw' | 'response'
    /** 内部使用：同一请求最多重试一次 */
    _retried?: boolean
}

export interface UploadOptions extends RequestOptions {
    fieldName?: string
    extraData?: Record<string, string | number | boolean | Blob | null | undefined>
    onProgress?: (percent: number | null, event: AxiosProgressEvent) => void
}

export class RequestError extends Error {
    readonly code?: number
    readonly httpStatus?: number

    constructor(
        message: string,
        code?: number,
        httpStatus?: number,
    ) {
        super(message)
        this.name = 'RequestError'
        this.code = code
        this.httpStatus = httpStatus
    }
}

interface RequestHooks {
    /** 从 Pinia 等状态管理器实时读取，不缓存旧 Token */
    getAccessToken?: () => string | null | undefined
    /** 自行调用刷新接口并更新 Token，返回新 Access Token */
    refreshAccessToken?: () => Promise<string | null>
    /** 无法刷新时进入未登录态 */
    onUnauthorized?: () => void
    /** 统一 Toast；取消请求和 silent 请求不会触发 */
    onError?: (error: RequestError) => void
    /** 可选：全局公共请求头，单次 options.headers 仍可覆盖 */
    defaultHeaders?: Record<string, string>
}

let hooks: RequestHooks = {}
let refreshPromise: Promise<string | null> | null = null
const privateSpaceTokens = new Map<string, string>()
/** 个人中心「私密设置」全局解锁令牌：可访问全部私密空间与私密条目 */
let vaultToken: string | null = null

export function setVaultToken(token: string | null) {
    vaultToken = token
}

export function hasVaultToken(): boolean {
    return !!vaultToken
}

export function setPrivateSpaceToken(spaceId: string, token: string) {
    privateSpaceTokens.set(spaceId, token)
}

export function clearPrivateSpaceToken(spaceId?: string) {
    if (spaceId) privateSpaceTokens.delete(spaceId)
    else {
        privateSpaceTokens.clear()
        vaultToken = null
    }
}

export function hasPrivateSpaceToken(spaceId: string): boolean {
    return privateSpaceTokens.has(spaceId) || !!vaultToken
}

/** 在 main.ts / 应用初始化时注册一次 */
export function setupRequest(config: RequestHooks) {
    hooks = config
    Object.entries(config.defaultHeaders ?? {}).forEach(([key, value]) => {
        client.defaults.headers.common[key] = value
    })
}

/** 运行时切换 API 基地址（如 Capacitor 原生 App 指向用户自托管域名） */
export function setApiBaseUrl(baseURL: string) {
    client.defaults.baseURL = baseURL.replace(/\/+$/, '') || '/api'
}

const client = axios.create({
    // Both applications use the same origin for session cookies and API traffic.
    baseURL: '/api',
    timeout: 20_000,
    headers: {Accept: 'application/json'},
})

function isR(value: unknown): value is R<unknown> {
    return typeof value === 'object' && value !== null &&
        typeof (value as { code?: unknown }).code === 'number'
}

/** 只对 API 同源地址注入 Token；防止 OSS URL 和单次 baseURL 覆盖泄露 JWT */
function isTrustedRequest(config: InternalAxiosRequestConfig) {
    const expectedOrigin = new URL(client.defaults.baseURL || '/api', window.location.origin).origin
    const requestBase = new URL(config.baseURL || client.defaults.baseURL || '/api', window.location.origin)
    const destination = new URL(config.url || '', requestBase)
    return requestBase.origin === expectedOrigin && destination.origin === expectedOrigin
}

client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const options = config as InternalAxiosRequestConfig & RequestOptions
    const token = hooks.getAccessToken?.()
    if (!options.skipAuth && isTrustedRequest(config) && token &&
        !config.headers.has('Authorization')) {
        config.headers.set('Authorization', `Bearer ${token}`)
    }
    if (isTrustedRequest(config) && config.url && !config.headers.has('X-Private-Token')) {
        const match = /\/v1\/spaces\/(\d+)\b/.exec(config.url)
        const spaceId = match?.[1]
        const pt = (spaceId && privateSpaceTokens.get(spaceId)) || vaultToken
        if (pt) config.headers.set('X-Private-Token', pt)
    }
    return config
})

/** 统一解析成功响应。放在 send 而不是 Axios 拦截器中，避免改变 AxiosResponse 的类型约定。 */
async function unwrapResponse<T>(response: AxiosResponse, options: RequestOptions): Promise<T> {
    if (options.responseMode === 'response') return response as unknown as T
    if (options.responseMode === 'raw') return response.data as T

    // 文件下载：返回 Blob；若服务端以 JSON 返回了 R.fail，则抛出业务错误
    if (response.config.responseType === 'blob' && response.data instanceof Blob) {
        const contentType = String(response.headers['content-type'] || '')
        if (contentType.includes('application/json')) {
            let body: unknown
            try {
                body = JSON.parse(await response.data.text())
            } catch { /* 不是 R JSON，直接返回 Blob */
            }
            if (isR(body) && body.code !== 0) {
                throw new RequestError(body.msg || '文件下载失败', body.code, response.status)
            }
        }
        return response.data as T
    }

    if (!isR(response.data)) {
        throw new RequestError('接口响应格式不正确，请检查 R<T> 返回体', undefined, response.status)
    }
    if (response.data.code !== 0) {
        throw new RequestError(response.data.msg || '请求失败', response.data.code, response.status)
    }
    return response.data.data as T
}

async function normalizeError(error: unknown): Promise<RequestError> {
    if (error instanceof RequestError) return error
    if (axios.isAxiosError(error)) {
        const status = error.response?.status
        let body: unknown = error.response?.data
        if (body instanceof Blob && body.type.includes('json')) {
            try {
                body = JSON.parse(await body.text())
            } catch { /* 使用 HTTP 状态信息 */
            }
        }
        if (isR(body)) return new RequestError(body.msg || '请求失败', body.code, status)
        if (error.code === 'ERR_CANCELED') return new RequestError('请求已取消', undefined, status)
        if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
            return new RequestError('请求超时，请稍后重试', undefined, status)
        }
        if (!error.response) return new RequestError('网络连接失败，请检查网络', undefined, status)
        const messages: Record<number, string> = {
            400: '请求参数错误', 401: '登录已失效', 403: '无权限访问',
            404: '请求资源不存在', 429: '操作过于频繁', 500: '服务器异常',
            502: '网关异常', 503: '服务暂不可用',
        }
        return new RequestError(messages[status ?? 0] || error.message || '请求失败', status, status)
    }
    return new RequestError(error instanceof Error ? error.message : '未知错误')
}

function refreshOnce() {
    if (!refreshPromise) {
        refreshPromise = Promise.resolve()
            .then(() => hooks.refreshAccessToken?.() ?? null)
            .finally(() => {
                refreshPromise = null
            })
    }
    return refreshPromise
}

/** 统一错误处理、Token 单飞刷新、至多重试一次，且保留每次请求独立的泛型。 */
async function send<T>(options: RequestOptions): Promise<T> {
    let response: AxiosResponse | undefined
    try {
        const received = await client.request<unknown, AxiosResponse>(options)
        response = received
        return await unwrapResponse<T>(received, options)
    } catch (error) {
        const normalized = await normalizeError(error)
        const original = (axios.isAxiosError(error)
            ? error.config
            : response?.config ?? options) as InternalAxiosRequestConfig
        const unauthorized = normalized.code === 401 || normalized.httpStatus === 401
        const isProtectedApi = !options.skipAuth && isTrustedRequest(original)

        if (unauthorized && isProtectedApi && !options.skipRefresh &&
            !options._retried && hooks.refreshAccessToken) {
            try {
                const sentAuthorization = AxiosHeaders.from(original.headers).get('Authorization')
                const latestToken = hooks.getAccessToken?.()
                // 其他并发请求可能已刷新成功；直接复用最新 Token
                const newToken = latestToken && sentAuthorization !== `Bearer ${latestToken}`
                    ? latestToken
                    : await refreshOnce()

                if (newToken) {
                    const headers = AxiosHeaders.from(options.headers as RawAxiosHeaders | undefined)
                    headers.set('Authorization', `Bearer ${newToken}`)
                    return send<T>({...options, headers, _retried: true})
                }
            } catch {
                // 刷新失败，交给下方统一处理
            }
        }

        if (unauthorized && isProtectedApi) hooks.onUnauthorized?.()
        if (!options.silent && !axios.isCancel(error)) hooks.onError?.(normalized)
        throw normalized
    }
}

export const request = {
    get<T = unknown>(url: string, params?: unknown, options: RequestOptions = {}) {
        return send<T>({...options, url, params: params ?? options.params, method: 'GET'})
    },
    post<T = unknown>(url: string, data?: unknown, options: RequestOptions = {}) {
        return send<T>({...options, url, data, method: 'POST'})
    },
    put<T = unknown>(url: string, data?: unknown, options: RequestOptions = {}) {
        return send<T>({...options, url, data, method: 'PUT'})
    },
    patch<T = unknown>(url: string, data?: unknown, options: RequestOptions = {}) {
        return send<T>({...options, url, data, method: 'PATCH'})
    },
    delete<T = unknown>(url: string, params?: unknown, options: RequestOptions = {}) {
        return send<T>({...options, url, params: params ?? options.params, method: 'DELETE'})
    },
    /** 通用请求，例如 HEAD / 特殊场景 */
    send<T = unknown>(options: RequestOptions) {
        return send<T>(options)
    },
    /** 返回未经 R.data 解包的服务端原始响应体 */
    raw<T = unknown>(options: RequestOptions) {
        return send<T>({...options, responseMode: 'raw'})
    },
    /** 返回 AxiosResponse，便于读取响应头和状态码 */
    response<T = unknown>(options: RequestOptions) {
        return send<AxiosResponse<T>>({...options, responseMode: 'response'})
    },
    upload<T = unknown>(
        url: string,
        file: File | Blob | File[] | FormData,
        options: UploadOptions = {},
    ) {
        const {fieldName = 'file', extraData, onProgress, onUploadProgress, ...rest} = options
        const form = file instanceof FormData ? file : new FormData()
        if (!(file instanceof FormData)) {
            const files = Array.isArray(file) ? file : [file]
            files.forEach(item => form.append(fieldName, item, item instanceof File ? item.name : 'file'))
        }
        Object.entries(extraData ?? {}).forEach(([key, value]) => {
            if (value !== null && value !== undefined) {
                form.append(key, value instanceof Blob ? value : String(value))
            }
        })
        const headers = AxiosHeaders.from(rest.headers as RawAxiosHeaders | undefined)
        headers.delete('Content-Type') // 让浏览器自动携带 multipart boundary
        return send<T>({
            ...rest, url, method: 'POST', data: form, headers,
            onUploadProgress: event => {
                const percent = event.total ? Math.round(event.loaded / event.total * 100) : null
                onProgress?.(percent, event)
                onUploadProgress?.(event)
            },
        })
    },
    /** 获取二进制内容，不自动触发浏览器下载 */
    download(url: string, params?: unknown, options: RequestOptions = {}) {
        return send<Blob>({
            ...options, url, params: params ?? options.params,
            method: 'GET', responseType: 'blob',
        })
    },
}

/** 浏览器保存 Blob；可与 request.download() 搭配使用 */
export function saveBlob(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1_000)
}
