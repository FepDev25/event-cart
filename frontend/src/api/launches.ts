import { apiRequest } from '@/lib/api'
import type { CreateLaunchInput, Launch } from './types'

export function listPublicLaunches(): Promise<Launch[]> {
  return apiRequest<Launch[]>('/api/v1/launches', { auth: false })
}

export function getLaunch(id: string): Promise<Launch> {
  return apiRequest<Launch>(`/api/v1/launches/${id}`, { auth: false })
}

/** Lanzamientos del organizador autenticado (incluye borradores). */
export function listMyLaunches(): Promise<Launch[]> {
  return apiRequest<Launch[]>('/api/v1/launches/mine')
}

export function createLaunch(input: CreateLaunchInput): Promise<Launch> {
  return apiRequest<Launch>('/api/v1/launches', { method: 'POST', body: input })
}

export function reconfigureLaunch(id: string, input: CreateLaunchInput): Promise<Launch> {
  return apiRequest<Launch>(`/api/v1/launches/${id}`, { method: 'PATCH', body: input })
}

export type LaunchAction = 'schedule' | 'open' | 'close' | 'cancel'

export function runLaunchAction(id: string, action: LaunchAction): Promise<Launch> {
  return apiRequest<Launch>(`/api/v1/launches/${id}/${action}`, { method: 'POST' })
}
