import { Alert, AlertTitle } from '@/components/ui/alert'
import { describeError } from '@/lib/errors'

export function ProblemAlert({ error }: { error: unknown }) {
  if (!error) return null
  const { message } = describeError(error)
  return (
    <Alert tone="danger" role="alert">
      <AlertTitle>No se pudo completar</AlertTitle>
      <span>{message}</span>
    </Alert>
  )
}
