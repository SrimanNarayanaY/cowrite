/** Shapes returned by the Spring Boot API. Keep in sync with the backend DTO records. */

export interface LoginResponse {
  token: string
}

export interface UserResponse {
  id: number
  email: string
  createdAt: string
}

export interface DocumentSummary {
  id: number
  title: string
  content: string | null
  version: number
  ownerId: number | null
  ownerEmail: string | null
  createdAt: string
  updatedAt: string
}

export type DocumentAccess = 'OWNER' | 'EDITOR' | 'VIEWER'

/** Detail response: the editor also needs the caller's access level. */
export interface DocumentDetail extends DocumentSummary {
  access: DocumentAccess
  canEdit: boolean
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ApiErrorBody {
  timestamp?: string
  status: number
  error: string
  message: string
  fieldErrors?: Record<string, string>
}
