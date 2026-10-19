import { expect, test } from '@playwright/test'
import { createAndOpenLaunch, logout, openLaunchDetail, register, uniqueTitle } from './helpers'

test.describe('Recorrido comercial completo', () => {
  test('organizador publica y comprador reserva y paga', async ({ page }) => {
    const title = uniqueTitle('Edición limitada')

    // 1. El organizador crea una cuenta y publica un lanzamiento abierto.
    await register(page, 'ORGANIZER')
    await createAndOpenLaunch(page, { title, stock: 3 })
    await logout(page)

    // 2. El comprador crea su cuenta.
    await register(page, 'BUYER')

    // 3. Abre el detalle del lanzamiento desde la home.
    await openLaunchDetail(page, title)
    await expect(page.getByRole('heading', { name: 'Disponibilidad' })).toBeVisible()

    // 4. Reserva una unidad: el contador de caducidad es informativo.
    await page.getByRole('button', { name: 'Reservar 1 unidad' }).click()
    await expect(page.getByText('Tu reserva')).toBeVisible()
    await expect(page.getByRole('status')).toContainText('para completar la compra')

    // 5. Completa el checkout simulado con un pago aprobado.
    await page.getByRole('button', { name: 'Completar compra' }).click()
    await expect(page.getByRole('heading', { name: 'Pago simulado' })).toBeVisible()
    await page.getByRole('radio', { name: /APPROVE/ }).check()
    await page.getByRole('button', { name: 'Pagar' }).click()

    // 6. El pedido queda confirmado.
    await expect(page.getByText('Pago aprobado. Tu pedido quedó confirmado.')).toBeVisible()
    await expect(page.getByRole('status')).toContainText('Confirmado')
  })
})
