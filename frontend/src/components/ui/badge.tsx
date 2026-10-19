import { cva, type VariantProps } from 'class-variance-authority'
import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center gap-1.5 rounded-full border border-transparent px-3 py-1 text-xs font-semibold uppercase tracking-wide',
  {
    variants: {
      tone: {
        available: 'bg-available-surface text-available',
        pending: 'bg-pending-surface text-pending',
        accent: 'bg-accent-surface text-accent-strong',
        danger: 'bg-danger-surface text-danger',
        neutral: 'bg-surface-muted text-muted-foreground',
      },
    },
    defaultVariants: { tone: 'neutral' },
  },
)

interface BadgeProps extends ComponentProps<'span'>, VariantProps<typeof badgeVariants> {
  dot?: boolean
}

export function Badge({ className, tone, dot = true, children, ...props }: BadgeProps) {
  return (
    <span className={cn(badgeVariants({ tone }), className)} {...props}>
      {dot && <span aria-hidden="true" className="size-2 rounded-full bg-current" />}
      {children}
    </span>
  )
}
