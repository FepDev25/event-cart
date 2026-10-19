import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '@/auth/AuthProvider'
import { Button } from '@/components/ui/button'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-surface-muted text-foreground' : 'text-muted-foreground hover:bg-surface-muted hover:text-foreground'
  }`

export function AppLayout() {
  const { user, status, logout } = useAuth()

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-40 border-b border-border bg-surface/95 backdrop-blur">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-5 py-3">
          <Link to="/" className="font-heading text-lg font-bold tracking-tight">
            Event<span className="text-accent-strong">Cart</span>
          </Link>

          <nav aria-label="Principal" className="flex flex-wrap items-center gap-1">
            <NavLink to="/" end className={navLinkClass}>
              Lanzamientos
            </NavLink>
            {user && (
              <NavLink to="/reservations" className={navLinkClass}>
                Mis reservas
              </NavLink>
            )}
            {user?.role === 'ORGANIZER' && (
              <NavLink to="/org" className={navLinkClass}>
                Organizador
              </NavLink>
            )}
            {user?.role === 'ORGANIZER' && (
              <NavLink to="/lab" className={navLinkClass}>
                Laboratorio
              </NavLink>
            )}
          </nav>

          <div className="flex items-center gap-2">
            {status === 'loading' ? null : user ? (
              <>
                <span className="hidden text-sm text-muted-foreground sm:inline">
                  {user.role === 'ORGANIZER' ? 'Organizador' : 'Comprador'}
                </span>
                <Button variant="outline" size="sm" onClick={() => void logout()}>
                  Salir
                </Button>
              </>
            ) : (
              <Button asChild variant="primary" size="sm">
                <Link to="/login">Entrar</Link>
              </Button>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl grow px-5 py-6">
        <Outlet />
      </main>

      <footer className="border-t border-border py-6">
        <div className="mx-auto max-w-5xl px-5 text-xs text-muted-foreground">
          EventCart — motor de comercio para lanzamientos limitados. Demo de portafolio; sin pagos reales.
        </div>
      </footer>
    </div>
  )
}
