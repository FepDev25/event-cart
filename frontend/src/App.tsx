import { Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { RequireAuth, RequireRole } from '@/auth/RequireRole'
import { LaunchListPage } from '@/pages/buyer/LaunchListPage'
import { LaunchDetailPage } from '@/pages/buyer/LaunchDetailPage'
import { ReservationsPage } from '@/pages/buyer/ReservationsPage'
import { LoginPage } from '@/pages/LoginPage'
import { OrganizerPage } from '@/pages/organizer/OrganizerPage'
import { LaunchFormPage } from '@/pages/organizer/LaunchFormPage'
import { LabPage } from '@/pages/lab/LabPage'
import { NotFoundPage } from '@/pages/NotFoundPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<AppLayout />}>
        <Route index element={<LaunchListPage />} />
        <Route path="launch/:id" element={<LaunchDetailPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route
          path="reservations"
          element={
            <RequireAuth>
              <ReservationsPage />
            </RequireAuth>
          }
        />
        <Route
          path="org"
          element={
            <RequireRole role="ORGANIZER">
              <OrganizerPage />
            </RequireRole>
          }
        />
        <Route
          path="org/launches/new"
          element={
            <RequireRole role="ORGANIZER">
              <LaunchFormPage />
            </RequireRole>
          }
        />
        <Route
          path="org/launches/:id"
          element={
            <RequireRole role="ORGANIZER">
              <LaunchFormPage />
            </RequireRole>
          }
        />
        <Route
          path="lab"
          element={
            <RequireRole role="ORGANIZER">
              <LabPage />
            </RequireRole>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
