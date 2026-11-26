export type { ApiPaths, ApiSchemas, ResourceId, components, paths } from './generated/openapi'

/** 与后端 com.wanyue.cloud.common.result.R<T> 对齐的统一响应信封 */
export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
  traceId?: string | null
}
