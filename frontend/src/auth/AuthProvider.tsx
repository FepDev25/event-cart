import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '@/api/auth'
import type { Role, User } from '@/api/types'
import { getToken, setToken } from '@/lib/api'
import { queryClient } from '@/lib/query'

type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

interface RegisterInput {
  email: string
  password: string
  displayName?: string
  role: Role
}

interface AuthContextValue {
  user: User | null
  status: AuthStatus
  login: (email: string, password: string) => Promise<User>
  register: (input: RegisterInput) => Promise<User>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  useEffect(() => {
    if (!getToken()) {
      setStatus('anonymous')
      return
    }
    let active = true
    authApi
      .me()
      .then((current) => {
        if (!active) return
        setUser(current)
        setStatus('authenticated')
      })
      .catch(() => {
        if (!active) return
        setToken(null)
        setStatus('anonymous')
      })
    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const result = await authApi.login(email, password)
    setToken(result.token)
    setUser(result.user)
    setStatus('authenticated')
    return result.user
  }, [])

  const register = useCallback(async (input: RegisterInput) => {
    await authApi.register(input)
    const result = await authApi.login(input.email, input.password)
    setToken(result.token)
    setUser(result.user)
    setStatus('authenticated')
    return result.user
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // La sesión pudo expirar en el servidor; limpiamos igual.
    }
    setToken(null)
    setUser(null)
    setStatus('anonymous')
    queryClient.clear()
  }, [])

  const value = useMemo(
    () => ({ user, status, login, register, logout }),
    [user, status, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth debe usarse dentro de <AuthProvider>')
  return context
}
