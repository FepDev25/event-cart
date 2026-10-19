import { apiRequest } from '@/lib/api'
import type { AuthResult, Role, User } from './types'

export function login(email: string, password: string): Promise<AuthResult> {
  return apiRequest<AuthResult>('/api/v1/auth/session', {
    method: 'POST',
    body: { email, password },
    auth: false,
  })
}

export function register(input: {
  email: string
  password: string
  displayName?: string
  role: Role
}): Promise<User> {
  return apiRequest<User>('/api/v1/auth/register', { method: 'POST', body: input, auth: false })
}

export function me(): Promise<User> {
  return apiRequest<User>('/api/v1/auth/me')
}

export function logout(): Promise<void> {
  return apiRequest<void>('/api/v1/auth/session', { method: 'DELETE' })
}
