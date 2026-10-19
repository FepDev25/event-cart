import { useQuery } from '@tanstack/react-query'
import { EVENTING_METRICS, getMetric } from '@/api/metrics'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/spinner'
import { ApiError } from '@/lib/api'

const METRIC_LABELS: Record<string, string> = {
  [EVENTING_METRICS.outboxPublished]: 'Eventos publicados (outbox)',
  [EVENTING_METRICS.outboxFailures]: 'Fallos de publicación (outbox)',
  [EVENTING_METRICS.duplicates]: 'Duplicados descartados (consumidores)',
  [EVENTING_METRICS.deadLetters]: 'Eventos enviados a DLQ',
  [EVENTING_METRICS.reservationsExpired]: 'Reservas expiradas liberadas',
  [EVENTING_METRICS.ordersFailed]: 'Pedidos fallados por reconciliación',
  [EVENTING_METRICS.staleReserved]: 'Reservas vencidas sin reconciliar (gauge)',
}

function MetricCard({ name }: { name: string }) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['metric', name],
    queryFn: () => getMetric(name),
    refetchInterval: 5_000,
    retry: false,
  })

  const measurement = data?.measurements?.[0]
  const notFound = error instanceof ApiError && error.status === 404

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-medium text-muted-foreground">
          {METRIC_LABELS[name] ?? name}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading && <Skeleton className="h-9 w-24" />}
        {isError && notFound && (
          <>
            <p className="font-heading text-3xl font-semibold tabular-nums">0</p>
            <p className="text-xs text-muted-foreground">Sin registros aún</p>
          </>
        )}
        {isError && !notFound && <p className="text-sm text-danger">No disponible</p>}
        {measurement && (
          <p className="font-heading text-3xl font-semibold tabular-nums">
            {Number.isInteger(measurement.value) ? measurement.value : measurement.value.toFixed(2)}
          </p>
        )}
        <p className="mt-1 font-mono text-[11px] text-muted-foreground">{name}</p>
      </CardContent>
    </Card>
  )
}

export function LabPage() {
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-heading text-2xl font-semibold">Laboratorio operativo</h1>
        <p className="text-sm text-muted-foreground">
          Indicadores de procesamiento asíncrono. Se refrescan cada 5s. Las pruebas reproducibles y la
          inyección de fallos viven en los scripts del repositorio, no en esta página.
        </p>
      </header>

      <Alert tone="info">
        <AlertTitle>Cómo ejecutar los experimentos</AlertTitle>
        <ul className="list-inside list-disc">
          <li>
            Kafka UI: <span className="font-mono">http://localhost:8095</span>
          </li>
          <li>
            Inspeccionar DLQ: <span className="font-mono">scripts/lab/inspect-dlt.sh</span> · reprocesar:{' '}
            <span className="font-mono">scripts/lab/requeue-dlt.sh</span>
          </li>
          <li>
            Detener/reiniciar consumidores o Kafka: <span className="font-mono">scripts/lab/*.sh</span>. Ver{' '}
            <span className="font-mono">docs/failure-lab.md</span>.
          </li>
        </ul>
      </Alert>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Object.values(EVENTING_METRICS).map((name) => (
          <MetricCard key={name} name={name} />
        ))}
      </div>
    </div>
  )
}
