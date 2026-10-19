export type Role = 'BUYER' | 'ORGANIZER'

export type LaunchStatus = 'DRAFT' | 'SCHEDULED' | 'OPEN' | 'CLOSED' | 'CANCELLED'

export type ReservationStatus = 'RESERVED' | 'CONFIRMED' | 'RELEASED' | 'EXPIRED'

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'FAILED'

export interface User {
  id: string
  email: string
  displayName: string | null
  role: Role
}

export interface AuthResult {
  token: string
  expiresAt: string
  user: User
}

export interface Launch {
  id: string
  title: string
  description: string | null
  productName: string
  productRef: string | null
  unitPrice: number
  currency: string
  totalStock: number
  availableUnits: number
  reservedUnits: number
  confirmedUnits: number
  status: LaunchStatus
  opensAt: string
  closesAt: string
  reservationTtlSeconds: number
  perBuyerLimit: number | null
  sellerId: string
}

export interface Reservation {
  id: string
  launchId: string
  quantity: number
  unitPrice: number
  currency: string
  totalAmount: number
  status: ReservationStatus
  expiresAt: string
  createdAt: string
}

export interface Order {
  id: string
  reservationId: string
  launchId: string
  quantity: number
  unitPrice: number
  currency: string
  totalAmount: number
  status: OrderStatus
  paymentResult: string | null
  failureReason: string | null
  createdAt: string
}

export interface CreateLaunchInput {
  title: string
  description?: string | null
  productName: string
  productRef?: string | null
  unitPrice: number
  currency: string
  totalStock: number
  opensAt: string
  closesAt: string
  reservationTtlSeconds?: number | null
  perBuyerLimit?: number | null
}

export interface Problem {
  type?: string
  title?: string
  status?: number
  detail?: string
}
