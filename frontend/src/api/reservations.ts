import { apiRequest } from '@/lib/api'
import type { Reservation } from './types'

export function reserve(launchId: string, quantity: number, idempotencyKey: string): Promise<Reservation> {
  return apiRequest<Reservation>(`/api/v1/launches/${launchId}/reservations`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: { quantity },
  })
}

export function getReservation(id: string): Promise<Reservation> {
  return apiRequest<Reservation>(`/api/v1/reservations/${id}`)
}

export function listMyReservations(): Promise<Reservation[]> {
  return apiRequest<Reservation[]>('/api/v1/reservations')
}

export function cancelReservation(id: string): Promise<Reservation> {
  return apiRequest<Reservation>(`/api/v1/reservations/${id}/cancel`, { method: 'POST' })
}
