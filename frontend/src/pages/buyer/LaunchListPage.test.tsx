import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import type { Launch } from '@/api/types'
import { jsonResponse, renderWithProviders } from '@/test/utils'
import { LaunchListPage } from './LaunchListPage'

const launch: Launch = {
  id: '11111111-1111-1111-1111-111111111111',
  title: 'Edición limitada',
  description: null,
  productName: 'Tote bag',
  productRef: 'TOTE-01',
  unitPrice: 19.9,
  currency: 'EUR',
  totalStock: 100,
  availableUnits: 42,
  reservedUnits: 3,
  confirmedUnits: 55,
  status: 'OPEN',
  opensAt: '2026-01-01T10:00:00Z',
  closesAt: '2026-01-01T12:00:00Z',
  reservationTtlSeconds: 600,
  perBuyerLimit: 2,
  sellerId: '22222222-2222-2222-2222-222222222222',
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('LaunchListPage', () => {
  it('renderiza los lanzamientos publicados', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([launch])))
    renderWithProviders(<LaunchListPage />)

    expect(await screen.findByText('Edición limitada')).toBeInTheDocument()
    expect(screen.getByText('Tote bag')).toBeInTheDocument()
    expect(screen.getByText('Abierto')).toBeInTheDocument()
  })

  it('muestra el estado vacío cuando no hay lanzamientos', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([])))
    renderWithProviders(<LaunchListPage />)

    expect(await screen.findByText(/No hay lanzamientos publicados/)).toBeInTheDocument()
  })

  it('muestra el problema cuando la API falla', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ title: 'Server Error', detail: 'boom' }, 500)),
    )
    renderWithProviders(<LaunchListPage />)

    expect(await screen.findByText('No se pudo completar')).toBeInTheDocument()
    expect(screen.getByText('boom')).toBeInTheDocument()
  })
})
