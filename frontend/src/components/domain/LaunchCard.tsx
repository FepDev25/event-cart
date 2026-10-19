import { Link } from 'react-router-dom'
import type { Launch } from '@/api/types'
import { formatDateTime, formatMoney } from '@/lib/format'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { LAUNCH_STATUS, StatusBadge } from './StatusBadge'

export function LaunchCard({ launch }: { launch: Launch }) {
  return (
    <Card className="flex h-full flex-col">
      <CardHeader>
        <div className="flex items-start justify-between gap-3">
          <CardTitle className="text-lg">
            <Link to={`/launch/${launch.id}`} className="hover:text-accent-strong">
              {launch.title}
            </Link>
          </CardTitle>
          <StatusBadge descriptor={LAUNCH_STATUS[launch.status]} />
        </div>
        <p className="text-sm text-muted-foreground">{launch.productName}</p>
      </CardHeader>
      <CardContent className="mt-auto flex flex-col gap-2">
        <span className="font-heading text-xl font-semibold">{formatMoney(launch.unitPrice, launch.currency)}</span>
        <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm text-muted-foreground">
          <dt>Disponibles</dt>
          <dd className="text-right text-foreground">{launch.availableUnits}</dd>
          <dt>Reservadas</dt>
          <dd className="text-right text-foreground">{launch.reservedUnits}</dd>
          <dt>Confirmadas</dt>
          <dd className="text-right text-foreground">{launch.confirmedUnits}</dd>
        </dl>
        <p className="text-xs text-muted-foreground">Abre: {formatDateTime(launch.opensAt)}</p>
        <Link
          to={`/launch/${launch.id}`}
          className="mt-1 inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground hover:bg-primary-hover"
        >
          Ver lanzamiento
        </Link>
      </CardContent>
    </Card>
  )
}
