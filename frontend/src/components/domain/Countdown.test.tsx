import { describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { Countdown } from './Countdown'

describe('Countdown', () => {
  it('muestra el tiempo restante (MM:SS) y lo anuncia', () => {
    vi.useFakeTimers()
    const expiresAt = new Date(Date.now() + 65_000).toISOString()
    render(<Countdown expiresAt={expiresAt} />)

    expect(screen.getByText('01:05')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite')
    vi.useRealTimers()
  })

  it('llega a 00:00 y notifica la expiración una sola vez', () => {
    vi.useFakeTimers()
    const onExpire = vi.fn()
    const expiresAt = new Date(Date.now() + 2_000).toISOString()
    render(<Countdown expiresAt={expiresAt} onExpire={onExpire} />)

    act(() => {
      vi.advanceTimersByTime(5_000)
    })

    expect(screen.getByText('00:00')).toBeInTheDocument()
    expect(onExpire).toHaveBeenCalledTimes(1)
    vi.useRealTimers()
  })
})
