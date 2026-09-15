import { Navigate, useLocation } from 'react-router-dom'
import { isAdminAuthenticated } from '../api/buylistApi'

interface ProtectedRouteProps {
  children: React.ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const isAuth = isAdminAuthenticated()
  const location = useLocation()

  if (!isAuth) {
    // Automatically redirect unauthenticated users to /admin/login preserving intended path
    return <Navigate to="/admin/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}
