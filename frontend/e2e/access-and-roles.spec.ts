import { expect, test } from '@playwright/test'
import { register } from './helpers'

test.describe('Acceso y guardas de rol', () => {
  test('un invitado es redirigido al login en rutas protegidas', async ({ page }) => {
    await page.goto('/org')
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()

    await page.goto('/lab')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('un comprador no accede al panel de organizador', async ({ page }) => {
    await register(page, 'BUYER')
    await page.goto('/org')
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('heading', { name: 'Lanzamientos' })).toBeVisible()
  })

  test('el organizador ve el laboratorio operativo con métricas', async ({ page }) => {
    await register(page, 'ORGANIZER')
    await page.getByRole('link', { name: 'Laboratorio' }).click()
    await expect(page.getByRole('heading', { name: 'Laboratorio operativo' })).toBeVisible()
    await expect(page.getByText('Eventos publicados (outbox)')).toBeVisible()
    await expect(page.getByText('Eventos enviados a DLQ')).toBeVisible()
  })
})
