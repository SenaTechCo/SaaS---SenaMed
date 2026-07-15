import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import type { UserRole } from '../types/auth';

export function ProtectedRoute({ children, allowedRoles }: { children: ReactNode; allowedRoles?: UserRole[] }) {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && user && !allowedRoles.includes(user.role)) {
    return <Navigate to={user.role === 'DOCTOR' ? '/dashboard/minha-agenda' : '/dashboard'} replace />;
  }

  return <>{children}</>;
}
