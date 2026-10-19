import { expect, type Page } from '@playwright/test'

export const PASSWORD = 'sup3r-secret-pass'

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}@eventcart.test`
}

export function uniqueTitle(prefix = 'Drop'): string {
  return `${prefix} ${Date.now()}-${Math.floor(Math.random() * 1_000)}`
}

/** Formato `datetime-local` en hora local: `YYYY-MM-DDTHH:mm`. */
export function localInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`
}

export function minutesFromNow(minutes: number): Date {
  return new Date(Date.now() + minutes * 60_000)
}

export async function register(page: Page, role: 'BUYER' | 'ORGANIZER'): Promise<string> {
  const email = uniqueEmail(role.toLowerCase())
  await page.goto('/login')
  await page.getByRole('button', { name: 'Registrate' }).click()
  await page.getByLabel('Correo').fill(email)
  await page.getByLabel('Contraseña').fill(PASSWORD)
  await page
    .getByRole('radio', { name: role === 'ORGANIZER' ? 'Organizador' : 'Comprador' })
    .check()
  await page.getByRole('button', { name: 'Crear cuenta' }).click()
  await expect(page.getByRole('button', { name: 'Salir' })).toBeVisible()
  return email
}

export async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Salir' }).click()
  await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible()
}

export interface LaunchOptions {
  title: string
  stock?: number
  opensAt?: Date
  closesAt?: Date
}

/**
 * Crea un lanzamiento desde el panel del organizador y lo abre.
 * Requiere una sesión de ORGANIZER activa.
 */
export async function createAndOpenLaunch(page: Page, options: LaunchOptions): Promise<void> {
  const { title, stock = 3, opensAt = minutesFromNow(-1), closesAt = minutesFromNow(60) } = options

  await page.getByRole('link', { name: 'Organizador' }).click()
  await page.getByRole('link', { name: 'Nuevo lanzamiento' }).click()

  await page.getByLabel('Título').fill(title)
  await page.getByLabel('Producto').fill('Tote bag')
  await page.getByLabel('Stock inicial').fill(String(stock))
  await page.getByLabel('Apertura').fill(localInput(opensAt))
  await page.getByLabel('Cierre').fill(localInput(closesAt))
  await page.getByRole('button', { name: 'Crear borrador' }).click()

  await expect(page.getByRole('cell', { name: title })).toBeVisible()
  await page.getByRole('button', { name: 'Abrir ya' }).click()
  await expect(page.getByText('Abierto')).toBeVisible()
}

/** Desde la home, abre el detalle del lanzamiento por su título. */
export async function openLaunchDetail(page: Page, title: string): Promise<void> {
  await page.goto('/')
  await page.getByRole('link', { name: title }).click()
  await expect(page).toHaveURL(/\/launch\/[0-9a-f-]{36}$/)
  await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible()
}
