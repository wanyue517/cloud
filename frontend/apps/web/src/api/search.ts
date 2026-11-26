import { request } from '@/utils/request'

export interface SearchHit {
  id: string
  spaceId: string
  spaceName: string
  parentId: string
  name: string
  nodeType: 'FILE' | 'FOLDER'
  category: string
  size: number
  mimeType: string
  updatedAt: string
  matchSource: 'NAME' | 'CONTENT' | 'BOTH'
  snippet: string
}

export interface SearchResponse {
  items: SearchHit[]
  total: number
  page: number
  pageSize: number
  hasMore: boolean
}

export const searchApi = {
  search: (params: {
    query?: string
    spaceId?: string
    category?: string
    extension?: string
    includeContent?: boolean
    page?: number
    pageSize?: number
  }) => request.get<SearchResponse>('/v1/search', params),
}
