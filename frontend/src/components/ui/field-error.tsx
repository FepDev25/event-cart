import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function FieldError({ id, children, className }: { id?: string; children?: ReactNode; className?: string }) {
  if (!children) return null
  return (
    <p id={id} role="alert" className={cn('text-sm font-medium text-danger', className)}>
      {children}
    </p>
  )
}
