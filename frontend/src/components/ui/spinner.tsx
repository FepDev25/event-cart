import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

export function Skeleton({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div className={cn('animate-pulse rounded-md bg-surface-muted', className)} {...props} />
  )
}

export function Spinner({ label = 'Cargando', className }: { label?: string; className?: string }) {
  return (
    <div role="status" aria-live="polite" className={cn('flex items-center gap-2 text-sm text-muted-foreground', className)}>
      <span
        aria-hidden="true"
        className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent"
      />
      {label}
    </div>
  )
}
