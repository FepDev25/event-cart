import { cva, type VariantProps } from 'class-variance-authority'
import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

const alertVariants = cva('flex flex-col gap-1 rounded-md border p-4 text-sm', {
  variants: {
    tone: {
      info: 'border-border bg-surface-muted text-foreground',
      warning: 'border-amber-100 bg-pending-surface text-pending',
      danger: 'border-red-100 bg-danger-surface text-danger',
      success: 'border-green-50 bg-available-surface text-available',
    },
  },
  defaultVariants: { tone: 'info' },
})

interface AlertProps extends ComponentProps<'div'>, VariantProps<typeof alertVariants> {}

export function Alert({ className, tone, ...props }: AlertProps) {
  return <div className={cn(alertVariants({ tone }), className)} {...props} />
}

export function AlertTitle({ className, ...props }: ComponentProps<'p'>) {
  return <p className={cn('font-semibold', className)} {...props} />
}
