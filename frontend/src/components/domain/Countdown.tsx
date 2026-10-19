import { useEffect, useRef, useState } from 'react'
import { formatCountdown } from '@/lib/format'
import { cn } from '@/lib/utils'

interface CountdownProps {
  expiresAt: string
  onExpire?: () => void
  className?: string
}

/**
 * Contador de caducidad INFORMATIVO (decisión M5): refleja `expiresAt` del servidor y anuncia un
 * estado significativo, nunca un número desnudo ni stock confirmado.
 */
export function Countdown({ expiresAt, onExpire, className }: CountdownProps) {
  const [now, setNow] = useState(() => Date.now())
  const firedRef = useRef(false)

  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [])

  const remainingMs = new Date(expiresAt).getTime() - now
  const expired = remainingMs <= 0

  useEffect(() => {
    if (expired && !firedRef.current) {
      firedRef.current = true
      onExpire?.()
    }
  }, [expired, onExpire])

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn('flex items-baseline gap-3', className)}
    >
      <span
        className={cn(
          'font-heading text-2xl font-semibold tabular-nums',
          expired ? 'text-danger' : 'text-pending',
        )}
      >
        {formatCountdown(expiresAt, now)}
      </span>
      <span className="text-sm text-muted-foreground">
        {expired ? 'El tiempo terminó.' : 'para completar la compra'}
      </span>
    </div>
  )
}
