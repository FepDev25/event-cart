import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { listMyLaunches, runLaunchAction, type LaunchAction } from '@/api/launches'
import type { Launch, LaunchStatus } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ProblemAlert } from '@/components/domain/ProblemAlert'
import { LAUNCH_STATUS, StatusBadge } from '@/components/domain/StatusBadge'
import { Skeleton } from '@/components/ui/spinner'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeaderCell,
  TableRow,
} from '@/components/ui/table'
import { formatDateTime, formatMoney } from '@/lib/format'

interface ActionDescriptor {
  action: LaunchAction
  label: string
  variant?: 'primary' | 'outline'
}

function actionsFor(status: LaunchStatus): ActionDescriptor[] {
  switch (status) {
    case 'DRAFT':
      return [
        { action: 'schedule', label: 'Programar' },
        { action: 'open', label: 'Abrir ya', variant: 'outline' },
      ]
    case 'SCHEDULED':
      return [{ action: 'open', label: 'Abrir' }]
    case 'OPEN':
      return [{ action: 'close', label: 'Cerrar', variant: 'outline' }]
    default:
      return []
  }
}

function LaunchRow({ launch }: { launch: Launch }) {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (action: LaunchAction) => runLaunchAction(launch.id, action),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['launches', 'mine'] }),
  })

  const canEdit = launch.status === 'DRAFT' || launch.status === 'SCHEDULED'
  const canCancel = !['CLOSED', 'CANCELLED'].includes(launch.status)

  return (
    <TableRow>
      <TableCell>
        <div className="flex flex-col">
          <span className="font-medium">{launch.title}</span>
          <span className="text-xs text-muted-foreground">{launch.productName}</span>
        </div>
      </TableCell>
      <TableCell>
        <StatusBadge descriptor={LAUNCH_STATUS[launch.status]} />
      </TableCell>
      <TableCell className="text-right tabular-nums">
        {launch.availableUnits}/{launch.totalStock}
      </TableCell>
      <TableCell className="text-right tabular-nums">{launch.confirmedUnits}</TableCell>
      <TableCell className="whitespace-nowrap text-xs text-muted-foreground">
        {formatDateTime(launch.opensAt)}
      </TableCell>
      <TableCell className="text-right">{formatMoney(launch.unitPrice, launch.currency)}</TableCell>
      <TableCell>
        <div className="flex flex-wrap justify-end gap-1.5">
          {canEdit && (
            <Button asChild size="sm" variant="ghost">
              <Link to={`/org/launches/${launch.id}`}>Editar</Link>
            </Button>
          )}
          {actionsFor(launch.status).map((item) => (
            <Button
              key={item.action}
              size="sm"
              variant={item.variant ?? 'primary'}
              onClick={() => mutation.mutate(item.action)}
              disabled={mutation.isPending}
            >
              {item.label}
            </Button>
          ))}
          {canCancel && (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => mutation.mutate('cancel')}
              disabled={mutation.isPending}
            >
              Cancelar
            </Button>
          )}
        </div>
      </TableCell>
    </TableRow>
  )
}

export function OrganizerPage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['launches', 'mine'],
    queryFn: listMyLaunches,
    refetchInterval: 10_000,
  })

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h1 className="font-heading text-2xl font-semibold">Organizador</h1>
          <p className="text-sm text-muted-foreground">
            Creá, programá y seguí tus lanzamientos. Los cambios tras abrir la venta están restringidos.
          </p>
        </div>
        <Button asChild variant="accent">
          <Link to="/org/launches/new">Nuevo lanzamiento</Link>
        </Button>
      </header>

      {isError && <ProblemAlert error={error} />}
      {isLoading && <Skeleton className="h-52" />}

      {data && data.length === 0 && (
        <Card>
          <CardContent className="p-8 text-center text-sm text-muted-foreground">
            No tenés lanzamientos. Creá el primero.
          </CardContent>
        </Card>
      )}

      {data && data.length > 0 && (
        <Card>
          <CardContent className="overflow-x-auto p-0">
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeaderCell>Lanzamiento</TableHeaderCell>
                  <TableHeaderCell>Estado</TableHeaderCell>
                  <TableHeaderCell className="text-right">Disp.</TableHeaderCell>
                  <TableHeaderCell className="text-right">Vendidas</TableHeaderCell>
                  <TableHeaderCell>Apertura</TableHeaderCell>
                  <TableHeaderCell className="text-right">Precio</TableHeaderCell>
                  <TableHeaderCell className="text-right">Acciones</TableHeaderCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.map((launch) => (
                  <LaunchRow key={launch.id} launch={launch} />
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
