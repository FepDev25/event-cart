import { ApiError } from '@/lib/api'

const FRIENDLY: Record<string, string> = {
  'invalid-credentials': 'Correo o contraseña incorrectos.',
  'email-already-used': 'Ese correo ya está registrado.',
  'validation-failed': 'Revisa los datos del formulario.',
  'missing-idempotency-key': 'La solicitud no se pudo identificar de forma segura. Reintenta.',
  'unauthenticated': 'Tu sesión expiró. Vuelve a iniciar sesión.',
  'forbidden': 'No tienes permiso para esta acción.',
  'not-found': 'No encontramos ese recurso.',
  'stock-insufficient': 'No hay suficientes unidades disponibles.',
  'purchase-limit-exceeded': 'Superaste el límite de compra para este lanzamiento.',
  'launch-not-open': 'La venta todavía no está abierta.',
  'outside-sale-window': 'La ventana de venta está cerrada.',
  'invalid-state-transition': 'Esa acción no es válida en el estado actual.',
  'idempotency-conflict': 'La solicitud entró en conflicto con una anterior equivalente.',
}

function errorKey(error: unknown): string | null {
  if (error instanceof ApiError) {
    return (error.problem.type ?? '').replace('urn:eventcart:error:', '') || null
  }
  return null
}

export function describeError(error: unknown): { message: string; status?: number } {
  const key = errorKey(error)
  if (key && FRIENDLY[key]) return { message: FRIENDLY[key], status: (error as ApiError).status }
  if (error instanceof ApiError) {
    return { message: error.problem.detail ?? `Error ${error.status}`, status: error.status }
  }
  if (error instanceof Error) return { message: error.message }
  return { message: 'Ocurrió un error inesperado.' }
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}
