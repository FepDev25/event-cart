import { expect, test } from '@playwright/test'
import { createAndOpenLaunch, logout, openLaunchDetail, register, uniqueTitle } from './helpers'

test.describe('Pago rechazado', () => {
  test('libera la unidad y permite volver a reservar', async ({ page }) => {
    const title = uniqueTitle('Drop rechazado')

    await register(page, 'ORGANIZER')
    await createAndOpenLaunch(page, { title, stock: 1 })
    await logout(page)
    await register(page, 'BUYER')

    await openLaunchDetail(page, title)
    await page.getByRole('button', { name: 'Reservar 1 unidad' }).click()
    await expect(page.getByText('Tu reserva')).toBeVisible()

    await page.getByRole('button', { name: 'Completar compra' }).click()
    await page.getByRole('radio', { name: /DECLINE/ }).check()
    await page.getByRole('button', { name: 'Pagar' }).click()

    await expect(page.getByText(/No se pudo completar el pago/)).toBeVisible()

    // La reserva se libera: el lanzamiento vuelve a aceptar reservas.
    await page.getByRole('button', { name: 'Volver al lanzamiento' }).click()
    await expect(page.getByRole('button', { name: 'Reservar 1 unidad' })).toBeVisible()
  })
})
