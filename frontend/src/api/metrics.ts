import { apiRequest } from '@/lib/api'

export interface MetricMeasurement {
  statistic: string
  value: number
}

export interface Metric {
  name: string
  description?: string
  baseUnit?: string
  measurements: MetricMeasurement[]
  availableTags?: { tag: string; values: string[] }[]
}

export function getMetric(name: string): Promise<Metric> {
  return apiRequest<Metric>(`/actuator/metrics/${encodeURIComponent(name)}`)
}

export const EVENTING_METRICS = {
  outboxPublished: 'eventcart.outbox.published',
  outboxFailures: 'eventcart.outbox.publish.failures',
  duplicates: 'eventcart.events.duplicates',
  deadLetters: 'eventcart.events.dlt',
  reservationsExpired: 'eventcart.reservations.expired',
  ordersFailed: 'eventcart.reconciliation.orders_failed',
  staleReserved: 'eventcart.reconciliation.stale_reserved',
} as const
