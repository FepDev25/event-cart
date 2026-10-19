import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cancelReservation, listMyReservations } from '@/api/reservations'
import type { Reservation } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Countdown } from '@/components/domain/Countdown'
import { ProblemAlert } from '@/components/domain/ProblemAlert'
import { RESERVATION_STATUS, StatusBadge } from '@/components/domain/StatusBadge'
import { Skeleton } from '@/components/ui/spinner'
import { formatDateTime, formatMoney } from '@/lib/format'

function ReservationRow({ reservation }: { reservation: Reservation }) {
  const queryClient = useQueryClient()
  const cancel = useMutation({
    mutationFn: () => cancelReservation(reservation.id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['reservations'] }),
  })

  return (
    <Card>
      <CardContent className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-3">
            <StatusBadge descriptor={RESERVATION_STATUS[reservation.status]} />
            <span className="text-sm text-muted-foreground">
              {reservation.quantity} unidad{reservation.quantity > 1 ? 'es' : ''} ·{' '}
              {formatMoney(reservation.totalAmount, reservation.currency)}
            </span>
          </div>
          <Link to={`/launch/${reservation.launchId}`} className="text-sm font-semibold hover:text-accent-strong">
            Ver lanzamiento →
          </Link>
          <p className="text-xs text-muted-foreground">Creada: {formatDateTime(reservation.createdAt)}</p>
        </div>

        <div className="flex flex-col items-start gap-2 sm:items-end">
          {reservation.status === 'RESERVED' && (
            <>
              <Countdown expiresAt={reservation.expiresAt} />
              <div className="flex gap-2">
                <Button asChild size="sm" variant="primary">
                  <Link to={`/launch/${reservation.launchId}`}>Completar compra</Link>
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => cancel.mutate()}
                  disabled={cancel.isPending}
                >
                  {cancel.isPending ? 'Cancelando…' : 'Cancelar'}
                </Button>
              </div>
            </>
          )}
          {cancel.isError && <ProblemAlert error={cancel.error} />}
        </div>
      </CardContent>
    </Card>
  )
}

export function ReservationsPage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['reservations'],
    queryFn: listMyReservations,
    refetchInterval: 5_000,
  })

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-heading text-2xl font-semibold">Mis reservas</h1>
        <p className="text-sm text-muted-foreground">
          Reservas activas y su estado. Completar la compra se hace desde el lanzamiento.
        </p>
      </header>

      {isError && <ProblemAlert error={error} />}
      {isLoading && (
        <div className="flex flex-col gap-3">
          {[0, 1].map((i) => (
            <Skeleton key={i} className="h-28" />
          ))}
        </div>
      )}

      {data && data.length === 0 && (
        <Card>
          <CardContent className="p-8 text-center text-sm text-muted-foreground">
            Todavía no tenés reservas.{' '}
            <Link to="/" className="font-semibold underline">
              Ver lanzamientos
            </Link>
            .
          </CardContent>
        </Card>
      )}

      {data && data.length > 0 && (
        <div className="flex flex-col gap-3">
          {data.map((reservation) => (
            <ReservationRow key={reservation.id} reservation={reservation} />
          ))}
        </div>
      )}
    </div>
  )
}
