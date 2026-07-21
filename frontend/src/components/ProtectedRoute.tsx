import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import type { Permission, UserRole } from '../types/auth';

export function ProtectedRoute({
  children,
  allowedRoles,
  requiredPermission,
}: {
  children: ReactNode;
  allowedRoles?: UserRole[];
  requiredPermission?: Permission;
}) {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const isAuthorized =
    (!allowedRoles && !requiredPermission) ||
    user?.role === 'ADMIN' ||
    (!!allowedRoles && !!user && allowedRoles.includes(user.role)) ||
    (!!requiredPermission && !!user && user.permissions.includes(requiredPermission));

  if (!isAuthorized) {
    return <Navigate to={user?.role === 'DOCTOR' ? '/dashboard/minha-agenda' : '/dashboard'} replace />;
  }

  return <>{children}</>;
}
