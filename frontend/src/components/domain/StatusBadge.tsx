import { Badge } from '@/components/ui/badge'
import type { LaunchStatus, OrderStatus, ReservationStatus } from '@/api/types'

type Tone = 'available' | 'pending' | 'accent' | 'danger' | 'neutral'
interface Descriptor {
  tone: Tone
  label: string
}

export const LAUNCH_STATUS: Record<LaunchStatus, Descriptor> = {
  DRAFT: { tone: 'neutral', label: 'Borrador' },
  SCHEDULED: { tone: 'pending', label: 'Programado' },
  OPEN: { tone: 'available', label: 'Abierto' },
  CLOSED: { tone: 'neutral', label: 'Cerrado' },
  CANCELLED: { tone: 'danger', label: 'Cancelado' },
}

export const RESERVATION_STATUS: Record<ReservationStatus, Descriptor> = {
  RESERVED: { tone: 'pending', label: 'Reservada' },
  CONFIRMED: { tone: 'available', label: 'Confirmada' },
  RELEASED: { tone: 'neutral', label: 'Liberada' },
  EXPIRED: { tone: 'danger', label: 'Expirada' },
}

export const ORDER_STATUS: Record<OrderStatus, Descriptor> = {
  PENDING: { tone: 'pending', label: 'Pendiente' },
  CONFIRMED: { tone: 'available', label: 'Confirmado' },
  FAILED: { tone: 'danger', label: 'Fallido' },
}

export function StatusBadge({ descriptor }: { descriptor: Descriptor }) {
  return <Badge tone={descriptor.tone}>{descriptor.label}</Badge>
}
