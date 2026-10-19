import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import type { Role } from '@/api/types'
import { useAuth } from '@/auth/AuthProvider'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ProblemAlert } from '@/components/domain/ProblemAlert'

type Mode = 'login' | 'register'

export function LoginPage() {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [role, setRole] = useState<Role>('BUYER')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      if (mode === 'login') {
        await login(email, password)
      } else {
        await register({ email, password, displayName: displayName || undefined, role })
      }
      navigate(from, { replace: true })
    } catch (caught) {
      setError(caught)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6 py-6">
      <div className="flex flex-col gap-1 text-center">
        <h1 className="font-heading text-2xl font-semibold">
          {mode === 'login' ? 'Entrar' : 'Crear cuenta'}
        </h1>
        <p className="text-sm text-muted-foreground">
          Identidad mínima del prototipo. El rol define qué podés hacer.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{mode === 'login' ? 'Sesión' : 'Registro'}</CardTitle>
          <CardDescription>
            {mode === 'login'
              ? 'Usá tu correo y contraseña.'
              : 'Elegí comprador para reservar, u organizador para publicar ventas.'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
            <div className="flex flex-col gap-2">
              <Label htmlFor="email">Correo</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="password">Contraseña</Label>
              <Input
                id="password"
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                required
                minLength={8}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>

            {mode === 'register' && (
              <>
                <div className="flex flex-col gap-2">
                  <Label htmlFor="displayName">Nombre (opcional)</Label>
                  <Input
                    id="displayName"
                    value={displayName}
                    onChange={(event) => setDisplayName(event.target.value)}
                  />
                </div>
                <fieldset className="flex flex-col gap-2">
                  <legend className="text-sm font-medium">Rol</legend>
                  <div className="flex gap-2">
                    {(['BUYER', 'ORGANIZER'] as const).map((option) => (
                      <label
                        key={option}
                        className="flex flex-1 items-center gap-2 rounded-md border border-border p-3 text-sm"
                      >
                        <input
                          type="radio"
                          name="role"
                          value={option}
                          checked={role === option}
                          onChange={() => setRole(option)}
                          className="size-4"
                        />
                        {option === 'BUYER' ? 'Comprador' : 'Organizador'}
                      </label>
                    ))}
                  </div>
                </fieldset>
              </>
            )}

            {error != null && <ProblemAlert error={error} />}

            <Button type="submit" variant="primary" disabled={submitting}>
              {submitting ? 'Enviando…' : mode === 'login' ? 'Entrar' : 'Crear cuenta'}
            </Button>
          </form>

          <p className="mt-4 text-center text-sm text-muted-foreground">
            {mode === 'login' ? '¿No tenés cuenta?' : '¿Ya tenés cuenta?'}{' '}
            <button
              type="button"
              className="font-semibold text-accent-strong underline"
              onClick={() => {
                setMode(mode === 'login' ? 'register' : 'login')
                setError(null)
              }}
            >
              {mode === 'login' ? 'Registrate' : 'Iniciá sesión'}
            </button>
          </p>
        </CardContent>
      </Card>

      <Link to="/" className="text-center text-sm text-muted-foreground hover:text-foreground">
        ← Volver a los lanzamientos
      </Link>
    </div>
  )
}
