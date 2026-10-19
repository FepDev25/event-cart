import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createLaunch, getLaunch, reconfigureLaunch } from '@/api/launches'
import type { CreateLaunchInput } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ProblemAlert } from '@/components/domain/ProblemAlert'
import { Input, Textarea } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'

interface FormState {
  title: string
  description: string
  productName: string
  productRef: string
  unitPrice: string
  currency: string
  totalStock: string
  opensAt: string
  closesAt: string
  reservationTtlSeconds: string
  perBuyerLimit: string
}

function toLocalInput(iso: string): string {
  const date = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function defaultState(): FormState {
  const now = Date.now()
  return {
    title: '',
    description: '',
    productName: '',
    productRef: '',
    unitPrice: '19.90',
    currency: 'EUR',
    totalStock: '100',
    opensAt: toLocalInput(new Date(now + 5 * 60_000).toISOString()),
    closesAt: toLocalInput(new Date(now + 60 * 60_000).toISOString()),
    reservationTtlSeconds: '600',
    perBuyerLimit: '',
  }
}

export function LaunchFormPage() {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(defaultState)

  const launchQuery = useQuery({
    queryKey: ['launch', id],
    queryFn: () => getLaunch(id!),
    enabled: editing,
  })

  useEffect(() => {
    if (!launchQuery.data) return
    const launch = launchQuery.data
    setForm({
      title: launch.title,
      description: launch.description ?? '',
      productName: launch.productName,
      productRef: launch.productRef ?? '',
      unitPrice: String(launch.unitPrice),
      currency: launch.currency,
      totalStock: String(launch.totalStock),
      opensAt: toLocalInput(launch.opensAt),
      closesAt: toLocalInput(launch.closesAt),
      reservationTtlSeconds: String(launch.reservationTtlSeconds),
      perBuyerLimit: launch.perBuyerLimit ? String(launch.perBuyerLimit) : '',
    })
  }, [launchQuery.data])

  const mutation = useMutation({
    mutationFn: (input: CreateLaunchInput) =>
      editing ? reconfigureLaunch(id!, input) : createLaunch(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['launches', 'mine'] })
      navigate('/org')
    },
  })

  const set = <K extends keyof FormState>(key: K, value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    mutation.mutate({
      title: form.title,
      description: form.description || null,
      productName: form.productName,
      productRef: form.productRef || null,
      unitPrice: Number(form.unitPrice),
      currency: form.currency,
      totalStock: Number(form.totalStock),
      opensAt: new Date(form.opensAt).toISOString(),
      closesAt: new Date(form.closesAt).toISOString(),
      reservationTtlSeconds: form.reservationTtlSeconds ? Number(form.reservationTtlSeconds) : null,
      perBuyerLimit: form.perBuyerLimit ? Number(form.perBuyerLimit) : null,
    })
  }

  if (editing && launchQuery.isLoading) {
    return (
      <div className="grid min-h-[40vh] place-items-center">
        <Spinner label="Cargando lanzamiento" />
      </div>
    )
  }
  if (editing && launchQuery.isError) return <ProblemAlert error={launchQuery.error} />

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-heading text-2xl font-semibold">
          {editing ? 'Editar lanzamiento' : 'Nuevo lanzamiento'}
        </h1>
        <p className="text-sm text-muted-foreground">
          Se crea como borrador. Después podés programarlo o abrirlo desde el panel.
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Datos de la venta</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="grid gap-4 sm:grid-cols-2" noValidate>
            <div className="flex flex-col gap-2 sm:col-span-2">
              <Label htmlFor="title">Título</Label>
              <Input id="title" required value={form.title} onChange={(e) => set('title', e.target.value)} />
            </div>

            <div className="flex flex-col gap-2 sm:col-span-2">
              <Label htmlFor="description">Descripción (opcional)</Label>
              <Textarea
                id="description"
                value={form.description}
                onChange={(e) => set('description', e.target.value)}
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="productName">Producto</Label>
              <Input
                id="productName"
                required
                value={form.productName}
                onChange={(e) => set('productName', e.target.value)}
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="productRef">Referencia (opcional)</Label>
              <Input
                id="productRef"
                value={form.productRef}
                onChange={(e) => set('productRef', e.target.value)}
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="unitPrice">Precio unitario</Label>
              <Input
                id="unitPrice"
                type="number"
                min="0"
                step="0.01"
                required
                value={form.unitPrice}
                onChange={(e) => set('unitPrice', e.target.value)}
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="currency">Moneda (ISO 4217)</Label>
              <Input
                id="currency"
                required
                maxLength={3}
                value={form.currency}
                onChange={(e) => set('currency', e.target.value.toUpperCase())}
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="totalStock">Stock inicial</Label>
              <Input
                id="totalStock"
                type="number"
                min="0"
                required
                disabled={editing}
                value={form.totalStock}
                onChange={(e) => set('totalStock', e.target.value)}
              />
              {editing && <p className="text-xs text-muted-foreground">El stock no se edita después de crear.</p>}
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="perBuyerLimit">Límite por comprador (opcional)</Label>
              <Input
                id="perBuyerLimit"
                type="number"
                min="1"
                value={form.perBuyerLimit}
                onChange={(e) => set('perBuyerLimit', e.target.value)}
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="opensAt">Apertura</Label>
              <Input
                id="opensAt"
                type="datetime-local"
                required
                value={form.opensAt}
                onChange={(e) => set('opensAt', e.target.value)}
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="closesAt">Cierre</Label>
              <Input
                id="closesAt"
                type="datetime-local"
                required
                value={form.closesAt}
                onChange={(e) => set('closesAt', e.target.value)}
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="reservationTtlSeconds">TTL de reserva (segundos)</Label>
              <Input
                id="reservationTtlSeconds"
                type="number"
                min="1"
                value={form.reservationTtlSeconds}
                onChange={(e) => set('reservationTtlSeconds', e.target.value)}
              />
            </div>

            {mutation.isError && (
              <div className="sm:col-span-2">
                <ProblemAlert error={mutation.error} />
              </div>
            )}

            <div className="flex gap-3 sm:col-span-2">
              <Button type="submit" variant="primary" disabled={mutation.isPending}>
                {mutation.isPending ? 'Guardando…' : editing ? 'Guardar cambios' : 'Crear borrador'}
              </Button>
              <Button type="button" variant="ghost" onClick={() => navigate('/org')}>
                Cancelar
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
