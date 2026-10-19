import { useQuery } from '@tanstack/react-query'
import { listPublicLaunches } from '@/api/launches'
import { LaunchCard } from '@/components/domain/LaunchCard'
import { ProblemAlert } from '@/components/domain/ProblemAlert'
import { Skeleton } from '@/components/ui/spinner'
import { Card, CardContent } from '@/components/ui/card'

export function LaunchListPage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['launches'],
    queryFn: listPublicLaunches,
    refetchInterval: 5_000,
  })

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-heading text-2xl font-semibold">Lanzamientos</h1>
        <p className="text-sm text-muted-foreground">
          Ventas programadas de unidades limitadas. El estado se actualiza cada pocos segundos.
        </p>
      </header>

      {isError && <ProblemAlert error={error} />}

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-64" />
          ))}
        </div>
      )}

      {data && data.length === 0 && (
        <Card>
          <CardContent className="p-8 text-center text-sm text-muted-foreground">
            No hay lanzamientos publicados todavía.
          </CardContent>
        </Card>
      )}

      {data && data.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.map((launch) => (
            <LaunchCard key={launch.id} launch={launch} />
          ))}
        </div>
      )}
    </div>
  )
}
