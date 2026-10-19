import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-3 py-16 text-center">
      <p className="font-heading text-4xl font-bold">404</p>
      <h1 className="font-heading text-xl font-semibold">Página no encontrada</h1>
      <p className="text-sm text-muted-foreground">
        La dirección no existe o el lanzamiento ya no está publicado.
      </p>
      <Link to="/" className="text-sm font-semibold text-accent-strong underline">
        Volver a los lanzamientos
      </Link>
    </div>
  )
}
