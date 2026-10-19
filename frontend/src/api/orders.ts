import { apiRequest } from '@/lib/api'
import type { Order } from './types'

export function checkout(reservationId: string, paymentToken: string, idempotencyKey: string): Promise<Order> {
  return apiRequest<Order>(`/api/v1/reservations/${reservationId}/checkout`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: { paymentToken },
  })
}

export function getOrder(id: string): Promise<Order> {
  return apiRequest<Order>(`/api/v1/orders/${id}`)
}

export function listMyOrders(): Promise<Order[]> {
  return apiRequest<Order[]>('/api/v1/orders')
}
