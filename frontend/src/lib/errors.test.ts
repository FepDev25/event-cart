import { describe, expect, it } from 'vitest'
import { ApiError } from '@/lib/api'
import { describeError, isUnauthorized } from './errors'

describe('describeError', () => {
  it('traduce un tipo de problema conocido a un mensaje en español', () => {
    const error = new ApiError(409, {
      type: 'urn:eventcart:error:stock-insufficient',
      status: 409,
      detail: 'not enough units',
    })
    expect(describeError(error).message).toBe('No hay suficientes unidades disponibles.')
    expect(describeError(error).status).toBe(409)
  })

  it('usa el detail del backend cuando el tipo es desconocido', () => {
    const error = new ApiError(400, { detail: 'boom' })
    expect(describeError(error).message).toBe('boom')
  })

  it('cae a un mensaje genérico para errores no-HTTP', () => {
    expect(describeError(new Error('red')).message).toBe('red')
    expect(describeError(null).message).toMatch(/inesperado/)
  })
})

describe('isUnauthorized', () => {
  it('detecta respuestas 401', () => {
    expect(isUnauthorized(new ApiError(401, { type: 'urn:eventcart:error:unauthenticated' }))).toBe(true)
    expect(isUnauthorized(new ApiError(500, {}))).toBe(false)
  })
})
