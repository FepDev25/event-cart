const LOCALE = 'es-ES'

export function formatMoney(amount: number | string, currency: string): string {
  const value = typeof amount === 'string' ? Number(amount) : amount
  return new Intl.NumberFormat(LOCALE, { style: 'currency', currency }).format(value)
}

export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

export function formatRelativeFromNow(iso: string, now: number = Date.now()): string {
  const diffMs = new Date(iso).getTime() - now
  const abs = Math.abs(diffMs)
  const minutes = Math.round(abs / 60_000)
  const rtf = new Intl.RelativeTimeFormat(LOCALE, { numeric: 'auto' })
  if (abs < 60_000) return rtf.format(Math.round(diffMs / 1000), 'second')
  if (minutes < 60) return rtf.format(Math.round(diffMs / 60_000), 'minute')
  const hours = Math.round(minutes / 60)
  if (hours < 24) return rtf.format(Math.round(diffMs / 3_600_000), 'hour')
  return rtf.format(Math.round(diffMs / 86_400_000), 'day')
}

/** Devuelve "MM:SS" restante (mínimo 00:00) para un instante futuro. */
export function formatCountdown(iso: string, now: number = Date.now()): string {
  const remaining = Math.max(0, new Date(iso).getTime() - now)
  const totalSeconds = Math.floor(remaining / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}
