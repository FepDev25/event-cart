import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Launch, Order, Reservation, User } from '@/api/types'
import { jsonResponse, renderWithProviders } from '@/test/utils'
import { LaunchDetailPage } from './LaunchDetailPage'

const buyer: User = {
  id: '33333333-3333-3333-3333-333333333333',
  email: 'buyer@eventcart.test',
  displayName: 'Buyer',
  role: 'BUYER',
}

const launch: Launch = {
  id: '11111111-1111-1111-1111-111111111111',
  title: 'Edición limitada',
  description: null,
  productName: 'Tote bag',
  productRef: 'TOTE-01',
  unitPrice: 19.9,
  currency: 'EUR',
  totalStock: 100,
  availableUnits: 5,
  reservedUnits: 0,
  confirmedUnits: 95,
  status: 'OPEN',
  opensAt: '2026-01-01T10:00:00Z',
  closesAt: '2026-01-01T12:00:00Z',
  reservationTtlSeconds: 600,
  perBuyerLimit: 2,
  sellerId: '22222222-2222-2222-2222-222222222222',
}

const reservation: Reservation = {
  id: '44444444-4444-4444-4444-444444444444',
  launchId: launch.id,
  quantity: 1,
  unitPrice: 19.9,
  currency: 'EUR',
  totalAmount: 19.9,
  status: 'RESERVED',
  expiresAt: new Date(Date.now() + 600_000).toISOString(),
  createdAt: new Date().toISOString(),
}

const order: Order = {
  id: '55555555-5555-5555-5555-555555555555',
  reservationId: reservation.id,
  launchId: launch.id,
  quantity: 1,
  unitPrice: 19.9,
  currency: 'EUR',
  totalAmount: 19.9,
  status: 'CONFIRMED',
  paymentResult: 'APPROVE',
  failureReason: null,
  createdAt: new Date().toISOString(),
}

function stubApi() {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    if (url === '/api/v1/auth/me') return Promise.resolve(jsonResponse(buyer))
    if (url === `/api/v1/launches/${launch.id}` && method === 'GET') {
      return Promise.resolve(jsonResponse(launch))
    }
    if (url === `/api/v1/launches/${launch.id}/reservations` && method === 'POST') {
      return Promise.resolve(jsonResponse(reservation, 201))
    }
    if (url === `/api/v1/reservations/${reservation.id}/checkout` && method === 'POST') {
      return Promise.resolve(jsonResponse(order, 200))
    }
    return Promise.resolve(jsonResponse({ detail: `unhandled ${method} ${url}` }, 404))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

beforeEach(() => {
  localStorage.setItem('eventcart.token', 'test-token')
})

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('LaunchDetailPage — flujo de compra', () => {
  it('reserva una unidad y completa el checkout simulado', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()

    renderWithProviders(
      <Routes>
        <Route path="/launch/:id" element={<LaunchDetailPage />} />
      </Routes>,
      { route: `/launch/${launch.id}` },
    )

    const reserveButton = await screen.findByRole('button', { name: /Reservar 1 unidad/i })
    await waitFor(() => expect(reserveButton).toBeEnabled())
    await user.click(reserveButton)

    expect(await screen.findByText('Tu reserva')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Completar compra/i }))
    expect(await screen.findByText('Pago simulado')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Pagar' }))

    expect(await screen.findByText('Pago aprobado. Tu pedido quedó confirmado.')).toBeInTheDocument()

    const reserveCall = fetchMock.mock.calls.find(
      ([input, init]) =>
        String(input) === `/api/v1/launches/${launch.id}/reservations` && init?.method === 'POST',
    )
    expect(reserveCall).toBeDefined()
    const [, init] = reserveCall ?? []
    const headers = (init?.headers ?? {}) as Record<string, string>
    expect(headers['Idempotency-Key']).toBeTruthy()
  })

  it('muestra el estado agotado cuando no hay disponibilidad', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        if (url === '/api/v1/auth/me') return Promise.resolve(jsonResponse(buyer))
        if (url === `/api/v1/launches/${launch.id}` && (init?.method ?? 'GET') === 'GET') {
          return Promise.resolve(jsonResponse({ ...launch, availableUnits: 0, reservedUnits: 0, confirmedUnits: 100 }))
        }
        return Promise.resolve(jsonResponse({}, 404))
      }),
    )

    renderWithProviders(
      <Routes>
        <Route path="/launch/:id" element={<LaunchDetailPage />} />
      </Routes>,
      { route: `/launch/${launch.id}` },
    )

    expect(await screen.findByText('Agotado')).toBeInTheDocument()
  })
})
