import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import type { Role } from '@/api/types'
import { useAuth } from './AuthProvider'
import { Spinner } from '@/components/ui/spinner'

function SessionLoader() {
  return (
    <div className="grid min-h-[40vh] place-items-center">
      <Spinner label="Cargando sesión" />
    </div>
  )
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <SessionLoader />
  if (status === 'anonymous') return <Navigate to="/login" state={{ from: location.pathname }} replace />
  return <>{children}</>
}

export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { status, user } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <SessionLoader />
  if (status === 'anonymous') return <Navigate to="/login" state={{ from: location.pathname }} replace />
  if (user && user.role !== role) return <Navigate to="/" replace />
  return <>{children}</>
}
