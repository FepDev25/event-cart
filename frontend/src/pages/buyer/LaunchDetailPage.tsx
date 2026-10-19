import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getLaunch } from '@/api/launches'
import { getOrder } from '@/api/orders'
import { cancelReservation, reserve } from '@/api/reservations'
import { checkout } from '@/api/orders'
import type { Launch, Order, Reservation } from '@/api/types'
import { useAuth } from '@/auth/AuthProvider'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Countdown } from '@/components/domain/Countdown'
import { ProblemAlert } from '@/components/domain/ProblemAlert'
import { LAUNCH_STATUS, ORDER_STATUS, StatusBadge } from '@/components/domain/StatusBadge'
import { Spinner } from '@/components/ui/spinner'
import { formatDateTime, formatMoney, formatRelativeFromNow } from '@/lib/format'
import { describeError } from '@/lib/errors'

function AvailabilityPanel({ launch }: { launch: Launch }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Disponibilidad</CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-3 gap-3 text-center">
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted-foreground">Disponibles</dt>
            <dd className="font-heading text-2xl font-semibold text-available">{launch.availableUnits}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted-foreground">Reservadas</dt>
            <dd className="font-heading text-2xl font-semibold text-pending">{launch.reservedUnits}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted-foreground">Confirmadas</dt>
            <dd className="font-heading text-2xl font-semibold">{launch.confirmedUnits}</dd>
          </div>
        </dl>
        <p className="mt-3 text-xs text-muted-foreground">
          Datos del servidor. El stock no se infiere en el navegador.
        </p>
      </CardContent>
    </Card>
  )
}

function ResultPanel({ order, onReset }: { order: Order; onReset: () => void }) {
  const descriptor = ORDER_STATUS[order.status]
  const message =
    order.status === 'CONFIRMED'
      ? 'Pago aprobado. Tu pedido quedó confirmado.'
      : order.status === 'PENDING'
        ? 'El resultado quedó indeterminado. El sistema lo reconciliará; te avisaremos.'
        : `No se pudo completar el pago${order.failureReason ? ` (${order.failureReason})` : ''}. La unidad se libera.`
  const tone = order.status === 'CONFIRMED' ? 'success' : order.status === 'PENDING' ? 'warning' : 'danger'

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle className="text-base">Resultado del pago</CardTitle>
        <StatusBadge descriptor={descriptor} />
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <Alert tone={tone} role="status">
          <AlertTitle>{descriptor.label}</AlertTitle>
          <span>{message}</span>
        </Alert>
        <dl className="grid grid-cols-2 gap-1 text-sm">
          <dt className="text-muted-foreground">Pedido</dt>
          <dd className="text-right font-mono text-xs">{order.id.slice(0, 8)}</dd>
          <dt className="text-muted-foreground">Total</dt>
          <dd className="text-right">{formatMoney(order.totalAmount, order.currency)}</dd>
        </dl>
        {order.status === 'PENDING' && <Spinner label="Consultando estado del pedido" />}
        <Button variant="outline" onClick={onReset}>
          Volver al lanzamiento
        </Button>
      </CardContent>
    </Card>
  )
}

export function LaunchDetailPage() {
  const { id = '' } = useParams()
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const [reservation, setReservation] = useState<Reservation | null>(null)
  const [initialOrder, setInitialOrder] = useState<Order | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [checkoutOpen, setCheckoutOpen] = useState(false)
  const [paymentToken, setPaymentToken] = useState('APPROVE')
  const reserveIdem = useRef<string>(crypto.randomUUID())
  const checkoutIdem = useRef<string>(crypto.randomUUID())

  const launchQuery = useQuery({
    queryKey: ['launch', id],
    queryFn: () => getLaunch(id),
    refetchInterval: 3_000,
    enabled: id.length > 0,
  })

  const orderQuery = useQuery({
    queryKey: ['order', initialOrder?.id],
    queryFn: () => getOrder(initialOrder!.id),
    enabled: !!initialOrder && initialOrder.status === 'PENDING',
    refetchInterval: (query) => (query.state.data?.status === 'PENDING' ? 2_000 : false),
  })
  const order = orderQuery.data ?? initialOrder

  const reserveMutation = useMutation({
    mutationFn: () => reserve(id, quantity, reserveIdem.current),
    onSuccess: (created) => {
      setReservation(created)
      reserveIdem.current = crypto.randomUUID()
      void queryClient.invalidateQueries({ queryKey: ['launch', id] })
    },
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelReservation(reservation!.id),
    onSuccess: () => {
      setReservation(null)
      void queryClient.invalidateQueries({ queryKey: ['launch', id] })
    },
  })

  const checkoutMutation = useMutation({
    mutationFn: () => checkout(reservation!.id, paymentToken, checkoutIdem.current),
    onSuccess: (created) => {
      setCheckoutOpen(false)
      setInitialOrder(created)
      setReservation(null)
      checkoutIdem.current = crypto.randomUUID()
      void queryClient.invalidateQueries({ queryKey: ['launch', id] })
    },
  })

  const reset = () => {
    setInitialOrder(null)
    setReservation(null)
    setQuantity(1)
    void launchQuery.refetch()
  }

  if (launchQuery.isLoading) {
    return (
      <div className="grid min-h-[40vh] place-items-center">
        <Spinner label="Cargando lanzamiento" />
      </div>
    )
  }
  if (launchQuery.isError) return <ProblemAlert error={launchQuery.error} />
  const launch = launchQuery.data
  if (!launch) return null

  const isOpen = launch.status === 'OPEN'
  const canReserve = isOpen && launch.availableUnits >= quantity && !reservation && !order

  return (
    <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
      <div className="flex flex-col gap-6">
        <div className="flex flex-col gap-2">
          <Link to="/" className="text-sm text-muted-foreground hover:text-foreground">
            ← Lanzamientos
          </Link>
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">
                {launch.productRef ?? launch.productName}
              </p>
              <h1 className="font-heading text-3xl font-semibold">{launch.title}</h1>
            </div>
            <StatusBadge descriptor={LAUNCH_STATUS[launch.status]} />
          </div>
          <p className="font-heading text-2xl font-semibold">{formatMoney(launch.unitPrice, launch.currency)}</p>
          {launch.description && <p className="text-sm text-muted-foreground">{launch.description}</p>}
          <dl className="mt-1 grid grid-cols-2 gap-1 text-sm text-muted-foreground">
            <dt>Apertura</dt>
            <dd className="text-right text-foreground">{formatDateTime(launch.opensAt)}</dd>
            <dt>Cierre</dt>
            <dd className="text-right text-foreground">{formatDateTime(launch.closesAt)}</dd>
            {launch.perBuyerLimit && (
              <>
                <dt>Límite por comprador</dt>
                <dd className="text-right text-foreground">{launch.perBuyerLimit}</dd>
              </>
            )}
          </dl>
        </div>

        <AvailabilityPanel launch={launch} />
      </div>

      <div className="flex flex-col gap-4">
        {order ? (
          <ResultPanel order={order} onReset={reset} />
        ) : reservation ? (
          <Card>
            <CardHeader className="flex-row items-center justify-between">
              <CardTitle className="text-base">Tu reserva</CardTitle>
              <StatusBadge descriptor={{ tone: 'pending', label: 'Reservada' }} />
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <Countdown
                expiresAt={reservation.expiresAt}
                onExpire={() => void queryClient.invalidateQueries({ queryKey: ['launch', id] })}
              />
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">
                  {reservation.quantity} unidad{reservation.quantity > 1 ? 'es' : ''} · total
                </span>
                <span className="font-semibold">
                  {formatMoney(reservation.totalAmount, reservation.currency)}
                </span>
              </div>
              <Button variant="primary" onClick={() => setCheckoutOpen(true)}>
                Completar compra
              </Button>
              <Button variant="ghost" onClick={() => cancelMutation.mutate()} disabled={cancelMutation.isPending}>
                {cancelMutation.isPending ? 'Cancelando…' : 'Cancelar reserva'}
              </Button>
              <p className="text-xs text-muted-foreground">
                El contador es informativo. La disponibilidad la confirma el servidor.
              </p>
            </CardContent>
          </Card>
        ) : (
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Reservar</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              {!user && (
                <Alert tone="info">
                  <AlertTitle>Inicia sesión para reservar</AlertTitle>
                  <span>
                    Necesitas una cuenta de comprador.{' '}
                    <Link to="/login" className="font-semibold underline">
                      Entrar o registrarte
                    </Link>
                    .
                  </span>
                </Alert>
              )}

              {launch.status === 'SCHEDULED' && (
                <Alert tone="warning">
                  <AlertTitle>La venta aún no abre</AlertTitle>
                  <span>Abre {formatRelativeFromNow(launch.opensAt)} ({formatDateTime(launch.opensAt)}).</span>
                </Alert>
              )}
              {launch.status === 'CLOSED' && (
                <Alert tone="info">
                  <AlertTitle>Venta cerrada</AlertTitle>
                  <span>Este lanzamiento ya no acepta reservas.</span>
                </Alert>
              )}
              {launch.status === 'CANCELLED' && (
                <Alert tone="danger">
                  <AlertTitle>Lanzamiento cancelado</AlertTitle>
                  <span>El organizador canceló esta venta.</span>
                </Alert>
              )}
              {isOpen && launch.availableUnits === 0 && (
                <Alert tone="danger">
                  <AlertTitle>Agotado</AlertTitle>
                  <span>No quedan unidades disponibles.</span>
                </Alert>
              )}

              <div className="flex items-center gap-3">
                <label htmlFor="qty" className="text-sm text-muted-foreground">
                  Cantidad
                </label>
                <input
                  id="qty"
                  type="number"
                  min={1}
                  max={launch.availableUnits || 1}
                  value={quantity}
                  onChange={(event) => setQuantity(Math.max(1, Number(event.target.value) || 1))}
                  className="h-11 w-20 rounded-md border border-input bg-surface px-3 text-sm"
                  disabled={!user || !isOpen}
                />
              </div>

              <Button
                variant="primary"
                disabled={!user || !canReserve || reserveMutation.isPending}
                onClick={() => reserveMutation.mutate()}
              >
                {reserveMutation.isPending ? 'Reservando…' : `Reservar ${quantity} unidad${quantity > 1 ? 'es' : ''}`}
              </Button>

              {reserveMutation.isError && <ProblemAlert error={reserveMutation.error} />}
              <p className="text-xs text-muted-foreground">
                {reveal(describeError(reserveMutation.error))}
              </p>
            </CardContent>
          </Card>
        )}
      </div>

      <Dialog open={checkoutOpen} onOpenChange={setCheckoutOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Pago simulado</DialogTitle>
            <DialogDescription>
              Elegí un token de prueba. No se mueve dinero real.
            </DialogDescription>
          </DialogHeader>
          <fieldset className="flex flex-col gap-2">
            <legend className="sr-only">Token de pago simulado</legend>
            {[
              { value: 'APPROVE', label: 'APPROVE — pago aprobado' },
              { value: 'DECLINE', label: 'DECLINE — pago rechazado' },
              { value: 'TIMEOUT', label: 'TIMEOUT — resultado indeterminado' },
            ].map((option) => (
              <label key={option.value} className="flex items-center gap-3 rounded-md border border-border p-3 text-sm">
                <input
                  type="radio"
                  name="paymentToken"
                  value={option.value}
                  checked={paymentToken === option.value}
                  onChange={() => setPaymentToken(option.value)}
                  className="size-4 accent-[var(--ec-color-primary)]"
                />
                <span className="font-mono text-xs">{option.label}</span>
              </label>
            ))}
          </fieldset>
          {checkoutMutation.isError && <ProblemAlert error={checkoutMutation.error} />}
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="ghost">Cancelar</Button>
            </DialogClose>
            <Button
              variant="accent"
              onClick={() => checkoutMutation.mutate()}
              disabled={checkoutMutation.isPending}
            >
              {checkoutMutation.isPending ? 'Procesando…' : 'Pagar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function reveal(error: unknown): string {
  if (!error) return 'Al reservar apartamos tu unidad por tiempo limitado. El stock se libera si no completas la compra.'
  return describeError(error).message
}
