import type { Problem } from '@/api/types'

const TOKEN_KEY = 'eventcart.token'
const API_BASE = import.meta.env.VITE_API_BASE ?? ''

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem

  constructor(status: number, problem: Problem) {
    super(problem.detail ?? problem.title ?? `Error ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  body?: unknown
  headers?: Record<string, string>
  auth?: boolean
  signal?: AbortSignal
}

/**
 * Cliente HTTP único. El token Bearer vive en localStorage (decisión M6) y se añade a menos que
 * `auth: false`. Los errores del backend son RFC 9457 (application/problem+json).
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, headers = {}, auth = true, signal } = options
  const finalHeaders: Record<string, string> = { ...headers }
  if (body !== undefined) finalHeaders['Content-Type'] = 'application/json'

  const token = getToken()
  if (auth && token) finalHeaders['Authorization'] = `Bearer ${token}`

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers: finalHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  })

  if (response.status === 204) return undefined as T

  const text = await response.text()
  const data = text ? JSON.parse(text) : undefined
  if (!response.ok) {
    throw new ApiError(response.status, (data ?? {}) as Problem)
  }
  return data as T
}
